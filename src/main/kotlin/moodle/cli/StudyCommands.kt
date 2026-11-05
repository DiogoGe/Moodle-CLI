package moodle.cli

/** Dispatches authenticated study commands identically from the launcher and the interactive session. */
class StudyCommands(
    private val courses: (List<String>) -> Int,
    private val assignments: (List<String>) -> Int,
    private val quizzes: (List<String>) -> Int
) {
    fun run(args: List<String>): Int = when (args.firstOrNull()) {
        "tasks", "assignments", "upcoming" -> assignments(args)
        "quizzes" -> quizzes(args)
        else -> courses(args)
    }
}
