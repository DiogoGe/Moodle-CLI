package moodle.infrastructure

import com.sun.net.httpserver.HttpServer
import moodle.domain.AccessToken
import moodle.domain.entities.CourseFile
import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.files.LocalCourseFiles
import moodle.infrastructure.files.MoodleFileClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalCourseFilesTest {
    private val token = AccessToken("a".repeat(32))

    @Test
    fun `download preserves bytes and sends token only in POST body`() {
        withServer("PDF fixture") { files, base, directory, requests ->
            val result = files.download(token, file(base), directory.toString())
            assertEquals("PDF fixture", Files.readString(Path.of(result.path)))
            assertEquals(11L, result.bytes)
            assertTrue(requests.single().startsWith("POST /webservice/pluginfile.php/1/test "))
            assertTrue(requests.single().endsWith("token=${token.value}"))
        }
    }

    @Test
    fun `existing files are never overwritten`() {
        withServer("replacement") { files, base, directory, _ ->
            val target = directory.resolve("test.pdf")
            Files.writeString(target, "original")
            val error = assertFailsWith<MoodleException.Download> { files.download(token, file(base), directory.toString()) }
            assertEquals("error.download.exists", error.messageKey)
            assertEquals("original", Files.readString(target))
        }
    }

    @Test
    fun `existing symlinks are never overwritten`() {
        withServer("replacement") { files, base, directory, _ ->
            val target = Files.writeString(directory.resolve("test.pdf"), "original")
            try {
                Files.createSymbolicLink(directory.resolve("link.pdf"), target)
            } catch (_: java.nio.file.FileSystemException) {
                org.junit.jupiter.api.Assumptions
                    .assumeFalse(System.getProperty("os.name").startsWith("Windows"))
                error("Symbolic link fixture unavailable")
            }
            assertFailsWith<MoodleException.Download> {
                files.download(token, file(base).copy(name = "link.pdf"), directory.toString())
            }
            assertEquals("original", Files.readString(target))
        }
    }

    @Test
    fun `unsafe filenames and untrusted endpoints are refused before any request`() {
        withServer("unused") { files, base, directory, requests ->
            for (name in listOf("../escape", "a/b", "a\\b", ".", "bad\u001b.pdf", "NUL.pdf", "con", "COM1.txt", "lpt².pdf")) {
                assertFailsWith<MoodleException.Download> {
                    files.download(token, file(base).copy(name = name), directory.toString())
                }
            }
            for (url in listOf(
                "http://untrusted.example/webservice/pluginfile.php/a",
                "$base/pluginfile.php/a",
                "$base/admin/"
            )) {
                assertFailsWith<MoodleException.Download> {
                    files.download(token, file(base).copy(url = url), directory.toString())
                }
            }
            assertTrue(requests.isEmpty())
            assertTrue(Files.list(directory).use { it.findAny().isEmpty })
        }
    }

    @Test
    fun `redirects are not followed and Moodle errors are not saved or exposed`() {
        withServer("", 302) { files, base, directory, requests ->
            assertFailsWith<MoodleException.Download> { files.download(token, file(base), directory.toString()) }
            assertEquals(1, requests.size)
            assertFalse(Files.exists(directory.resolve("test.pdf")))
        }
        withServer("""{"exception":"error","errorcode":"denied","message":"secret"}""") { files, base, directory, _ ->
            val error = assertFailsWith<MoodleException.Download> { files.download(token, file(base), directory.toString()) }
            assertFalse(error.message.orEmpty().contains("secret"))
            assertFalse(Files.exists(directory.resolve("test.pdf")))
        }
    }

    @Test
    fun `expired file credentials are reported as an authentication failure`() {
        withServer("""{"exception":"error","errorcode":"invalidtoken"}""") { files, base, _, _ ->
            assertFailsWith<MoodleException.Authentication> { files.readText(token, file(base)) }
        }
    }

    @Test
    fun `text reading is bounded and URL supplied tokens are discarded`() {
        withServer("<p>Text</p>") { files, base, _, requests ->
            val resource = file(base).copy(url = "$base/webservice/pluginfile.php?file=/1/test&token=old-secret")
            assertEquals("<p>Text</p>", files.readText(token, resource))
            assertFalse(requests.single().contains("old-secret"))
            assertTrue(requests.single().contains("file=%2F1%2Ftest"))
        }
        withServer("x".repeat(2 * 1024 * 1024 + 1)) { files, base, _, _ ->
            assertFailsWith<MoodleException.Download> { files.readText(token, file(base)) }
        }
    }

    @Test
    fun `interrupted transfers remove partial files`() {
        withServer("x".repeat(8192), declaredLength = 16384) { files, base, directory, _ ->
            assertFailsWith<MoodleException.Download> { files.download(token, file(base), directory.toString()) }
            assertFalse(Files.exists(directory.resolve("test.pdf")))
        }
    }

    private fun file(base: String): CourseFile = CourseFile("test.pdf", "$base/webservice/pluginfile.php/1/test", 0, null)

    private fun withServer(
        body: String,
        status: Int = 200,
        declaredLength: Long? = null,
        test: (LocalCourseFiles, String, Path, List<String>) -> Unit
    ) {
        val requests = mutableListOf<String>()
        val directory = Files.createTempDirectory("moodle-download-test-")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestBody.reader().readText()}")
            if (status == 302) exchange.responseHeaders.add("Location", "/redirect")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, declaredLength ?: bytes.size.toLong())
            try {
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) {
                // The truncated-response fixture deliberately closes before Content-Length is satisfied.
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            test(LocalCourseFiles(MoodleFileClient("$base/")), base, directory, requests)
        } finally {
            server.stop(0)
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
}
