package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class SubmissionDetailsDto(
    val lastattempt: SubmissionAttemptDto? = null,
    val warnings: List<ApiWarningDto> = emptyList()
)
