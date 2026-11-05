package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class QuizDto(
    val id: Long,
    val course: Long,
    val name: String,
    val timeopen: Long? = null,
    val timeclose: Long? = null,
    val timelimit: Long? = null
)
