package moodle.infrastructure.api

import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.SubmissionDetails
import moodle.domain.enums.SubmissionStatus
import moodle.infrastructure.api.dtos.AssignmentListDto
import moodle.infrastructure.api.dtos.SubmissionDetailsDto
import java.time.Instant

internal fun AssignmentListDto.toDomain(): AssignmentList = AssignmentList(
    items = courses.flatMap { course ->
        course.assignments.map { assignment ->
            Assignment(
                id = assignment.id,
                courseId = course.id,
                name = assignment.name,
                dueDate = assignment.duedate.toDeadline(),
                courseName = course.fullname,
                groupSubmission = assignment.teamsubmission == 1,
                submissionsEnabled = assignment.nosubmissions == 0
            )
        }
    },
    partial = warnings.isNotEmpty()
)

internal fun SubmissionDetailsDto.toDomain(groupSubmission: Boolean): SubmissionDetails {
    if (warnings.isNotEmpty()) return SubmissionDetails()
    val attempt = lastattempt ?: return SubmissionDetails()
    val submission = if (groupSubmission) attempt.teamsubmission else attempt.submission
    val status = when (submission?.status) {
        "new" -> SubmissionStatus.NOT_SUBMITTED
        "draft" -> SubmissionStatus.DRAFT
        "submitted" -> SubmissionStatus.SUBMITTED
        "reopened" -> SubmissionStatus.REOPENED
        else -> SubmissionStatus.UNKNOWN
    }
    return SubmissionDetails(status, attempt.extensionduedate?.toDeadline())
}

private fun Long.toDeadline(): Instant? = takeIf { it > 0 }?.let(Instant::ofEpochSecond)
