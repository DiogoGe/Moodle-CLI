package moodle.infrastructure.api.repository

import moodle.domain.entities.Assignment
import moodle.domain.entities.Course
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleRepository
import moodle.infrastructure.api.interfaces.MoodleApi

/** Adapter boundary; real requests and DTO mapping are not implemented yet. */
internal class RetrofitMoodleRepository(private val api: MoodleApi) : MoodleRepository {
    override fun getCourses(): List<Course> = throw MoodleException.FeatureUnavailable("Course API integration")

    override fun getAssignments(): List<Assignment> = throw MoodleException.FeatureUnavailable("Assignment API integration")
}
