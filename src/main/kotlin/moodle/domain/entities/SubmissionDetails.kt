package moodle.domain.entities

import moodle.domain.enums.SubmissionStatus
import java.time.Instant

data class SubmissionDetails(val status: SubmissionStatus = SubmissionStatus.UNKNOWN, val extensionDueDate: Instant? = null)
