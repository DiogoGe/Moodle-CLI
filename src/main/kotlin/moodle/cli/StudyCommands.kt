package moodle.cli

/** Dispatches authenticated study commands identically from the launcher and the interactive session. */
class StudyCommands(private val courses: CourseCommands, private val assignments: AssignmentCommands) {
    fun run(args: List<String>): Int = when (args.firstOrNull()) {
        "tasks", "upcoming" -> assignments.run(args)
        else -> courses.run(args)
    }
}
