package moodle.domain.entities

data class Quiz(
    val id: Long,
    val courseId: Long,
    val name: String,
    val timing: QuizTiming? = null
)
