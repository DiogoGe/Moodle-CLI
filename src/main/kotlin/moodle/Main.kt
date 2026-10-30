package moodle

import kotlinx.serialization.json.Json
import moodle.application.AccessService
import moodle.application.BrowserLoginService
import moodle.application.MoodleService
import moodle.cli.AccessCommands
import moodle.cli.MoodleCli
import moodle.config.MoodleConfig
import moodle.infrastructure.api.RetrofitMoodleAccess
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitMoodleRepository
import moodle.infrastructure.browser.IsolatedChromeLoginBrowser
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = MoodleConfig(baseUrl = "https://moodle.zhaw.ch/")
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val api = createMoodleApi(config.baseUrl, json)
    val repository = RetrofitMoodleRepository(api)
    val service = MoodleService(repository, Clock.systemDefaultZone())
    val access = AccessService(RetrofitMoodleAccess(api, json), config.baseUrl)
    val commands = AccessCommands(
        access,
        BrowserLoginService(config.baseUrl, Clock.systemUTC()),
        IsolatedChromeLoginBrowser()
    )
    exitProcess(MoodleCli(service, accessCommand = commands::run).run(args))
}
