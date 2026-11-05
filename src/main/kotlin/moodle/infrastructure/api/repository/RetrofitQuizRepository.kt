package moodle.infrastructure.api.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import moodle.domain.AccessToken
import moodle.domain.entities.QuizList
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.QuizRepository
import moodle.infrastructure.api.dtos.QuizListDto
import moodle.infrastructure.api.interfaces.MoodleApi
import moodle.infrastructure.api.readMoodle
import moodle.infrastructure.api.toDomain

internal class RetrofitQuizRepository(
    private val api: MoodleApi,
    private val json: Json,
    private val availableFunctions: () -> Set<String>
) : QuizRepository {
    override fun getQuizzes(token: AccessToken, courseId: Long?): QuizList {
        if (LIST_FUNCTION !in availableFunctions()) throw MoodleException.Permission("error.api.quizzesUnavailable")
        val fields = courseId?.let { mapOf("courseids[0]" to it.toString()) }.orEmpty()
        return api.getQuizzes(token.value, fields).readMoodle("error.api.quizResponse", "error.api.quizAccess") { body ->
            json.decodeFromJsonElement<QuizListDto>(body).toDomain()
        }
    }

    private companion object {
        const val LIST_FUNCTION = "mod_quiz_get_quizzes_by_courses"
    }
}
