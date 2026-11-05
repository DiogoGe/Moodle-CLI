package moodle.infrastructure.api.dtos

import kotlinx.serialization.Serializable

@Serializable
internal data class QuizListDto(val quizzes: List<QuizDto>, val warnings: List<ApiWarningDto> = emptyList())
