package moodle.domain.interfaces

import moodle.domain.entities.Assignment
import moodle.domain.entities.Course

interface MoodleRepository {
    fun getCourses(): List<Course>

    fun getAssignments(): List<Assignment>
}
