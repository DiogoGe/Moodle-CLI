package moodle.application

import moodle.domain.entities.Agenda
import moodle.domain.entities.AgendaEvent
import moodle.domain.entities.Quiz
import moodle.domain.enums.AgendaEventKind
import moodle.domain.exceptions.MoodleException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

internal class AgendaService(
    private val assignments: AssignmentService,
    private val quizzes: QuizService,
    private val clock: Clock
) {
    fun getAgenda(days: Int = DEFAULT_DAYS): Agenda {
        if (days !in 1..MAX_DAYS) throw MoodleException.Input("error.agenda.days", MAX_DAYS)
        val now = clock.instant()
        val window = now..now.plus(days.toLong(), ChronoUnit.DAYS)
        val assignmentSource = readSource { assignments.getUpcoming(window) }
        val quizSource = readSource { quizzes.getQuizzes() }
        if (assignmentSource.isFailure && quizSource.isFailure) assignmentSource.getOrThrow()

        val assignmentList = assignmentSource.getOrNull()
        val quizList = quizSource.getOrNull()
        val deadlines = assignmentList?.items.orEmpty().mapNotNull { assignment ->
            assignment.dueDate?.let {
                AgendaEvent(AgendaEventKind.ASSIGNMENT_DUE, assignment.id, assignment.courseId, assignment.name, it)
            }
        }
        val scheduledQuizzes = quizList?.items.orEmpty().flatMap { quizEvents(it, window) }
        val items = (deadlines + scheduledQuizzes).sortedWith(
            compareBy(AgendaEvent::at, AgendaEvent::kind, AgendaEvent::courseId, AgendaEvent::activityId)
        )
        val partial =
            assignmentList?.partial != false || quizList == null || quizList.partial || quizList.items.any { it.timing == null }
        return Agenda(items, partial)
    }

    private fun quizEvents(quiz: Quiz, window: ClosedRange<Instant>): List<AgendaEvent> {
        val timing = quiz.timing ?: return emptyList()
        return buildList {
            timing.opensAt?.takeIf { it in window }?.let {
                add(AgendaEvent(AgendaEventKind.QUIZ_OPENS, quiz.id, quiz.courseId, quiz.name, it))
            }
            timing.closesAt?.takeIf { it in window }?.let {
                add(AgendaEvent(AgendaEventKind.QUIZ_CLOSES, quiz.id, quiz.courseId, quiz.name, it))
            }
        }
    }

    private fun <T> readSource(load: () -> T): Result<T> = try {
        Result.success(load())
    } catch (error: MoodleException.Permission) {
        Result.failure(error)
    }

    companion object {
        const val DEFAULT_DAYS = 7
        const val MAX_DAYS = 366
    }
}
