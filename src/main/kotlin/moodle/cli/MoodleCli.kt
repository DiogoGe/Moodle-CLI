package moodle.cli

import moodle.domain.exceptions.MoodleException

class MoodleCli(
    private val output: (String) -> Unit = ::println,
    private val errorOutput: (String) -> Unit = System.err::println,
    private val accessCommand: (String) -> Int = { throw MoodleException.FeatureUnavailable("Access commands") },
    private val courseCommand: (List<String>) -> Int = { throw MoodleException.FeatureUnavailable("Course commands") }
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
        args.size == 1 && args[0] in setOf("login", "health") -> accessCommand(args[0])
        args.contentEquals(arrayOf("login", "--manual")) -> accessCommand("login-manual")
        args.first() in setOf("courses", "course", "read", "download") -> courseCommand(args.toList())
        args.first() in PLANNED_COMMANDS -> usageError("${args.first()} is not implemented yet. See --help.")
        else -> usageError("Unknown command or unsupported arguments. See --help.")
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
            health                 Check public API configuration
            login                  Automatic callback in a separate private Chrome window
            login --manual         Copy the app callback from your normal browser
            courses                List enrolled courses (requires login)
            course <id>            Show sections, text, and downloadable files
            read <course> <module> Read an activity or text page
            download <course> <module> <file-number> [directory]
                                   Save a file without overwriting existing files

            Planned: setup <url>, tasks [--course <id>], upcoming --days <n>
            After login: courses, course, read, download, health, logout, exit

            Run login from the installed launcher in a terminal. No credentials are saved.
            """.trimIndent()
    }
}
