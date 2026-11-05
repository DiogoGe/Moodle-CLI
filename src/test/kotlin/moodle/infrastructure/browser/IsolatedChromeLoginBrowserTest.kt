package moodle.infrastructure.browser

import com.sun.net.httpserver.HttpServer
import moodle.application.BrowserLoginService
import moodle.domain.exceptions.MoodleException
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "MOODLE_CHROME_TESTS", matches = "true")
class IsolatedChromeLoginBrowserTest {
    @TempDir
    lateinit var profiles: Path

    @Test
    fun `real Chrome captures and validates the callback and removes its private profile`() {
        val permissionsChecked = AtomicBoolean(false)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/admin/tool/mobile/launch.php") { exchange ->
            Files.list(profiles).use { entries ->
                val profile = entries.findFirst().orElseThrow()
                permissionsChecked.set(
                    hasPrivatePermissions(profile) &&
                        !Files.exists(profile.resolve("DevToolsActivePort"))
                )
            }
            val passport = exchange.requestURI.rawQuery
                .split('&')
                .single { it.startsWith("passport=") }
                .substringAfter('=')
            val site = "http://127.0.0.1:${server.address.port}"
            val hash = MessageDigest
                .getInstance("MD5")
                .digest((site + passport).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val payload = Base64.getEncoder().encodeToString("$hash:::${"a".repeat(32)}".toByteArray())
            val html =
                """
                <!doctype html><html><body>
                <a id="launchapp" href="moodle-cli-fixture://token=$payload">Open app</a>
                <script>window.onload = () => document.getElementById('launchapp').click();</script>
                </body></html>
                """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
            exchange.close()
        }
        server.start()
        try {
            val service = BrowserLoginService("http://127.0.0.1:${server.address.port}/", Clock.systemUTC())
            val browser = IsolatedChromeLoginBrowser(
                timeout = Duration.ofSeconds(10),
                headless = true,
                profileParent = profiles
            )
            assertEquals("a".repeat(32), service.authenticate(browser).value)
            assertTrue(permissionsChecked.get())
            Files.list(profiles).use { assertEquals(0L, it.count()) }
        } finally {
            server.stop(0)
        }
    }

    private fun hasPrivatePermissions(profile: Path): Boolean {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return Files.getPosixFilePermissions(profile) == PosixFilePermissions.fromString("rwx------")
        }
        val view = Files.getFileAttributeView(profile, AclFileAttributeView::class.java)
        val permissions = view.acl
        return permissions.size == 1 && permissions.single().type() == AclEntryType.ALLOW &&
            permissions.single().principal() == view.owner
    }

    @Test
    fun `callbacks outside the expected launch page are ignored and timeout cleans up`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestURI.path == "/admin/tool/mobile/launch.php") {
                exchange.responseHeaders.add("Location", "/other-page")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val html = """<a id="launchapp" href="moodle-cli-fixture://token=untrusted">App</a>""".toByteArray()
                exchange.sendResponseHeaders(200, html.size.toLong())
                exchange.responseBody.use { it.write(html) }
            }
            exchange.close()
        }
        server.start()
        try {
            val url = URI("http://127.0.0.1:${server.address.port}/admin/tool/mobile/launch.php?passport=test")
            val browser = IsolatedChromeLoginBrowser(
                timeout = Duration.ofSeconds(1),
                headless = true,
                profileParent = profiles
            )
            assertFailsWith<MoodleException.Authentication> { browser.receiveCallback(url) }
            Files.list(profiles).use { assertEquals(0L, it.count()) }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `browser start failure cleans up the temporary profile`() {
        val browser = IsolatedChromeLoginBrowser(
            executable = { Path.of("/nonexistent/moodle-test-browser") },
            headless = true,
            profileParent = profiles
        )
        assertFailsWith<MoodleException.Authentication> {
            browser.receiveCallback(URI("https://moodle.example/admin/tool/mobile/launch.php?passport=test"))
        }
        Files.list(profiles).use { assertEquals(0L, it.count()) }
    }
}
