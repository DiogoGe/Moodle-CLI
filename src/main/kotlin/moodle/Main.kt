package moodle

import moodle.application.BrowserLoginService
import moodle.cli.AccessCommands
import moodle.cli.AgendaCommands
import moodle.cli.AssignmentCommands
import moodle.cli.CliOptions
import moodle.cli.CourseCommands
import moodle.cli.MoodleCli
import moodle.cli.QuizCommands
import moodle.cli.StudyCommands
import moodle.cli.i18n.Messages
import moodle.config.MoodleConfig
import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.browser.IsolatedChromeLoginBrowser
import moodle.mcp.runMoodleMcp
import java.time.Clock
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = try {
        CliOptions.parse(args)
    } catch (error: MoodleException.Input) {
        System.err.println(Messages().error(error))
        exitProcess(2)
    }
    if (options.arguments == listOf("mcp")) {
        exitProcess(runMoodleMcp(createMoodleApplication(options.useKeychain), Messages(options.locale)))
    }
    val cli = createCli(Messages(options.locale), options.useKeychain)
    exitProcess(cli.run(options.arguments.toTypedArray()))
}

private fun createCli(messages: Messages, useKeychain: Boolean): MoodleCli {
    val config = MoodleConfig()
    val application = createMoodleApplication(useKeychain, config)
    val access = application.access
    val clock = Clock.systemDefaultZone()
    val study = StudyCommands(
        CourseCommands(application.courses, messages = messages, config = config)::run,
        AssignmentCommands(application.assignments, clock, messages = messages)::run,
        QuizCommands(application.quizzes, clock, messages = messages)::run,
        AgendaCommands(application.agenda, clock, messages = messages)::run
    )
    val commands = AccessCommands(
        access,
        BrowserLoginService(config.baseUrl, Clock.systemUTC()),
        IsolatedChromeLoginBrowser(onCleanupFailure = { System.err.println(messages.text("browser.shutdown")) }),
        studyCommand = study::run,
        messages = messages,
        persistent = useKeychain
    )
    return MoodleCli(accessCommand = commands::run, studyCommand = study::run, messages = messages)
}
