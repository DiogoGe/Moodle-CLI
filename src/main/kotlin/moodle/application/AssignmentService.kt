package moodle.application

import moodle.domain.AccessToken
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.AssignmentRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

class AssignmentService(
    private val access: AccessService,
    private val repository: AssignmentRepository,
    private val clock: Clock
) {
    fun getAssignments(courseId: Long? = null): AssignmentList {
        if (courseId != null && courseId <= 0) throw MoodleException.Input("error.input.id")
        return access.withSession { token, _ ->
            val result = repository.getAssignments(token, courseId)
            val items = result.items
                .filter { courseId == null || it.courseId == courseId }
                .map { withSubmissionDetails(token, it) }
                .sortedWith(compareBy<Assignment, Instant?>(nullsLast()) { it.dueDate }.thenBy { it.id })
            result.copy(items = items)
        }
    }

    fun getUpcoming(days: Int): AssignmentList {
        if (days <= 0) throw MoodleException.Input("error.input.days")
        val now = clock.instant()
        val end = now.plus(days.toLong(), ChronoUnit.DAYS)
        val result = getAssignments()
        // Extensions must be applied before filtering: a past course deadline may have moved into this window.
        return result.copy(items = result.items.filter { assignment -> assignment.dueDate?.let { it in now..end } ?: false })
    }

    private fun withSubmissionDetails(token: AccessToken, assignment: Assignment): Assignment {
        val details = repository.getSubmissionDetails(token, assignment)
        val dueDate = listOfNotNull(assignment.dueDate, details.extensionDueDate).maxOrNull()
        return assignment.copy(dueDate = dueDate, submissionStatus = details.status)
    }
}
