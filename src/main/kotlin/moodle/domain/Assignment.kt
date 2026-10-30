package moodle.domain

import java.time.Instant

data class Assignment(
    val id: Long,
    val courseId: Long,
    val name: String,
    val dueDate: Instant?,
    val submissionStatus: SubmissionStatus = SubmissionStatus.UNKNOWN,
)

enum class SubmissionStatus {
    UNKNOWN,
    NOT_SUBMITTED,
    DRAFT,
    SUBMITTED,
}
