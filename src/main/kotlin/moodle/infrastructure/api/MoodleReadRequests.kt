package moodle.infrastructure.api

import okhttp3.FormBody
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** At most one explicit retry, only for known read-only REST functions after a transport interruption. */
internal fun <T> Call<T>.executeMoodleRead(): Response<T> {
    try {
        return execute()
    } catch (error: IOException) {
        if (isCanceled || !mayRetryRead() || !error.isRecoverable()) throw error
        return clone().execute()
    }
}

private fun Call<*>.mayRetryRead(): Boolean {
    val request = request()
    if (request.method != "POST") return false
    val body = request.body as? FormBody ?: return false
    val functions = (0 until body.size).filter { body.name(it) == "wsfunction" }.map { body.value(it) }
    return functions.singleOrNull() in READ_FUNCTIONS
}

private fun IOException.isRecoverable(): Boolean =
    !Thread.currentThread().isInterrupted && this !is InterruptedIOException && this !is SSLException &&
        this !is UnknownHostException

private val READ_FUNCTIONS = setOf(
    "core_webservice_get_site_info",
    "core_enrol_get_users_courses",
    "core_course_get_contents",
    "mod_assign_get_assignments",
    "mod_assign_get_submission_status",
    "mod_quiz_get_quizzes_by_courses"
)
