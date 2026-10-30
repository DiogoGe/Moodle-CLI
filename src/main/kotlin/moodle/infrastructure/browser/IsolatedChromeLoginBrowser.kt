package moodle.infrastructure.browser

import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.LoginBrowser
import java.net.URI
import java.nio.file.Path
import java.time.Duration

/** Coordinates login; process ownership and page interaction have separate responsibilities. */
internal class IsolatedChromeLoginBrowser(
    private val executable: () -> Path = { ChromeProcess.findExecutable() },
    private val timeout: Duration = Duration.ofMinutes(10),
    private val headless: Boolean = false,
    private val profileParent: Path? = null
) : LoginBrowser {
    override fun receiveCallback(launchUrl: URI): String {
        try {
            return ChromeProcess.start(executable(), headless, profileParent).use { browser ->
                val page = ChromeLoginPage.open(browser.connection, launchUrl)
                awaitCallback(browser, page)
            }
        } catch (error: MoodleException) {
            throw error
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MoodleException.Authentication("Browser login cancelled.")
        } catch (_: Exception) {
            throw MoodleException.Authentication("Automatic browser login failed. Try login --manual.")
        }
    }

    private fun awaitCallback(browser: ChromeProcess, page: ChromeLoginPage): String {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (!browser.isAlive) throw MoodleException.Authentication("Browser login cancelled.")
            val callback = page.readCallback()
            if (callback != null) return callback
            Thread.sleep(200)
        }
        throw MoodleException.Authentication("Browser login timed out. Try again, or use login --manual.")
    }
}
