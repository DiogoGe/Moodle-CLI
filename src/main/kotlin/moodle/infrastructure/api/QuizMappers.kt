package moodle.infrastructure.api

import moodle.domain.entities.Quiz
import moodle.domain.entities.QuizList
import moodle.domain.entities.QuizTiming
import moodle.infrastructure.api.dtos.QuizDto
import moodle.infrastructure.api.dtos.QuizListDto
import java.time.Duration
import java.time.Instant

internal fun QuizListDto.toDomain(): QuizList = QuizList(
    items = quizzes.map { Quiz(it.id, it.course, it.name, it.toTiming()) },
    partial = warnings.isNotEmpty()
)

private fun QuizDto.toTiming(): QuizTiming? {
    // Moodle normally exposes these three fields together. Incomplete data must not imply an unrestricted quiz.
    val opening = timeopen ?: return null
    val closing = timeclose ?: return null
    val limit = timelimit ?: return null
    require(opening >= 0 && closing >= 0 && limit >= 0) { "Invalid quiz timing" }
    return QuizTiming(
        opensAt = opening.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
        closesAt = closing.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
        timeLimit = limit.takeIf { it > 0 }?.let(Duration::ofSeconds)
    )
}
