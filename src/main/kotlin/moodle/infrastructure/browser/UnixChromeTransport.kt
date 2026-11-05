package moodle.infrastructure.browser

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

internal class UnixChromeTransport(arguments: List<String>) : ChromeTransport {
    private val process: Process

    init {
        // Fixed shell text only; paths and switches are separate positional arguments.
        val bridge = """exec 3<&0 4>&1; exec "${'$'}@" </dev/null >/dev/null"""
        val builder = ProcessBuilder(listOf("/bin/sh", "-c", bridge, "moodle-login") + arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        builder.environment().remove("CHROME_LOG_FILE")
        process = builder.start()
    }

    override val input: InputStream get() = process.inputStream
    override val output: OutputStream get() = process.outputStream
    override val isAlive: Boolean get() = process.isAlive

    override fun close() {
        val children = process.descendants().use { it.toList() }
        output.close()
        input.close()
        process.destroy()
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        children.filter { it.isAlive }.forEach { it.destroyForcibly() }
        check(process.waitFor(3, TimeUnit.SECONDS)) { "Browser did not exit" }
    }
}
