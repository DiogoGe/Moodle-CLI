package moodle.infrastructure

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import moodle.fixtures.FailingMoodleCall
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.executeMoodleRead
import moodle.infrastructure.api.interfaces.MoodleApi
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoodleReadRequestsTest {
    @Test
    fun `interrupted read connections are retried once with the same POST request`() {
        withServer { api, calls ->
            val response = api.getSiteInfo("a".repeat(32)).executeMoodleRead()
            assertTrue(response.isSuccessful)
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun `unknown or mutating functions are never retried`() {
        withServer { api, calls ->
            assertFailsWith<IOException> {
                api.getSiteInfo("a".repeat(32), function = "mod_assign_submit_for_grading").executeMoodleRead()
            }
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun `persistent transport failures stop after two requests`() {
        withServer(alwaysFail = true) { api, calls ->
            assertFailsWith<IOException> { api.getSiteInfo("a".repeat(32)).executeMoodleRead() }
            assertEquals(2, calls.get())
        }
    }

    @Test
    fun `TLS resolution interruption and cancellation failures are never retried`() {
        val failures =
            listOf(SSLException("TLS failure"), UnknownHostException("resolution failure"), InterruptedIOException("cancel"))
        failures.forEach { failure ->
            val call = FailingMoodleCall(failure)
            assertFailsWith<IOException> { call.executeMoodleRead() }
            assertEquals(1, call.calls.get())
        }
        val cancelled = FailingMoodleCall(IOException("cancelled"))
        cancelled.cancel()
        assertFailsWith<IOException> { cancelled.executeMoodleRead() }
        assertEquals(1, cancelled.calls.get())
    }

    private fun withServer(alwaysFail: Boolean = false, test: (MoodleApi, AtomicInteger) -> Unit) {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            val attempt = calls.incrementAndGet()
            if (attempt == 1 || alwaysFail) {
                exchange.close()
            } else {
                val bytes = "{}".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
        }
        server.start()
        try {
            test(createMoodleApi("http://127.0.0.1:${server.address.port}/", Json), calls)
        } finally {
            server.stop(0)
        }
    }
}
