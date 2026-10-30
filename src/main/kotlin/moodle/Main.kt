package moodle

import kotlinx.serialization.json.Json
import moodle.application.AccessService
import moodle.application.BrowserLoginService
import moodle.application.CourseService
import moodle.cli.AccessCommands
import moodle.cli.CourseCommands
import moodle.cli.MoodleCli
import moodle.config.MoodleConfig
import moodle.infrastructure.api.RetrofitMoodleAccess
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitCourseRepository
import moodle.infrastructure.browser.IsolatedChromeLoginBrowser
import moodle.infrastructure.files.LocalCourseFiles
import moodle.infrastructure.files.MoodleFileClient
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = MoodleConfig(baseUrl = "https://moodle.zhaw.ch/")
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val api = createMoodleApi(config.baseUrl, json)
    val access = AccessService(RetrofitMoodleAccess(api, json), config.baseUrl)
    val courseService = CourseService(
        access,
        RetrofitCourseRepository(api, json),
        LocalCourseFiles(MoodleFileClient(config.baseUrl))
    )
    val courses = CourseCommands(courseService)
    val commands = AccessCommands(
        access,
        BrowserLoginService(config.baseUrl, Clock.systemUTC()),
        IsolatedChromeLoginBrowser(),
        courseCommand = courses::run
    )
    exitProcess(MoodleCli(accessCommand = commands::run, courseCommand = courses::run).run(args))
}
