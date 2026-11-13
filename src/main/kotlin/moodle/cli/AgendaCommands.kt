package moodle.cli

import moodle.application.AgendaService
import moodle.cli.i18n.Messages
import moodle.domain.entities.AgendaEvent
import moodle.domain.enums.AgendaEventKind
import moodle.domain.exceptions.MoodleException
import moodle.presentation.PlainText
import java.time.Clock

internal class AgendaCommands(
    private val service: AgendaService,
    clock: Clock,
    private val output: (String) -> Unit = ::println,
    private val messages: Messages = Messages()
) {
    private val dates = TerminalDates(clock, messages)
    private val text = PlainText()

    fun run(args: List<String>): Int {
        val days = when {
            args == listOf("agenda") -> AgendaService.DEFAULT_DAYS
            args.size == 3 && args.take(2) == listOf("agenda", "--days") ->
                args[2].toIntOrNull() ?: throw MoodleException.Input("error.agenda.days", AgendaService.MAX_DAYS)
            else -> throw MoodleException.Input("error.agenda.usage")
        }
        val result = service.getAgenda(days)
        if (result.partial) output(messages.text("agenda.partial"))
        if (result.items.isEmpty()) output(messages.text("agenda.empty"))
        result.items.forEach(::show)
        return 0
    }

    private fun show(event: AgendaEvent) {
        val label = when (event.kind) {
            AgendaEventKind.ASSIGNMENT_DUE -> "agenda.assignmentDue"
            AgendaEventKind.QUIZ_OPENS -> "agenda.quizOpens"
            AgendaEventKind.QUIZ_CLOSES -> "agenda.quizCloses"
        }
        output(messages.text("agenda.row", dates.format(event.at), messages.text(label), text.label(event.name), event.courseId))
    }
}
