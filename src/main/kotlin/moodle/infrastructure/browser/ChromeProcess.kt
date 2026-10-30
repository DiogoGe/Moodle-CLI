package moodle.infrastructure.browser

import moodle.domain.exceptions.MoodleException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/** Owns exactly one temporary Chrome process, its private pipes, and its disposable profile. */
internal class ChromeProcess private constructor(
    private val process: Process,
    val connection: ChromePipe,
    private val profile: Path
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
                "The temporary browser could not be fully cleaned up. Close its window before trying again."
            )
        } finally {
            runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun stopProcess() {
        val children: List<ProcessHandle> = process.descendants().use { it.toList() }
        connection.close()
        process.destroy()
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        children.filter { it.isAlive }.forEach { it.destroyForcibly() }
        check(process.waitFor(3, TimeUnit.SECONDS)) { "Browser did not exit" }
    }

    private fun closeAtShutdown() {
        try {
            close()
        } catch (_: Exception) {
            System.err.println("Temporary login browser cleanup failed.")
        }
    }

    companion object {
        fun start(executable: Path, headless: Boolean, profileParent: Path?): ChromeProcess {
            val profile = createProfile(profileParent)
            var process: Process? = null
            try {
                process = launch(executable, profile, headless)
                val connection = ChromePipe(process.inputStream, process.outputStream)
                return ChromeProcess(process, connection, profile)
            } catch (error: Exception) {
                process?.destroyForcibly()
                deleteProfile(profile)
                throw error
            }
        }

        fun findExecutable(): Path {
            val candidates = listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser"
            )
            return candidates.map(Path::of).firstOrNull { Files.isExecutable(it) }
                ?: throw MoodleException.Authentication(
                    "Automatic login needs Chrome or Chromium on macOS/Linux. Use login --manual with your normal browser."
                )
        }

        private fun createProfile(parent: Path?): Path {
            val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            return if (parent == null) {
                Files.createTempDirectory("moodle-cli-login-", permissions)
            } else {
                Files.createTempDirectory(parent, "moodle-cli-login-", permissions)
            }
        }

        private fun launch(executable: Path, profile: Path, headless: Boolean): Process {
            // Fixed shell code only; paths are separate positional arguments, never interpolated shell text.
            // Chrome reads fd 3 and writes fd 4. No debugging TCP listener is opened.
            val bridge = """exec 3<&0 4>&1; exec "${'$'}@" </dev/null >/dev/null"""
            val arguments = mutableListOf(
                "/bin/sh", "-c", bridge, "moodle-login", executable.toString(),
                "--remote-debugging-pipe", "--user-data-dir=$profile", "--no-startup-window",
                "--no-first-run", "--no-default-browser-check", "--disable-sync", "--disable-background-networking",
                "--disable-extensions", "--disable-logging", "--disable-breakpad", "--disable-crash-reporter"
            )
            if (headless) arguments.add("--headless=new")
            val builder = ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment().remove("CHROME_LOG_FILE")
            return builder.start()
        }

        private fun deleteProfile(profile: Path) {
            Files.walk(profile).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
