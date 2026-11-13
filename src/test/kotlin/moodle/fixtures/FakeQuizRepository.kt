package moodle.fixtures

import moodle.domain.AccessToken
import moodle.domain.entities.QuizList
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.QuizRepository

class FakeQuizRepository(var result: QuizList) : QuizRepository {
    var calls = 0
    var requestedCourse: Long? = null
    var receivedToken: AccessToken? = null
    var expired = false
    var denied = false

    override fun getQuizzes(token: AccessToken, courseId: Long?): QuizList {
        calls++
        requestedCourse = courseId
        receivedToken = token
        if (expired) throw MoodleException.Authentication("error.session.expired")
        if (denied) throw MoodleException.Permission("error.api.quizzesUnavailable")
        return result
    }
}
