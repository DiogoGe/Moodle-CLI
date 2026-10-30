package moodle.domain

interface MoodleRepository {
    fun getCourses(): List<Course>

    fun getAssignments(): List<Assignment>
}
