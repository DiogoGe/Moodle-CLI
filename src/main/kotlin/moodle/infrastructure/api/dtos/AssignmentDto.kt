package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class AssignmentDto(
    val id: Long,
    val name: String,
    val duedate: Long,
    val teamsubmission: Int = 0,
    val nosubmissions: Int = 0
)
