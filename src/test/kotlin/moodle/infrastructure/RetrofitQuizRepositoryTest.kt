package moodle.infrastructure

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import moodle.application.QuizService
import moodle.cli.MoodleCli
import moodle.cli.QuizCommands
import moodle.cli.i18n.Messages
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.authenticatedAccess
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitQuizRepository
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetrofitQuizRepositoryTest {
    private val token = AccessToken("a".repeat(32))

    @Test
    fun `quiz listing uses the quiz API and sends credentials and course filters only in POST bodies`() {
        withRepository(QUIZZES) { repository, requests ->
            val quiz = repository.getQuizzes(token, 42).items.single()
            assertEquals(10L, quiz.id)
            assertEquals(42L, quiz.courseId)
            assertEquals(Instant.ofEpochSecond(1793872800), quiz.timing?.opensAt)
            assertEquals(Duration.ofSeconds(95), quiz.timing?.timeLimit)
            assertFalse(quiz.toString().contains("private-password"))
            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("/webservice/rest/server.php", request.uri)
            assertEquals(FUNCTION, request.fields["wsfunction"])
            assertEquals("42", request.fields["courseids[0]"])
            assertEquals(token.value, request.fields["wstoken"])
            assertFalse(request.uri.contains(token.value))
        }
    }

    @Test
    fun `missing or incomplete timing remains unknown while zero means unrestricted`() {
        val body = """
            {"quizzes":[
              {"id":1,"course":42,"name":"Unknown"},
              {"id":2,"course":42,"name":"Unrestricted","timeopen":0,"timeclose":0,"timelimit":0},
              {"id":3,"course":42,"name":"Incomplete","timeclose":1793872800}
            ],"warnings":[]}
        """.trimIndent()
        withRepository(body) { repository, _ ->
            val items = repository.getQuizzes(token, null).items
            assertEquals(null, items[0].timing)
            assertTrue(items[1].timing != null)
            assertEquals(null, items[1].timing?.closesAt)
            assertEquals(null, items[1].timing?.timeLimit)
            assertEquals(null, items[2].timing)
        }
    }

    @Test
    fun `unavailable quiz functions fail before a request and server permission errors mention quizzes`() {
        withRepository("", available = emptySet()) { repository, requests ->
            val error = assertFailsWith<MoodleException.Permission> { repository.getQuizzes(token, null) }
            assertEquals("error.api.quizzesUnavailable", error.messageKey)
            assertTrue(requests.isEmpty())
        }
        withRepository("""{"exception":"error","errorcode":"nopermission","message":"private-detail"}""") { repository, _ ->
            val error = assertFailsWith<MoodleException.Permission> { repository.getQuizzes(token, null) }
            assertEquals("error.api.quizAccess", error.messageKey)
            assertFalse(Messages(Locale.ENGLISH).error(error).contains("private-detail"))
        }
        withRepository("", status = 403) { repository, _ ->
            val error = assertFailsWith<MoodleException.Permission> { repository.getQuizzes(token, null) }
            assertEquals("error.api.quizAccess", error.messageKey)
        }
    }

    @Test
    fun `warnings mark partial quiz access without retaining remote warning text`() {
        val body = """{"quizzes":[],"warnings":[{"warningcode":"1","message":"private-warning"}]}"""
        withRepository(body) { repository, _ ->
            val result = repository.getQuizzes(token, null)
            assertTrue(result.partial)
            assertTrue(result.items.isEmpty())
            assertFalse(result.toString().contains("private-warning"))
        }
    }

    @Test
    fun `malformed quiz responses and invalid timing are failures rather than empty lists`() {
        val invalid = listOf(
            "<html>private-response</html>",
            "{}",
            "[]",
            """{"quizzes":[{"id":1,"course":42,"name":"Bad","timeopen":-1,"timeclose":0,"timelimit":0}]}""",
            """{"quizzes":[{"id":1,"course":42,"name":"Bad","timeopen":9223372036854775807,"timeclose":0,"timelimit":0}]}"""
        )
        invalid.forEach { body ->
            withRepository(body) { repository, _ ->
                val error = assertFailsWith<MoodleException.Connection> { repository.getQuizzes(token, null) }
                assertFalse(Messages(Locale.GERMAN).error(error).contains("private-response"))
            }
        }
        withRepository("", status = 302) { repository, requests ->
            assertFailsWith<MoodleException.Connection> { repository.getQuizzes(token, null) }
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `quiz authentication failures propagate and clear the active session`() {
        withRepository("""{"exception":"error","errorcode":"invalidtoken"}""") { repository, requests ->
            val access = authenticatedAccess()
            val service = QuizService(access, repository)
            assertFailsWith<MoodleException.Authentication> { service.getQuizzes() }
            assertFailsWith<MoodleException.Authentication> { service.getQuizzes() }
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `launcher dispatch can list quiz metadata through real HTTP without any attempt requests`() {
        withRepository(QUIZZES) { repository, requests ->
            val output = mutableListOf<String>()
            val clock = Clock.fixed(Instant.ofEpochSecond(1793872800), ZoneOffset.UTC)
            val commands =
                QuizCommands(QuizService(authenticatedAccess(), repository), clock, output::add, Messages(Locale.GERMAN))
            val cli = MoodleCli(studyCommand = commands::run, messages = Messages(Locale.GERMAN))
            assertEquals(0, cli.run(arrayOf("quizzes", "--course", "42")))
            assertTrue(output.any { "[10] Lernkontrolle" in it })
            assertTrue(output.any { "1 Min 35 s" in it })
            assertFalse(output.any { token.value in it })
            assertEquals(listOf(FUNCTION), requests.map { it.fields["wsfunction"] })
        }
    }

    private fun withRepository(
        body: String,
        available: Set<String> = setOf(FUNCTION),
        status: Int = 200,
        test: (RetrofitQuizRepository, List<Request>) -> Unit
    ) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val fields = exchange.requestBody.reader().readText().split('&').associate { part ->
                val pair = part.split('=', limit = 2)
                URLDecoder.decode(pair[0], Charsets.UTF_8) to URLDecoder.decode(pair.getOrElse(1) { "" }, Charsets.UTF_8)
            }
            requests.add(Request(exchange.requestMethod, exchange.requestURI.toString(), fields))
            val bytes = body.toByteArray()
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
            test(RetrofitQuizRepository(api, json) { available }, requests)
        } finally {
            server.stop(0)
        }
    }

    private data class Request(val method: String, val uri: String, val fields: Map<String, String>)

    private companion object {
        const val FUNCTION = "mod_quiz_get_quizzes_by_courses"
        val QUIZZES = """
            {"quizzes":[{"id":10,"coursemodule":999,"course":42,"name":"Lernkontrolle",
              "timeopen":1793872800,"timeclose":1793959200,"timelimit":95,"password":"private-password"}],"warnings":[]}
        """.trimIndent()
    }
}
