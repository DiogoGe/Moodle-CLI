package moodle.cli

import moodle.application.MoodleService
import moodle.domain.MoodleException

class MoodleCli(
    private val service: MoodleService,
    private val output: (String) -> Unit = ::println,
    private val errorOutput: (String) -> Unit = System.err::println,
) {
    fun run(args: Array<String>): Int = try {
        when {
            args.isEmpty() || args.contentEquals(arrayOf("--help")) -> {
                output(HELP)
                0
            }
            args.contentEquals(arrayOf("courses")) -> {
                val courses = service.getCourses()
                if (courses.isEmpty()) output("No courses found.")
                courses.forEach { output("${it.id}  ${it.name}") }
                0
            }
            args.first() in PLANNED_COMMANDS -> {
                errorOutput("${args.first()} is not implemented yet. See --help.")
                2
            }
            else -> {
                errorOutput("Unknown command or unsupported arguments. See --help.")
                2
            }
        }
    } catch (error: MoodleException) {
        errorOutput(error.message ?: "Moodle operation failed.")
        1
    }

    private companion object {
        val PLANNED_COMMANDS = setOf("setup", "login", "doctor", "tasks", "upcoming", "logout")
        val HELP = """
            Moodle CLI — application skeleton

            Usage: moodle <command>

            --help                 Show help
            courses                List courses (API integration pending)

            Planned: setup <url>, login, doctor, tasks [--course <id>],
                     upcoming --days <n>, logout

            No authentication or network requests are implemented yet.
        """.trimIndent()
    }
}
