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
            throw MoodleException.Connection("error.api.publicHttp", response.code())
        }
        val envelope = response.body()?.singleOrNull()
            ?: throw MoodleException.Connection("error.api.publicResponse")
        if (envelope.error) throw MoodleException.Connection("error.api.publicUnavailable")
        return envelope.data ?: throw MoodleException.Connection("error.api.publicUnavailable")
    }

    private fun loadSiteInfo(token: AccessToken): JsonObject {
        val response = api.getSiteInfo(token.value).execute()
        if (!response.isSuccessful) {
            throw MoodleException.Connection("error.api.authHttp", response.code())
        }
        return response.body() ?: throw MoodleException.Connection("error.api.empty")
    }

    private fun checkApiError(body: JsonObject) {
        val code = body["errorcode"]?.jsonPrimitive?.contentOrNull
        if (code == null && "exception" !in body) return
        when (code) {
            "invalidtoken", "accessexception", "requireloginerror" -> throw MoodleException.Authentication(
                "error.api.token"
            )
            else -> throw MoodleException.Connection("error.api.siteInfo")
        }
    }

    private fun <T> safely(action: () -> T): T {
        try {
            return action()
        } catch (_: IOException) {
            throw MoodleException.Connection("error.api.connection")
        } catch (_: SerializationException) {
            throw MoodleException.Connection("error.api.response")
        } catch (_: IllegalArgumentException) {
            throw MoodleException.Connection("error.api.response")
        }
    }
}
