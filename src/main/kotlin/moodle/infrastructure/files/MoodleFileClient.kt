package moodle.infrastructure.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import moodle.domain.AccessToken
import moodle.domain.entities.CourseFile
import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.api.createMoodleHttpClient
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/** Sends file credentials in the POST body, only to the configured Moodle file endpoint. */
internal class MoodleFileClient(baseUrl: String, private val http: OkHttpClient = createMoodleHttpClient()) {
    private val site = baseUrl.toHttpUrl()
    private val endpoint = site.encodedPath + "webservice/pluginfile.php"

    fun <T> read(token: AccessToken, file: CourseFile, consume: (InputStream) -> T): T {
        try {
            val request = Request
                .Builder()
                .url(validateUrl(file.url))
                .post(FormBody.Builder().add("token", token.value).build())
                .build()
            return http.newCall(request).execute().use { response ->
                checkResponse(response)
                val body = response.body ?: throw MoodleException.Download("Moodle returned an empty file response.")
                body.byteStream().buffered().use { input ->
                    checkApiError(input)
                    consume(input)
                }
            }
        } catch (_: IOException) {
            throw MoodleException.Download("File transfer failed. Check the connection and destination directory.")
        }
    }

    private fun validateUrl(value: String): HttpUrl {
        val url = try {
            value.toHttpUrl()
        } catch (_: IllegalArgumentException) {
            throw MoodleException.Download("Moodle returned an invalid file URL.")
        }
        val sameOrigin = url.scheme == site.scheme && url.host == site.host && url.port == site.port
        val fileEndpoint = url.encodedPath == endpoint || url.encodedPath.startsWith("$endpoint/")
        if (!sameOrigin || !fileEndpoint || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw MoodleException.Download("Only files served by this Moodle instance can be downloaded.")
        }
        // Keep only the file argument for installations without slash arguments; discard any supplied credentials.
        val builder = url.newBuilder().query(null).fragment(null)
        url.queryParameter("file")?.let { builder.addQueryParameter("file", it) }
        return builder.build()
    }

    private fun checkResponse(response: Response) {
        if (response.code == 401) throw MoodleException.Authentication("Session expired. Sign in again.")
        if (!response.isSuccessful) {
            throw MoodleException.Download("Moodle file request returned HTTP ${response.code}. Redirects are not followed.")
        }
    }

    private fun checkApiError(input: BufferedInputStream) {
        input.mark(ERROR_PREFIX_BYTES)
        val prefix = input.readNBytes(ERROR_PREFIX_BYTES).toString(Charsets.UTF_8).trimStart()
        input.reset()
        if (prefix.startsWith("{") && prefix.contains("\"exception\"") && prefix.contains("\"errorcode\"")) {
            val error = runCatching { Json.parseToJsonElement(prefix) as? JsonObject }.getOrNull()
            val code = (error?.get("errorcode") as? JsonPrimitive)?.contentOrNull
            if (code in setOf("invalidtoken", "requireloginerror")) {
                throw MoodleException.Authentication("Session expired. Sign in again.")
            }
            throw MoodleException.Download("Moodle refused this file. Check file access and Web Service download permissions.")
        }
    }

    private companion object {
        const val ERROR_PREFIX_BYTES = 4096
    }
}
