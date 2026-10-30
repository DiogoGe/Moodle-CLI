package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class CourseContentDto(
    val type: String,
    val filename: String = "",
    val fileurl: String? = null,
    val filesize: Long = 0,
    val mimetype: String? = null,
    val content: String? = null
)
