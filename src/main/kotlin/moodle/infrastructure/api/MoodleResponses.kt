package moodle.infrastructure.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import moodle.domain.exceptions.MoodleException
import retrofit2.Call
import java.io.IOException
import java.time.DateTimeException

/** Decode a REST response without exposing response bodies, request credentials, or transport exception messages. */
internal fun <T> Call<JsonElement>.readMoodle(responseKey: String, decode: (JsonElement) -> T): T {
    try {
        val response = execute()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            when (response.code()) {
                401 -> throw MoodleException.Authentication("error.session.expired")
                403 -> throw MoodleException.Permission("error.api.assignmentAccess")
                else -> throw MoodleException.Connection("error.api.http", response.code())
            }
        }
        val body = response.body() ?: throw MoodleException.Connection("error.api.empty")
        if (body is JsonObject) checkMoodleError(body, responseKey)
        return decode(body)
    } catch (_: IOException) {
        throw MoodleException.Connection("error.api.connection")
    } catch (_: SerializationException) {
        throw MoodleException.Connection(responseKey)
    } catch (_: IllegalArgumentException) {
        throw MoodleException.Connection(responseKey)
    } catch (_: DateTimeException) {
        throw MoodleException.Connection(responseKey)
    }
}

private fun checkMoodleError(body: JsonObject, responseKey: String) {
    if ("exception" !in body && "errorcode" !in body) return
    val code = (body["errorcode"] as? JsonPrimitive)?.contentOrNull
    when (code) {
        "invalidtoken", "requireloginerror" -> throw MoodleException.Authentication("error.session.expired")
        "accessexception", "nopermission", "nopermissions", "required_capability_exception" ->
            throw MoodleException.Permission("error.api.assignmentAccess")
        else -> throw MoodleException.Connection(responseKey)
    }
}
