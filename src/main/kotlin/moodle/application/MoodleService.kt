package moodle.application

import moodle.domain.entities.Assignment
import moodle.domain.entities.Course
import moodle.domain.interfaces.MoodleRepository
import java.time.Clock
import java.time.temporal.ChronoUnit

class MoodleService(private val repository: MoodleRepository, private val clock: Clock) {
    fun getCourses(): List<Course> = repository.getCourses()

    fun getAssignments(courseId: Long? = null): List<Assignment> =
        repository.getAssignments().filter { courseId == null || it.courseId == courseId }

    fun getUpcoming(days: Int): List<Assignment> {
        require(days > 0) { "Days must be greater than zero." }
        val now = clock.instant()
        val end = now.plus(days.toLong(), ChronoUnit.DAYS)
        val upcoming = repository.getAssignments().filter { assignment ->
            assignment.dueDate?.let { it in now..end } ?: false
        }
        return upcoming.sortedBy { it.dueDate }
    }
}
