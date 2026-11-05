package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class AssignmentCourseDto(val id: Long, val fullname: String, val assignments: List<AssignmentDto>)
