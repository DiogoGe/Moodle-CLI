package moodle.infrastructure.browser

import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.windows.WindowsChromeTransport
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path

/** Owns exactly one temporary Chrome process, its private pipes, and its disposable profile. */
internal class ChromeProcess private constructor(
    private val process: ChromeTransport,
    val connection: ChromePipe,
    private val profile: Path,
    private val onCleanupFailure: () -> Unit
) : AutoCloseable {
    private var closed = false
    private val shutdownHook = Thread(::closeAtShutdown, "moodle-login-cleanup")

    val isAlive: Boolean get() = process.isAlive

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val interrupted = Thread.interrupted()
        try {
            stopProcess()
            deleteProfile(profile)
        } catch (_: Exception) {
            throw MoodleException.Authentication(
                "error.browser.cleanup"
            )
        } finally {
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun stopProcess() {
        try {
            process.close()
        } finally {
            connection.close()
        }
    }

    private fun closeAtShutdown() {
        try {
            close()
        } catch (_: Exception) {
            onCleanupFailure()
        }
    }

    companion object {
        fun start(
            executable: Path,
            headless: Boolean,
            profileParent: Path?,
            onCleanupFailure: () -> Unit
        ): ChromeProcess {
            val profile = createPrivateBrowserProfile(profileParent)
            var process: ChromeTransport? = null
            try {
                process = launch(executable, profile, headless)
                val connection = ChromePipe(process.input, process.output)
                return ChromeProcess(process, connection, profile, onCleanupFailure)
            } catch (error: Throwable) {
                try {
                    process?.close()
                } finally {
                    deleteProfile(profile)
                }
                throw error
            }
        }

        fun findExecutable(): Path = findChromeExecutable()

        private fun launch(executable: Path, profile: Path, headless: Boolean): ChromeTransport {
            val arguments = mutableListOf(
                executable.toString(), "--remote-debugging-pipe", "--user-data-dir=$profile", "--no-startup-window",
                "--no-first-run", "--no-default-browser-check", "--disable-sync", "--disable-background-networking",
                "--disable-extensions", "--disable-logging", "--disable-breakpad", "--disable-crash-reporter"
            )
            if (headless) arguments.add("--headless=new")
            return if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                WindowsChromeTransport.start(arguments)
            } else {
                UnixChromeTransport(arguments)
            }
        }

        private fun deleteProfile(profile: Path) {
            repeat(10) { attempt ->
                try {
                    if (!Files.exists(profile)) return
                    Files.walk(profile).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                    return
                } catch (error: FileSystemException) {
                    if (!System.getProperty("os.name").startsWith("Windows") || attempt == 9) throw error
                    Thread.sleep(100)
                }
            }
        }
    }
}
