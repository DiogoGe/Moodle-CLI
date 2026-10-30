package moodle.infrastructure.api

import kotlinx.serialization.json.Json
import moodle.infrastructure.api.interfaces.MoodleApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration

internal fun createMoodleApi(baseUrl: String, json: Json): MoodleApi {
    val http = createMoodleHttpClient()
    return Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MoodleApi::class.java)
}

internal fun createMoodleHttpClient(): OkHttpClient = OkHttpClient
    .Builder()
    .callTimeout(Duration.ofSeconds(30))
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .build()
