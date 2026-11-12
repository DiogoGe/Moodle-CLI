package moodle.infrastructure

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitCourseRepository
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetrofitCourseRepositoryTest {
    private val token = AccessToken("a".repeat(32))

    @Test
    fun `course list uses authenticated user and sends credentials only in POST body`() {
        withServer("""[{"id":12,"fullname":"Programming","shortname":"PRG"}]""") { repository, requests ->
            assertEquals("Programming", repository.getCourses(token, 42).single().name)
            val request = requests.single()
            assertTrue(request.contains("POST /webservice/rest/server.php "))
            assertTrue(request.contains("userid=42"))
            assertTrue(request.contains("wstoken=${token.value}"))
            assertTrue(request.contains("wsfunction=core_enrol_get_users_courses"))
        }
    }

    @Test
    fun `sections map descriptions inline text and files while hiding unavailable modules`() {
        withServer(CONTENTS) { repository, requests ->
            val section = repository.getContents(token, 12).single()
            assertEquals("<p>Overview</p>", section.description)
            val module = section.modules.single()
            assertEquals(listOf("<p>Read this</p>"), module.texts)
            assertEquals("lecture.pdf", module.files.single().name)
            assertEquals(100L, module.files.single().size)
            assertTrue(requests.single().contains("courseid=12"))
            assertTrue(requests.single().contains("wsfunction=core_course_get_contents"))
        }
    }

    @Test
    fun `empty courses remain distinct from API failures and upstream secrets are not displayed`() {
        withServer("[]") { repository, _ -> assertTrue(repository.getCourses(token, 42).isEmpty()) }
        withServer("""{"exception":"error","errorcode":"invalidtoken","message":"secret-value"}""") { repository, _ ->
            val error = assertFailsWith<MoodleException.Authentication> { repository.getCourses(token, 42) }
            assertFalse(error.message.orEmpty().contains("secret-value"))
        }
        withServer("""{"exception":"error","errorcode":"accessexception"}""") { repository, _ ->
            assertFailsWith<MoodleException.Permission> { repository.getContents(token, 12) }
        }
    }

    @Test
    fun `malformed course responses and redirects are failures`() {
        withServer("<html>Login</html>") { repository, _ ->
            assertFailsWith<MoodleException.Connection> { repository.getCourses(token, 42) }
        }
        withServer("", 302) { repository, requests ->
            assertFailsWith<MoodleException.Connection> { repository.getCourses(token, 42) }
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `HTTP authentication permission and server errors remain distinct`() {
        withServer("private response", 401) { repository, _ ->
            val error = assertFailsWith<MoodleException.Authentication> { repository.getCourses(token, 42) }
            assertEquals("error.session.expired", error.messageKey)
        }
        withServer("private response", 403) { repository, _ ->
            val error = assertFailsWith<MoodleException.Permission> { repository.getContents(token, 12) }
            assertEquals("error.api.courseAccess", error.messageKey)
        }
        withServer("private response", 500) { repository, _ ->
            val error = assertFailsWith<MoodleException.Connection> { repository.getCourses(token, 42) }
            assertEquals("error.api.http", error.messageKey)
            assertFalse(error.toString().contains("private response"))
        }
    }

    private fun withServer(body: String, status: Int = 200, test: (RetrofitCourseRepository, List<String>) -> Unit) {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestBody.reader().readText()}")
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (status == 302) exchange.responseHeaders.add("Location", "/redirect")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val json = Json { ignoreUnknownKeys = true }
            val api = createMoodleApi("http://127.0.0.1:${server.address.port}/", json)
            test(RetrofitCourseRepository(api, json), requests)
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        val CONTENTS = """
            [{"id":1,"name":"Week 1","summary":"<p>Overview</p>","modules":[
              {"id":10,"name":"Lecture","modname":"resource","description":"Intro","contents":[
                {"type":"content","content":"<p>Read this</p>"},
                {"type":"file","filename":"lecture.pdf","filesize":100,"fileurl":"https://moodle.example/webservice/pluginfile.php/1/a"}
              ]},
              {"id":11,"name":"Hidden","modname":"resource","uservisible":false}
            ]}]
        """.trimIndent()
    }
}
