package moodle.infrastructure.files

import moodle.domain.AccessToken
import moodle.domain.entities.CourseFile
import moodle.domain.entities.DownloadedFile
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.CourseFiles
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class LocalCourseFiles(private val client: MoodleFileClient) : CourseFiles {
    override fun download(token: AccessToken, file: CourseFile, directory: String): DownloadedFile {
        validateFileName(file.name)
        if (file.size > MAX_DOWNLOAD_BYTES) throw MoodleException.Download("error.download.tooLarge")
        try {
            val root = Files.createDirectories(Path.of(directory)).toRealPath()
            val target = root.resolve(file.name)
            return client.read(token, file) { input -> save(input, target) }
        } catch (_: InvalidPathException) {
            throw MoodleException.Download("error.download.directory")
        } catch (_: java.io.IOException) {
            throw MoodleException.Download("error.download.save")
        }
    }

    override fun readText(token: AccessToken, file: CourseFile): String = client.read(token, file) { input ->
        val bytes = input.readNBytes(MAX_TEXT_BYTES + 1)
        if (bytes.size > MAX_TEXT_BYTES) throw MoodleException.Download("error.download.textTooLarge")
        bytes.toString(Charsets.UTF_8)
    }

    private fun save(input: InputStream, target: Path): DownloadedFile {
        // CREATE_NEW also rejects existing symlinks. Delete only the file this invocation created if transfer fails.
        val output = try {
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (_: FileAlreadyExistsException) {
            throw MoodleException.Download("error.download.exists")
        }
        try {
            val bytes = output.use { stream ->
                var total = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    total += count
                    if (total > MAX_DOWNLOAD_BYTES) throw MoodleException.Download("error.download.tooLarge")
                    stream.write(buffer, 0, count)
                }
                total
            }
            return DownloadedFile(target.toString(), bytes)
        } catch (error: Exception) {
            Files.deleteIfExists(target)
            throw error
        }
    }

    private fun validateFileName(name: String) {
        val deviceName = name.substringBefore('.').trimEnd(' ').uppercase(java.util.Locale.ROOT)
        val reserved = deviceName in setOf("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$") ||
            Regex("(?:COM|LPT)[1-9¹²³]").matches(deviceName)
        val unsafe =
            reserved || name.isBlank() || name in setOf(".", "..") || name.any { it.isISOControl() || it in "\\/:*?\"<>|" }
        if (unsafe || name.endsWith('.') || name.endsWith(' ')) {
            throw MoodleException.Download("error.download.unsafeName")
        }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    }
}
