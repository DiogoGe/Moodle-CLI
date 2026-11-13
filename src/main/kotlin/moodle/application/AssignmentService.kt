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
        return loadAssignments(courseId)
    }

    fun getUpcoming(days: Int): AssignmentList {
        if (days <= 0) throw MoodleException.Input("error.input.days")
        val now = clock.instant()
        val end = now.plus(days.toLong(), ChronoUnit.DAYS)
        return loadAssignments(courseId = null, window = now..end)
    }

    private fun loadAssignments(courseId: Long?, window: ClosedRange<Instant>? = null): AssignmentList =
        access.withSession { token, _ ->
            val result = repository.getAssignments(token, courseId)
            val items = result.items
                .asSequence()
                .filter { courseId == null || it.courseId == courseId }
                // An extension cannot move a deadline that is already beyond the window back into it.
                .filter { window == null || it.dueDate?.isAfter(window.endInclusive) != true }
                .map { withSubmissionDetails(token, it) }
                .filter { assignment -> window == null || assignment.dueDate?.let { it in window } == true }
                .sortedWith(compareBy<Assignment, Instant?>(nullsLast()) { it.dueDate }.thenBy { it.id })
                .toList()
            result.copy(items = items)
        }

    private fun withSubmissionDetails(token: AccessToken, assignment: Assignment): Assignment {
        val details = repository.getSubmissionDetails(token, assignment)
        val dueDate = listOfNotNull(assignment.dueDate, details.extensionDueDate).maxOrNull()
        return assignment.copy(dueDate = dueDate, submissionStatus = details.status)
    }
}
