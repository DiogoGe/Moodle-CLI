package moodle.infrastructure.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import moodle.infrastructure.api.dtos.PublicConfigDto
import moodle.infrastructure.api.dtos.PublicConfigRequestDto
import moodle.infrastructure.api.dtos.SiteInfoDto
import moodle.infrastructure.api.interfaces.MoodleApi
import java.io.IOException

internal class RetrofitMoodleAccess(private val api: MoodleApi, private val json: Json) : MoodleAccess {
    override fun getPublicStatus(): PublicStatus = safely {
        loadPublicConfig().toDomain()
    }

    override fun getSiteInfo(token: AccessToken): SiteInfo = safely {
        val body = loadSiteInfo(token)
        checkApiError(body)
        json.decodeFromJsonElement(SiteInfoDto.serializer(), body).toDomain()
    }

    private fun loadPublicConfig(): PublicConfigDto {
        val response = api.getPublicConfig(listOf(PublicConfigRequestDto())).execute()
        if (!response.isSuccessful) {
            throw MoodleException.Connection("Moodle public API returned HTTP ${response.code()}.")
        }
        val envelope = response.body()?.singleOrNull()
            ?: throw MoodleException.Connection("Moodle returned an unexpected public API response.")
        if (envelope.error) throw MoodleException.Connection("Moodle public configuration is unavailable.")
        return envelope.data ?: throw MoodleException.Connection("Moodle public configuration is unavailable.")
    }

    private fun loadSiteInfo(token: AccessToken): JsonObject {
        val response = api.getSiteInfo(token.value).execute()
        if (!response.isSuccessful) {
            throw MoodleException.Connection("Moodle authenticated API returned HTTP ${response.code()}.")
        }
        return response.body() ?: throw MoodleException.Connection("Moodle returned an empty API response.")
    }

    private fun checkApiError(body: JsonObject) {
        val code = body["errorcode"]?.jsonPrimitive?.contentOrNull
        if (code == null && "exception" !in body) return
        when (code) {
            "invalidtoken", "accessexception", "requireloginerror" -> throw MoodleException.Authentication(
                "Moodle rejected the token or its service permissions. Sign in again."
            )
            else -> throw MoodleException.Connection("Moodle could not complete the site information request.")
        }
    }

    private fun <T> safely(action: () -> T): T {
        try {
            return action()
        } catch (_: IOException) {
            throw MoodleException.Connection("Cannot reach Moodle. Check the connection and try again.")
        } catch (_: SerializationException) {
            throw MoodleException.Connection("Moodle returned an unsupported API response.")
        } catch (_: IllegalArgumentException) {
            throw MoodleException.Connection("Moodle returned an unsupported API response.")
        }
    }
}
