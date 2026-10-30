package moodle.infrastructure.api.interfaces

import kotlinx.serialization.json.JsonObject
import moodle.infrastructure.api.dtos.PublicConfigEnvelopeDto
import moodle.infrastructure.api.dtos.PublicConfigRequestDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

internal interface MoodleApi {
    @POST("lib/ajax/service.php?info=tool_mobile_get_public_config")
    fun getPublicConfig(@Body requests: List<PublicConfigRequestDto>): Call<List<PublicConfigEnvelopeDto>>

    @FormUrlEncoded
    @POST("webservice/rest/server.php")
    fun getSiteInfo(
        @Field("wstoken") token: String,
        @Field("wsfunction") function: String = "core_webservice_get_site_info",
        @Field("moodlewsrestformat") format: String = "json"
    ): Call<JsonObject>
}
