package moodle.application

import moodle.cli.AgendaCommands
import moodle.cli.i18n.Messages
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.Quiz
import moodle.domain.entities.QuizList
import moodle.domain.entities.QuizTiming
import moodle.domain.entities.SubmissionDetails
import moodle.domain.enums.AgendaEventKind
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.FakeAssignmentRepository
import moodle.fixtures.FakeQuizRepository
import moodle.fixtures.authenticatedAccess
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgendaServiceTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zurich"))
    private val access = authenticatedAccess()
    private val assignments = FakeAssignmentRepository(AssignmentList(listOf(Assignment(7, 1, "<b>Exercise</b>", now))))
    private val quizzes = FakeQuizRepository(QuizList(emptyList()))
    private val service = AgendaService(AssignmentService(access, assignments, clock), QuizService(access, quizzes), clock)

    @Test
    fun `agenda orders event types and respects inclusive boundaries and personal extensions`() {
        val end = now.plus(1, ChronoUnit.DAYS)
        assignments.result = AssignmentList(
            listOf(
                Assignment(8, 1, "Later", end.plusSeconds(1)),
                Assignment(7, 1, "Extended", now.minusSeconds(60))
            )
        )
        assignments.details[7] = SubmissionDetails(extensionDueDate = now.plusSeconds(60))
        quizzes.result = QuizList(
            listOf(
                Quiz(7, 2, "Scheduled quiz", QuizTiming(now, end, null)),
                Quiz(9, 2, "Outside window", QuizTiming(now.minusSeconds(1), end.plusSeconds(1), null)),
                Quiz(10, 2, "Unrestricted", QuizTiming(null, null, null))
            )
        )
        val agenda = service.getAgenda(1)
        assertEquals(listOf(now, now.plusSeconds(60), end), agenda.items.map { it.at })
        assertEquals(
            listOf(AgendaEventKind.QUIZ_OPENS, AgendaEventKind.ASSIGNMENT_DUE, AgendaEventKind.QUIZ_CLOSES),
            agenda.items.map { it.kind }
        )
        assertEquals(listOf(7L), assignments.statusCalls)
        assertFalse(agenda.partial)
    }

    @Test
    fun `denied quiz access is partial but expired login is reported as an error`() {
        quizzes.denied = true
        val agenda = service.getAgenda()
        assertEquals(AgendaEventKind.ASSIGNMENT_DUE, agenda.items.single().kind)
        assertTrue(agenda.partial)

        quizzes.expired = true
        assertFailsWith<MoodleException.Authentication> { service.getAgenda() }
    }

    @Test
    fun `CLI shows localized dates and marks missing quiz timing as incomplete`() {
        quizzes.result = QuizList(listOf(Quiz(7, 2, "Unavailable", null)))
        val output = mutableListOf<String>()
        val cli = AgendaCommands(service, clock, output::add, Messages(Locale.GERMAN))
        assertFailsWith<MoodleException.Input> { cli.run(listOf("agenda", "--days", "367")) }
        assertEquals(0, assignments.listCalls)
        assertEquals(0, quizzes.calls)
        assertEquals(0, cli.run(listOf("agenda")))
        assertTrue(output.first().contains("unvollständig"))
        assertTrue(output.last().contains("13.11.2026 13:00 +01:00 (Europe/Zurich) | Abgabefrist | Exercise"))
    }
}
