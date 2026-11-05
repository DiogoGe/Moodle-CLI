package moodle.cli

import moodle.application.AssignmentService
import moodle.cli.i18n.Messages
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.enums.SubmissionStatus
import moodle.domain.exceptions.MoodleException
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale

class AssignmentCommands(
    private val service: AssignmentService,
    private val clock: Clock,
    private val output: (String) -> Unit = ::println,
    private val messages: Messages = Messages()
) {
    private val text = TerminalText()
    private val dates = DateTimeFormatter.ofPattern(messages.text("tasks.datePattern"), Locale.ROOT).withZone(clock.zone)

    fun run(args: List<String>): Int {
        val result = when {
            args == listOf("tasks") -> service.getAssignments()
            args.size == 3 && args.take(2) == listOf("tasks", "--course") -> service.getAssignments(courseId(args[2]))
            args.size == 3 && args.take(2) == listOf("upcoming", "--days") -> service.getUpcoming(days(args[2]))
            else -> throw MoodleException.Input("error.tasks.usage")
        }
        show(result)
        return 0
    }

    private fun show(result: AssignmentList) {
        if (result.partial) output(messages.text("tasks.partial"))
        if (result.items.isEmpty()) output(messages.text("tasks.empty"))
        val now = clock.instant()
        result.items.forEach { assignment ->
            output(messages.text("tasks.title", assignment.id, text.label(assignment.name)))
            output(messages.text("tasks.course", text.label(assignment.courseName), assignment.courseId))
            val deadline = assignment.dueDate?.let(dates::format) ?: messages.text("tasks.noDeadline")
            val past = if (assignment.dueDate?.isBefore(now) == true) messages.text("tasks.pastDeadline") else ""
            output(messages.text("tasks.deadline", deadline, past))
            output(messages.text("tasks.status", status(assignment)))
        }
        if (result.items.any { it.submissionStatus == SubmissionStatus.UNKNOWN && it.submissionsEnabled }) {
            output(messages.text("tasks.unknownNotice"))
        }
    }

    private fun status(assignment: Assignment): String {
        if (!assignment.submissionsEnabled) return messages.text("tasks.offline")
        val key = when (assignment.submissionStatus) {
            SubmissionStatus.UNKNOWN -> "tasks.status.unknown"
            SubmissionStatus.NOT_SUBMITTED -> "tasks.status.notSubmitted"
            SubmissionStatus.DRAFT -> "tasks.status.draft"
            SubmissionStatus.SUBMITTED -> "tasks.status.submitted"
            SubmissionStatus.REOPENED -> "tasks.status.reopened"
        }
        return messages.text(key)
    }

    private fun courseId(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw MoodleException.Input("error.input.id")

    private fun days(value: String): Int = value.toIntOrNull()?.takeIf { it > 0 }
        ?: throw MoodleException.Input("error.input.days")
}
