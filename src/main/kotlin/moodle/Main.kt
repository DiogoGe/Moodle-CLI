package moodle

import kotlinx.serialization.json.Json
import moodle.application.MoodleService
import moodle.cli.MoodleCli
import moodle.config.MoodleConfig
import moodle.infrastructure.MoodleApi
import moodle.infrastructure.RetrofitMoodleRepository
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Placeholder only: load the user's configuration in the setup milestone.
    // Creating the client does not send a request.
    val config = MoodleConfig(baseUrl = "https://moodle.example.edu/")
    val json = Json { ignoreUnknownKeys = true }
    val api =
        Retrofit
            .Builder()
            .baseUrl(config.baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MoodleApi::class.java)
    val repository = RetrofitMoodleRepository(api)
    val service = MoodleService(repository, Clock.systemDefaultZone())
    exitProcess(MoodleCli(service).run(args))
}
