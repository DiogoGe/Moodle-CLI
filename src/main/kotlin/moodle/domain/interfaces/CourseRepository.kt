package moodle.domain.interfaces

import moodle.domain.AccessToken
import moodle.domain.entities.Course
import moodle.domain.entities.CourseSection

interface CourseRepository {
    fun getCourses(token: AccessToken, userId: Long): List<Course>

    fun getContents(token: AccessToken, courseId: Long): List<CourseSection>
}
