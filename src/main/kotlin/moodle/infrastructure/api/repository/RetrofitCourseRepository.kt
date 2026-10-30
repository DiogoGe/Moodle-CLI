package moodle.infrastructure.api.repository

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import moodle.domain.AccessToken
import moodle.domain.entities.Course
import moodle.domain.entities.CourseSection
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.CourseRepository
import moodle.infrastructure.api.dtos.CourseDto
import moodle.infrastructure.api.dtos.CourseSectionDto
import moodle.infrastructure.api.interfaces.MoodleApi
import moodle.infrastructure.api.toDomain
import retrofit2.Call
import java.io.IOException

internal class RetrofitCourseRepository(private val api: MoodleApi, private val json: Json) : CourseRepository {
    override fun getCourses(token: AccessToken, userId: Long): List<Course> = load(api.getCourses(token.value, userId)) { body ->
        json.decodeFromJsonElement<List<CourseDto>>(body).map { Course(it.id, it.fullname) }
    }

    override fun getContents(token: AccessToken, courseId: Long): List<CourseSection> =
        load(api.getCourseContents(token.value, courseId)) { body ->
            json.decodeFromJsonElement<List<CourseSectionDto>>(body).map { it.toDomain() }
        }

    private fun <T> load(call: Call<JsonElement>, decode: (JsonElement) -> T): T {
        try {
            val response = call.execute()
            if (!response.isSuccessful) throw MoodleException.Connection("Moodle API returned HTTP ${response.code()}.")
            val body = response.body() ?: throw MoodleException.Connection("Moodle returned an empty response.")
            if (body is JsonObject) throw apiError(body)
            return decode(body)
        } catch (_: IOException) {
            throw MoodleException.Connection("Cannot reach Moodle. Check the connection and try again.")
        } catch (_: SerializationException) {
            throw MoodleException.Connection("Moodle returned an unsupported course response.")
        } catch (_: IllegalArgumentException) {
            throw MoodleException.Connection("Moodle returned an unsupported course response.")
        }
    }

    private fun apiError(body: JsonObject): MoodleException = when (body["errorcode"]?.toString()?.trim('"')) {
        "invalidtoken", "requireloginerror" -> MoodleException.Authentication("Session expired. Sign in again.")
        else -> MoodleException.Connection("Course data is unavailable. Check course access and Web Service permissions.")
    }
}
