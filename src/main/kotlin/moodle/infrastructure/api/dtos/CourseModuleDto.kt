package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class CourseModuleDto(
    val id: Long,
    val name: String,
    val modname: String,
    val description: String = "",
    val uservisible: Boolean = true,
    val contents: List<CourseContentDto> = emptyList(),
    val noviewlink: Boolean = false
)
