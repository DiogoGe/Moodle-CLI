package moodle.domain

import moodle.infrastructure.enums.SubmissionStatus
import java.time.Instant

data class Assignment(
    val id: Long,
    val courseId: Long,
    val name: String,
    val dueDate: Instant?,
    val submissionStatus: SubmissionStatus = SubmissionStatus.UNKNOWN,
)

