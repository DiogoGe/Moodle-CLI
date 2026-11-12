package moodle.cli

import moodle.cli.i18n.Messages
import moodle.domain.exceptions.MoodleException

class MoodleCli(
    private val output: (String) -> Unit = ::println,
    private val errorOutput: (String) -> Unit = System.err::println,
    private val accessCommand: (String) -> Int = { throw MoodleException.FeatureUnavailable("access") },
    private val studyCommand: (List<String>) -> Int = { throw MoodleException.FeatureUnavailable("courses") },
    private val messages: Messages = Messages()
) {
    fun run(args: Array<String>): Int = try {
        dispatch(args)
    } catch (error: MoodleException) {
        errorOutput(messages.error(error))
        1
    }

    private fun dispatch(args: Array<String>): Int = when {
        args.isEmpty() || args.contentEquals(arrayOf("--help")) -> {
            output(messages.text("cli.help"))
            0
        }
        args.size == 1 && args[0] in setOf("login", "health", "logout") -> accessCommand(args[0])
        args.contentEquals(arrayOf("login", "--manual")) -> accessCommand("login-manual")
        args.first() in STUDY_COMMANDS -> studyCommand(args.toList())
        args.first() in PLANNED_COMMANDS -> usageError(messages.text("error.cli.planned", args.first()))
        else -> usageError(messages.text("error.cli.usage"))
    }

    private fun usageError(message: String): Int {
        errorOutput(message)
        return 2
    }

    private companion object {
        val STUDY_COMMANDS =
            setOf("courses", "course", "search", "read", "download", "tasks", "assignments", "upcoming", "quizzes")
        val PLANNED_COMMANDS = setOf("setup")
    }
}
