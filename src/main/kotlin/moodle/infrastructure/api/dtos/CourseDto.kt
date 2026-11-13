package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class CourseDto(val id: Long, val fullname: String, val summary: String? = null)
