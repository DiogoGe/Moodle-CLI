package moodle.infrastructure

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import moodle.application.AssignmentService
import moodle.cli.AssignmentCommands
import moodle.cli.i18n.Messages
import moodle.domain.AccessToken
import moodle.domain.entities.Assignment
import moodle.domain.enums.SubmissionStatus
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.assignmentAccess
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitAssignmentRepository
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetrofitAssignmentRepositoryTest {
    private val token = AccessToken("a".repeat(32))
    private val assignment = Assignment(10, 42, "Report", null)

    @Test
    fun `assignments send course filters and credentials only in POST bodies and map missing deadlines`() {
        withRepository(mapOf(LIST to ASSIGNMENTS)) { repository, requests ->
            val items = repository.getAssignments(token, 42).items
            assertEquals(listOf(10L, 11L), items.map { it.id })
            assertEquals("Programming", items.first().courseName)
            assertEquals(Instant.ofEpochSecond(1793872800), items.first().dueDate)
            assertEquals(null, items.last().dueDate)
            assertTrue(items.last().groupSubmission)
            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("/webservice/rest/server.php", request.uri)
            assertEquals(token.value, request.fields["wstoken"])
            assertEquals("42", request.fields["courseids[0]"])
            assertFalse(request.uri.contains(token.value))
            assertFalse(request.fields.containsKey("userid"))
        }
    }

    @Test
    fun `known submission statuses map explicitly and unsupported values stay unknown`() {
        val cases = mapOf(
            "new" to SubmissionStatus.NOT_SUBMITTED,
            "draft" to SubmissionStatus.DRAFT,
            "submitted" to SubmissionStatus.SUBMITTED,
            "reopened" to SubmissionStatus.REOPENED,
            "future-status" to SubmissionStatus.UNKNOWN
        )
        for ((value, expected) in cases) {
            withRepository(mapOf(STATUS to """{"lastattempt":{"submission":{"status":"$value"}}}""")) { repository, requests ->
                assertEquals(expected, repository.getSubmissionDetails(token, assignment).status)
                assertEquals("10", requests.single().fields["assignid"])
                assertFalse(requests.single().fields.containsKey("userid"))
            }
        }
    }

    @Test
    fun `group submission takes precedence over an individual attempt and extensions are exposed`() {
        withRepository(mapOf(STATUS to GROUP_STATUS)) { repository, _ ->
            val details = repository.getSubmissionDetails(token, assignment.copy(groupSubmission = true))
            assertEquals(SubmissionStatus.DRAFT, details.status)
            assertEquals(Instant.ofEpochSecond(1794045600), details.extensionDueDate)
        }
        withRepository(mapOf(STATUS to """{"lastattempt":{}}""")) { repository, _ ->
            assertEquals(SubmissionStatus.UNKNOWN, repository.getSubmissionDetails(token, assignment).status)
        }
    }

    @Test
    fun `unavailable status capability skips requests and unavailable lists fail before any request`() {
        withRepository(emptyMap(), available = setOf(LIST)) { repository, requests ->
            assertEquals(SubmissionStatus.UNKNOWN, repository.getSubmissionDetails(token, assignment).status)
            assertTrue(requests.isEmpty())
        }
        withRepository(emptyMap(), available = emptySet()) { repository, requests ->
            assertFailsWith<MoodleException.Permission> { repository.getAssignments(token, null) }
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun `optional permission failures yield unknown but authentication errors are propagated`() {
        withRepository(mapOf(STATUS to """{"exception":"error","errorcode":"accessexception","message":"private-data"}""")) {
                repository,
                _
            ->
            assertEquals(SubmissionStatus.UNKNOWN, repository.getSubmissionDetails(token, assignment).status)
        }
        withRepository(mapOf(STATUS to """{"exception":"error","errorcode":"invalidtoken","message":"private-data"}""")) {
                repository,
                _
            ->
            val error = assertFailsWith<MoodleException.Authentication> { repository.getSubmissionDetails(token, assignment) }
            assertFalse(Messages(Locale.GERMAN).error(error).contains("private-data"))
        }
        withRepository(mapOf(LIST to """{"exception":"error","errorcode":"nopermissions"}""")) { repository, _ ->
            assertFailsWith<MoodleException.Permission> { repository.getAssignments(token, null) }
        }
    }

    @Test
    fun `warnings mark incomplete lists and do not become an invented submission status`() {
        val warning = """[{"warningcode":"1","message":"private-warning","item":"module","itemid":1}]"""
        withRepository(mapOf(LIST to """{"courses":[],"warnings":$warning}""")) { repository, _ ->
            val result = repository.getAssignments(token, null)
            assertTrue(result.partial)
            assertTrue(result.items.isEmpty())
            assertFalse(result.toString().contains("private-warning"))
        }
        withRepository(mapOf(STATUS to """{"warnings":$warning,"lastattempt":{"submission":{"status":"submitted"}}}""")) {
                repository,
                _
            ->
            assertEquals(SubmissionStatus.UNKNOWN, repository.getSubmissionDetails(token, assignment).status)
        }
    }

    @Test
    fun `malformed responses and redirects fail safely instead of becoming empty task lists`() {
        for (body in listOf("<html>private-response</html>", "{}", "[]")) {
            withRepository(mapOf(LIST to body)) { repository, _ ->
                val error = assertFailsWith<MoodleException.Connection> { repository.getAssignments(token, null) }
                assertFalse(Messages(Locale.GERMAN).error(error).contains("private-response"))
            }
        }
        withRepository(mapOf(LIST to ""), status = 302) { repository, requests ->
            assertFailsWith<MoodleException.Connection> { repository.getAssignments(token, null) }
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `task commands read actual HTTP fixtures through services and render localized output`() {
        withRepository(mapOf(LIST to ASSIGNMENTS, STATUS to GROUP_STATUS)) { repository, requests ->
            val clock = Clock.fixed(Instant.ofEpochSecond(1793872800), ZoneOffset.UTC)
            val service = AssignmentService(assignmentAccess(), repository, clock)
            val output = mutableListOf<String>()
            val commands = AssignmentCommands(service, clock, output::add, Messages(Locale.GERMAN))
            commands.run(listOf("tasks", "--course", "42"))
            assertTrue(output.any { "Programming (42)" in it })
            assertTrue(output.any { "eingereicht" in it })
            assertTrue(output.any { "Entwurf" in it })
            assertEquals(3, requests.size)
            assertFalse(output.any { token.value in it })
            commands.run(listOf("upcoming", "--days", "7"))
            assertTrue(output.any { "Frist:" in it })
        }
    }

    private fun withRepository(
        responses: Map<String, String>,
        available: Set<String> = setOf(LIST, STATUS),
        status: Int = 200,
        test: (RetrofitAssignmentRepository, List<Request>) -> Unit
    ) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val fields = exchange.requestBody.reader().readText().split('&').associate { part ->
                val pair = part.split('=', limit = 2)
                URLDecoder.decode(pair[0], Charsets.UTF_8) to URLDecoder.decode(pair.getOrElse(1) { "" }, Charsets.UTF_8)
            }
            requests.add(Request(exchange.requestMethod, exchange.requestURI.toString(), fields))
            val bytes = responses.getValue(fields.getValue("wsfunction")).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (status == 302) exchange.responseHeaders.add("Location", "/redirect")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val json = Json { ignoreUnknownKeys = true }
            val api = createMoodleApi("http://127.0.0.1:${server.address.port}/", json)
            test(RetrofitAssignmentRepository(api, json) { available }, requests)
        } finally {
            server.stop(0)
        }
    }

    private data class Request(val method: String, val uri: String, val fields: Map<String, String>)

    private companion object {
        const val LIST = "mod_assign_get_assignments"
        const val STATUS = "mod_assign_get_submission_status"
        val ASSIGNMENTS = """
            {"courses":[{"id":42,"fullname":"Programming","assignments":[
              {"id":10,"cmid":999,"name":"Report","duedate":1793872800},
              {"id":11,"cmid":1000,"name":"Group project","duedate":0,"teamsubmission":1}
            ]}],"warnings":[]}
        """.trimIndent()
        val GROUP_STATUS = """
            {"lastattempt":{"submission":{"status":"submitted"},"teamsubmission":{"status":"draft"},
              "extensionduedate":1794045600},"warnings":[]}
        """.trimIndent()
    }
}
