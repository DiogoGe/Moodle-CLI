package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class SubmissionAttemptDto(
    val submission: SubmissionDto? = null,
    val teamsubmission: SubmissionDto? = null,
    val extensionduedate: Long? = null
)
