package moodle.cli

import moodle.application.QuizService
import moodle.cli.i18n.Messages
import moodle.domain.entities.Quiz
import moodle.domain.exceptions.MoodleException
import moodle.presentation.PlainText
import java.time.Clock

class QuizCommands(
    private val service: QuizService,
    clock: Clock,
    private val output: (String) -> Unit = ::println,
    private val messages: Messages = Messages()
) {
    private val text = PlainText()
    private val dates = TerminalDates(clock, messages)

    fun run(args: List<String>): Int {
        val result = when {
            args == listOf("quizzes") -> service.getQuizzes()
            args.size == 3 && args.take(2) == listOf("quizzes", "--course") -> service.getQuizzes(courseId(args[2]))
            else -> throw MoodleException.Input("error.quizzes.usage")
        }
        if (result.partial) output(messages.text("quizzes.partial"))
        if (result.items.isEmpty()) output(messages.text("quizzes.empty"))
        result.items.forEach(::show)
        return 0
    }

    private fun show(quiz: Quiz) {
        output(messages.text("quizzes.title", quiz.id, text.label(quiz.name)))
        output(messages.text("quizzes.course", quiz.courseId))
        val timing = quiz.timing
        if (timing == null) {
            output(messages.text("quizzes.timingUnknown"))
            return
        }
        output(messages.text("quizzes.opens", timing.opensAt?.let(dates::format) ?: messages.text("quizzes.noOpening")))
        output(messages.text("quizzes.closes", timing.closesAt?.let(dates::format) ?: messages.text("quizzes.noClosing")))
        val duration = timing.timeLimit?.let { messages.text("quizzes.duration", it.toMinutes(), it.toSecondsPart()) }
            ?: messages.text("quizzes.noLimit")
        output(messages.text("quizzes.limit", duration))
    }

    private fun courseId(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw MoodleException.Input("error.input.id")
}
