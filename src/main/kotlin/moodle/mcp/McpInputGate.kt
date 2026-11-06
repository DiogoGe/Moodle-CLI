package moodle.mcp

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch

/** Prevent EOF from closing a session before the SDK finishes registering it. */
internal class McpInputGate(input: InputStream) : FilterInputStream(input) {
    private val registered = CountDownLatch(1)

    fun open() = registered.countDown()

    override fun read(): Int {
        awaitRegistration()
        return super.read()
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        awaitRegistration()
        return super.read(bytes, offset, length)
    }

    override fun close() {
        open()
        super.close()
    }

    private fun awaitRegistration() {
        try {
            registered.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("MCP input interrupted")
        }
    }
}
