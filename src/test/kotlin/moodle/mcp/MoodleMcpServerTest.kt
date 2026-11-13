package moodle.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import moodle.fixtures.McpFixture
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoodleMcpServerTest {
    @Test
    @Timeout(20)
    fun `stdio handshake tools list tool calls and EOF work without terminal output`() {
        val fixture = McpFixture()
        val executor = Executors.newSingleThreadExecutor()
        val input = PipedInputStream(65536)
        val client = PipedOutputStream(input)
        val responses = PipedInputStream(65536)
        val output = PipedOutputStream(responses)
        val server = executor.submit { runBlocking { MoodleMcpServer(fixture.tools).serve(input, output) } }
        val reader = responses.bufferedReader()
        val downloading = CountDownLatch(1)
        val releaseDownload = CountDownLatch(1)
        fixture.beforeDownload = {
            downloading.countDown()
            check(releaseDownload.await(5, TimeUnit.SECONDS))
        }
        try {
            send(
                client,
                1,
                "initialize",
                """{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"fixture","version":"test"}}"""
            )
            val handshake = Json.parseToJsonElement(reader.readLine()).jsonObject
            assertEquals("1", handshake["id"].toString())
            assertTrue(handshake["result"].toString().contains("moodle-cli"))
            client.write("""{"jsonrpc":"2.0","method":"notifications/initialized"}""".plus("\n").toByteArray())
            client.flush()
            send(client, 2, "tools/list")
            val tools = Json
                .parseToJsonElement(reader.readLine())
                .jsonObject["result"]
                ?.jsonObject
                ?.get("tools")
                ?.jsonArray
            assertEquals(11, tools?.size)
            send(client, 3, "tools/call", """{"name":"list_courses","arguments":{"limit":1}}""")
            val reply = Json.parseToJsonElement(reader.readLine()).jsonObject
            assertEquals("3", reply["id"].toString())
            val data = reply["result"]?.jsonObject?.get("structuredContent")?.jsonObject
            assertEquals(1, data?.get("items")?.jsonArray?.size)
            assertFalse(reply.toString().contains(fixture.token))
            send(
                client,
                4,
                "tools/call",
                """{"name":"download_course_file","arguments":{"course_id":1,"module_id":5,"file_number":1}}"""
            )
            assertTrue(downloading.await(5, TimeUnit.SECONDS))
            send(client, 5, "tools/call", """{"name":"list_courses","arguments":{"limit":1}}""")
            assertEquals("5", Json.parseToJsonElement(reader.readLine()).jsonObject["id"].toString())
            releaseDownload.countDown()
            val download = Json.parseToJsonElement(reader.readLine()).jsonObject
            assertEquals("4", download["id"].toString())
            assertEquals("false", download["result"]?.jsonObject?.get("isError").toString())
            client.close()
            server.get(5, TimeUnit.SECONDS)
        } finally {
            releaseDownload.countDown()
            client.close()
            input.close()
            output.close()
            responses.close()
            executor.shutdownNow()
        }
    }

    @Test
    @Timeout(10)
    fun `EOF before initialization also closes the owned session`() = runBlocking {
        val server = MoodleMcpServer(McpFixture().tools)
        withTimeout(4000) {
            server.serve(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        }
    }

    private fun send(
        output: PipedOutputStream,
        id: Int,
        method: String,
        params: String = "{}"
    ) {
        val request: JsonObject = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", Json.parseToJsonElement(params))
        }
        output.write((request.toString() + "\n").toByteArray())
        output.flush()
    }
}
