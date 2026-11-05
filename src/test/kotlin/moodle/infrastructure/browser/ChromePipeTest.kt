package moodle.infrastructure.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import moodle.domain.exceptions.MoodleException
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ChromePipeTest {
    @Test
    fun `matches replies while discarding browser notifications`() {
        withPipe({ request ->
            val event = """{"method":"Runtime.consoleAPICalled","params":{"value":"do-not-log"}}"""
            val reply = buildJsonObject {
                put("id", request.getValue("id"))
                put("result", buildJsonObject { put("status", "ok") })
            }
            "$event\u0000$reply\u0000"
        }) { pipe ->
            val result = pipe.call("Test.method", sessionId = "test-session")
            assertEquals("\"ok\"", result?.get("status").toString())
        }
    }

    @Test
    fun `browser command errors never disclose protocol error details`() {
        withPipe({ request ->
            buildJsonObject {
                put("id", request.getValue("id"))
                put("error", buildJsonObject { put("message", "sensitive-callback") })
            }.toString() + "\u0000"
        }) { pipe ->
            val error = assertFailsWith<MoodleException.Authentication> { pipe.call("Test.method") }
            assertFalse(error.message.orEmpty().contains("sensitive-callback"))
        }
    }

    @Test
    fun `a non-responsive browser has a bounded timeout`() {
        withPipe({ "" }) { pipe ->
            assertFailsWith<MoodleException.Authentication> { pipe.call("Test.method") }
        }
    }

    @Test
    fun `malformed protocol frames do not escape as parser errors`() {
        withPipe({ "private-invalid-json\u0000" }) { pipe ->
            val error = assertFailsWith<MoodleException.Authentication> { pipe.call("Test.method") }
            assertFalse(error.message.orEmpty().contains("private-invalid-json"))
        }
    }

    private fun withPipe(reply: (JsonObject) -> String, test: (ChromePipe) -> Unit) {
        val commands = PipedInputStream()
        val commandOutput = PipedOutputStream(commands)
        val responses = PipedInputStream()
        val responseOutput = PipedOutputStream(responses)
        val worker = thread(isDaemon = true) {
            runCatching {
                val frame = ByteArrayOutputStream()
                while (true) {
                    val value = commands.read()
                    if (value == -1) return@runCatching
                    if (value == 0) break
                    frame.write(value)
                }
                val request = Json.parseToJsonElement(frame.toString(Charsets.UTF_8)).jsonObject
                responseOutput.write(reply(request).toByteArray())
                responseOutput.flush()
                // Keep the fake browser pipe alive until the caller closes it.
                while (commands.read() != -1) {
                    // Wait for the client to close the pipe.
                }
            }
        }
        try {
            ChromePipe(responses, commandOutput, Duration.ofMillis(500)).use(test)
        } finally {
            commands.close()
            responseOutput.close()
            worker.join(1000)
        }
    }
}
