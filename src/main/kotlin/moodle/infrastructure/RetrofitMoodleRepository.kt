package moodle.infrastructure

import moodle.domain.Assignment
import moodle.domain.Course
import moodle.domain.MoodleException
import moodle.domain.MoodleRepository

/** Adapter boundary; real requests and DTO mapping are not implemented yet. */
class RetrofitMoodleRepository(
    private val api: MoodleApi,
) : MoodleRepository {
    override fun getCourses(): List<Course> =
        throw MoodleException.FeatureUnavailable("Course API integration")

    override fun getAssignments(): List<Assignment> =
        throw MoodleException.FeatureUnavailable("Assignment API integration")
}
