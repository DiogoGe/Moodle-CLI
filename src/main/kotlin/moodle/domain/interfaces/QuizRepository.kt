package moodle.domain.interfaces

import moodle.domain.AccessToken
import moodle.domain.entities.QuizList

interface QuizRepository {
    fun getQuizzes(token: AccessToken, courseId: Long?): QuizList
}
