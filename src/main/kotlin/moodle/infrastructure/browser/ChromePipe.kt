package moodle.infrastructure.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import moodle.domain.exceptions.MoodleException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** CDP over inherited process pipes, without a TCP listener or protocol logging. */
internal class ChromePipe(
    private val input: InputStream,
    private val output: OutputStream,
    private val timeout: Duration = Duration.ofSeconds(15)
) : AutoCloseable {
    private val replies = ArrayBlockingQueue<JsonObject>(8)
    private val closed = AtomicBoolean(false)
    private var nextId = 0
    private val reader = thread(name = "moodle-login-pipe", isDaemon = true, block = ::readReplies)

    @Synchronized
    fun call(
        method: String,
        params: JsonObject = buildJsonObject {},
        sessionId: String? = null,
        allowCommandError: Boolean = false
    ): JsonObject? {
        if (closed.get()) throw failure()
        val id = ++nextId
        try {
            sendCommand(id, method, params, sessionId)
            return readResult(awaitReply(id), allowCommandError)
        } catch (error: MoodleException) {
            throw error
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure()
        } catch (_: Exception) {
            throw failure()
        }
    }

    private fun sendCommand(
        id: Int,
        method: String,
        params: JsonObject,
        sessionId: String?
    ) {
        val command = buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
            if (sessionId != null) put("sessionId", sessionId)
        }
        output.write(command.toString().toByteArray(Charsets.UTF_8))
        output.write(0)
        output.flush()
    }

    private fun awaitReply(id: Int): JsonObject {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            val reply = replies.poll(100, TimeUnit.MILLISECONDS)
            if (reply?.get("id")?.jsonPrimitive?.intOrNull == id) return reply
            if (reply == null && closed.get()) throw failure()
        }
        throw failure()
    }

    private fun readResult(reply: JsonObject, allowCommandError: Boolean): JsonObject? {
        if ("error" in reply) {
            if (allowCommandError) return null
            throw failure()
        }
        return reply["result"]?.jsonObject ?: throw failure()
    }

    private fun readReplies() {
        try {
            while (!closed.get()) {
                val bytes = readFrame(input) ?: break
                val message = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                // Discard notifications instead of retaining console output or navigation details.
                if (message["id"] != null && !replies.offer(message)) break
            }
        } catch (_: Exception) {
            // Parser errors may contain secrets, so only the caller's sanitized error is exposed.
        } finally {
            closed.set(true)
        }
    }

    override fun close() {
        closed.set(true)
        runCatching { output.close() }
        runCatching { input.close() }
        reader.interrupt()
        replies.clear()
    }

    private fun failure(): MoodleException.Authentication = MoodleException.Authentication(
        "The isolated login browser closed or did not respond. Try again, or use login --manual."
    )

    private companion object {
        const val MAX_FRAME_BYTES = 1_048_576

        fun readFrame(input: InputStream): ByteArray? {
            val frame = ByteArrayOutputStream()
            while (true) {
                val byte = input.read()
                if (byte == -1) return null
                if (byte == 0) return frame.toByteArray()
                check(frame.size() < MAX_FRAME_BYTES) { "Browser message too large" }
                frame.write(byte)
            }
        }
    }
}
