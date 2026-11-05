package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class AssignmentListDto(val courses: List<AssignmentCourseDto>, val warnings: List<ApiWarningDto> = emptyList())
