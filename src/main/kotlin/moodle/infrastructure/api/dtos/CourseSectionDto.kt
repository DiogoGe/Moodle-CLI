package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class CourseSectionDto(
    val id: Long,
    val name: String,
    val summary: String = "",
    val uservisible: Boolean = true,
    val modules: List<CourseModuleDto> = emptyList()
)
