package moodle.application

import moodle.domain.entities.Quiz
import moodle.domain.entities.QuizList
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.QuizRepository
import java.time.Instant

class QuizService(private val access: AccessService, private val repository: QuizRepository) {
    fun getQuizzes(courseId: Long? = null): QuizList {
        if (courseId != null && courseId <= 0) throw MoodleException.Input("error.input.id")
        return access.withSession { token, _ ->
            val result = repository.getQuizzes(token, courseId)
            val items = result.items
                .filter { courseId == null || it.courseId == courseId }
                .sortedWith(compareBy<Quiz, Instant?>(nullsLast()) { it.timing?.closesAt }.thenBy { it.id })
            result.copy(items = items)
        }
    }
}
