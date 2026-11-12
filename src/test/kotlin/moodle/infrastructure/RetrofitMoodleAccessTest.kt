package moodle.infrastructure

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.api.RetrofitMoodleAccess
import moodle.infrastructure.api.createMoodleApi
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetrofitMoodleAccessTest {
    @Test
    fun `public configuration request contains the method and args without a token`() {
        withServer(PUBLIC_CONFIG) { access, requests ->
            val status = access.getPublicStatus()
            assertTrue(status.webServicesEnabled && status.mobileServiceEnabled && status.browserLogin)
            val request = requests.single()
            assertEquals("POST", request.method)
            assertTrue(request.body.contains("tool_mobile_get_public_config"))
            assertTrue(request.body.contains("\"args\":{}"))
            assertFalse(request.body.contains("wstoken"))
        }
    }

    @Test
    fun `authenticated check sends token in POST body rather than URL`() {
        withServer(SITE_INFO) { access, requests ->
            val token = AccessToken("a".repeat(32))
            val info = access.getSiteInfo(token)
            assertEquals(42L, info.userId)
            assertTrue("core_enrol_get_users_courses" in info.functions)
            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("/webservice/rest/server.php", request.uri)
            assertTrue(request.body.contains("wstoken=${token.value}"))
            assertTrue(request.body.contains("wsfunction=core_webservice_get_site_info"))
            assertFalse(request.uri.contains(token.value))
        }
    }

    @Test
    fun `Moodle HTTP 200 authentication errors are failures and upstream messages are not exposed`() {
        withServer(
            """{"exception":"moodle_exception","errorcode":"invalidtoken","message":"secret-value"}"""
        ) { access, _ ->
            val error = assertFailsWith<MoodleException.Authentication> {
                access.getSiteInfo(AccessToken("a".repeat(32)))
            }
            assertFalse(error.message.orEmpty().contains("secret-value"))
        }
    }

    @Test
    fun `HTML responses are reported as unsupported API responses`() {
        withServer("<html>Login page</html>") { access, _ ->
            assertFailsWith<MoodleException.Connection> { access.getSiteInfo(AccessToken("a".repeat(32))) }
        }
    }

    @Test
    fun `redirect responses are not followed with credentials`() {
        withServer("", 302) { access, requests ->
            assertFailsWith<MoodleException.Connection> { access.getSiteInfo(AccessToken("a".repeat(32))) }
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `HTTP unauthorized status invalidates authentication without exposing its body`() {
        withServer("private response", 401) { access, _ ->
            val error = assertFailsWith<MoodleException.Authentication> {
                access.getSiteInfo(AccessToken("a".repeat(32)))
            }
            assertEquals("error.session.expired", error.messageKey)
            assertFalse(error.toString().contains("private response"))
        }
    }

    private fun withServer(response: String, status: Int = 200, test: (RetrofitMoodleAccess, List<Request>) -> Unit) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                Request(
                    exchange.requestMethod,
                    exchange.requestURI.toString(),
                    exchange.requestBody.reader().readText()
                )
            )
            exchange.responseHeaders.add("Content-Type", "application/json")
            if (status == 302) exchange.responseHeaders.add("Location", "/redirected")
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val api = createMoodleApi("http://127.0.0.1:${server.address.port}/", json)
            test(RetrofitMoodleAccess(api, json), requests)
        } finally {
            server.stop(0)
        }
    }

    private data class Request(val method: String, val uri: String, val body: String)

    private companion object {
        val PUBLIC_CONFIG =
            """
            [{"error":false,"data":{
                "wwwroot":"https://moodle.zhaw.ch",
                "sitename":"Test Moodle",
                "enablewebservices":1,
                "enablemobilewebservice":1,
                "typeoflogin":2,
                "maintenanceenabled":0
            }}]
            """.trimIndent()
        val SITE_INFO =
            """
            {
                "siteurl":"https://moodle.zhaw.ch",
                "sitename":"Test Moodle",
                "userid":42,
                "fullname":"Test User",
                "functions":[{"name":"core_enrol_get_users_courses","version":"1"}],
                "ignoredfield":true
            }
            """.trimIndent()
    }
}
