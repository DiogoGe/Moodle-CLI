package moodle.infrastructure.api.interfaces

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import moodle.infrastructure.api.dtos.PublicConfigEnvelopeDto
import moodle.infrastructure.api.dtos.PublicConfigRequestDto
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FieldMap
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

    @FormUrlEncoded
    @POST("webservice/rest/server.php")
    fun getCourses(
        @Field("wstoken") token: String,
        @Field("userid") userId: Long,
        @Field("wsfunction") function: String = "core_enrol_get_users_courses",
        @Field("moodlewsrestformat") format: String = "json"
    ): Call<JsonElement>

    @FormUrlEncoded
    @POST("webservice/rest/server.php")
    fun getCourseContents(
        @Field("wstoken") token: String,
        @Field("courseid") courseId: Long,
        @Field("wsfunction") function: String = "core_course_get_contents",
        @Field("moodlewsrestformat") format: String = "json"
    ): Call<JsonElement>

    @FormUrlEncoded
    @POST("webservice/rest/server.php")
    fun getAssignments(
        @Field("wstoken") token: String,
        @FieldMap courseIds: Map<String, String>,
        @Field("wsfunction") function: String = "mod_assign_get_assignments",
        @Field("moodlewsrestformat") format: String = "json"
    ): Call<JsonElement>

    @FormUrlEncoded
    @POST("webservice/rest/server.php")
    fun getSubmissionStatus(
        @Field("wstoken") token: String,
        @Field("assignid") assignmentId: Long,
        @Field("wsfunction") function: String = "mod_assign_get_submission_status",
        @Field("moodlewsrestformat") format: String = "json"
    ): Call<JsonElement>

    @FormUrlEncoded
    @POST("webservice/rest/server.php")
    fun getQuizzes(
        @Field("wstoken") token: String,
        @FieldMap courseIds: Map<String, String>,
        @Field("wsfunction") function: String = "mod_quiz_get_quizzes_by_courses",
        @Field("moodlewsrestformat") format: String = "json"
    ): Call<JsonElement>
}
