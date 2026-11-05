package moodle

import kotlinx.serialization.json.Json
import moodle.application.AccessService
import moodle.application.AssignmentService
import moodle.application.BrowserLoginService
import moodle.application.CourseService
import moodle.cli.AccessCommands
import moodle.cli.AssignmentCommands
import moodle.cli.CliOptions
import moodle.cli.CourseCommands
import moodle.cli.MoodleCli
import moodle.cli.StudyCommands
import moodle.cli.i18n.Messages
import moodle.config.MoodleConfig
import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.api.RetrofitMoodleAccess
import moodle.infrastructure.api.createMoodleApi
import moodle.infrastructure.api.repository.RetrofitAssignmentRepository
import moodle.infrastructure.api.repository.RetrofitCourseRepository
import moodle.infrastructure.browser.IsolatedChromeLoginBrowser
import moodle.infrastructure.files.LocalCourseFiles
import moodle.infrastructure.files.MoodleFileClient
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = try {
        CliOptions.parse(args)
    } catch (error: MoodleException.Input) {
        System.err.println(Messages().error(error))
        exitProcess(2)
    }
    val cli = createCli(Messages(options.locale))
    exitProcess(cli.run(options.arguments.toTypedArray()))
}

private fun createCli(messages: Messages): MoodleCli {
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
    val clock = Clock.systemDefaultZone()
    val assignmentRepository = RetrofitAssignmentRepository(api, json, access::getAvailableFunctions)
    val assignments = AssignmentService(access, assignmentRepository, clock)
    val study = StudyCommands(
        CourseCommands(courseService, messages = messages),
        AssignmentCommands(assignments, clock, messages = messages)
    )
    val commands = AccessCommands(
        access,
        BrowserLoginService(config.baseUrl, Clock.systemUTC()),
        IsolatedChromeLoginBrowser(onCleanupFailure = { System.err.println(messages.text("browser.shutdown")) }),
        studyCommand = study::run,
        messages = messages
    )
    return MoodleCli(accessCommand = commands::run, studyCommand = study::run, messages = messages)
}
