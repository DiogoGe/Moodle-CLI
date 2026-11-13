package moodle.infrastructure.api.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import moodle.domain.AccessToken
import moodle.domain.entities.Course
import moodle.domain.entities.CourseSection
import moodle.domain.interfaces.CourseRepository
import moodle.infrastructure.api.dtos.CourseDto
import moodle.infrastructure.api.dtos.CourseSectionDto
import moodle.infrastructure.api.interfaces.MoodleApi
import moodle.infrastructure.api.readMoodle
import moodle.infrastructure.api.toDomain

internal class RetrofitCourseRepository(private val api: MoodleApi, private val json: Json) : CourseRepository {
    override fun getCourses(token: AccessToken, userId: Long): List<Course> =
        api.getCourses(token.value, userId).readMoodle("error.api.courseResponse", "error.api.courseAccess") { body ->
            json.decodeFromJsonElement<List<CourseDto>>(body).map { Course(it.id, it.fullname, it.summary.orEmpty()) }
        }

    override fun getContents(token: AccessToken, courseId: Long): List<CourseSection> =
        api.getCourseContents(token.value, courseId).readMoodle("error.api.courseResponse", "error.api.courseAccess") { body ->
            json.decodeFromJsonElement<List<CourseSectionDto>>(body).map { it.toDomain() }
        }
}
