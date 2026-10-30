package moodle.cli

import moodle.application.MoodleService
import moodle.domain.exceptions.MoodleException

class MoodleCli(
    private val service: MoodleService,
    private val output: (String) -> Unit = ::println,
    private val errorOutput: (String) -> Unit = System.err::println,
    private val accessCommand: (String) -> Int = { throw MoodleException.FeatureUnavailable("Access commands") }
) {
    fun run(args: Array<String>): Int = try {
        dispatch(args)
    } catch (error: MoodleException) {
        errorOutput(error.message ?: "Moodle operation failed.")
        1
    }

    private fun dispatch(args: Array<String>): Int = when {
        args.isEmpty() || args.contentEquals(arrayOf("--help")) -> {
            output(HELP)
            0
        }
        args.size == 1 && args[0] in setOf("login", "doctor") -> accessCommand(args[0])
        args.contentEquals(arrayOf("login", "--manual")) -> accessCommand("login-manual")
        args.contentEquals(arrayOf("courses")) -> showCourses()
        args.first() in PLANNED_COMMANDS -> usageError("${args.first()} is not implemented yet. See --help.")
        else -> usageError("Unknown command or unsupported arguments. See --help.")
    }

    private fun showCourses(): Int {
        val courses = service.getCourses()
        if (courses.isEmpty()) output("No courses found.")
        courses.forEach { output("${it.id}  ${it.name}") }
        return 0
    }

    private fun usageError(message: String): Int {
        errorOutput(message)
        return 2
    }

    private companion object {
        val PLANNED_COMMANDS = setOf("setup", "tasks", "upcoming", "logout")
        val HELP =
            """
            Moodle CLI

            Usage: moodle <command>

            --help                 Show help
            doctor                 Check public API configuration
            login                  Automatic callback in a separate private Chrome window
            login --manual         Copy the app callback from your normal browser
            courses                List courses (API integration pending)

            Planned: setup <url>, tasks [--course <id>], upcoming --days <n>
            After login: doctor, logout, exit

            Run login from the installed launcher in a terminal. No credentials are saved.
            """.trimIndent()
    }
}
