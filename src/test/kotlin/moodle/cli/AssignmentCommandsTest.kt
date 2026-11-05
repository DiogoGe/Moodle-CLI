package moodle.cli

import moodle.application.AssignmentService
import moodle.cli.i18n.Messages
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.SubmissionDetails
import moodle.domain.enums.SubmissionStatus
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.FakeAssignmentRepository
import moodle.fixtures.assignmentAccess
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssignmentCommandsTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zurich"))
    private val repository = FakeAssignmentRepository(AssignmentList(emptyList()))
    private val service = AssignmentService(assignmentAccess(), repository, clock)

    @Test
    fun `German tasks show course local time unknown status and neutral past deadline markers`() {
        repository.result = AssignmentList(
            listOf(
                Assignment(1, 42, "<b>Report</b>\u001b", now.minusSeconds(60), courseName = "Programming &amp; Tools"),
                Assignment(2, 42, "No deadline", null, courseName = "Programming")
            ),
            partial = true
        )
        repository.details[1] = SubmissionDetails(SubmissionStatus.SUBMITTED)
        val output = mutableListOf<String>()
        AssignmentCommands(service, clock, output::add, Messages(Locale.GERMAN)).run(listOf("tasks"))
        val result = output.joinToString("\n")
        assertTrue(result.contains("Programming & Tools (42)"))
        assertTrue(result.contains("05.11.2026 10:59 +01:00 (Europe/Zurich)"), result)
        assertTrue(result.contains("Frist verstrichen"))
        assertTrue(result.contains("eingereicht"))
        assertTrue(result.contains("Keine Abgabefrist"))
        assertTrue(result.contains("unbekannt"))
        assertTrue(result.contains("unvollständig"))
        assertFalse(result.contains("<b>") || result.contains('\u001b'))
    }

    @Test
    fun `English upcoming is sorted and omits missing and past deadlines`() {
        repository.result = AssignmentList(
            listOf(
                Assignment(3, 1, "Later", now.plusSeconds(3600)),
                Assignment(2, 1, "Past", now.minusSeconds(1)),
                Assignment(1, 1, "First", now),
                Assignment(4, 1, "No deadline", null)
            )
        )
        val output = mutableListOf<String>()
        AssignmentCommands(service, clock, output::add, Messages(Locale.ENGLISH)).run(listOf("upcoming", "--days", "7"))
        val titles = output.filter { it.startsWith('[') }
        assertEquals(listOf("[1] First", "[3] Later"), titles)
    }

    @Test
    fun `summer deadlines display the daylight saving offset explicitly`() {
        repository.result = AssignmentList(listOf(Assignment(1, 1, "Summer", Instant.parse("2026-06-01T10:00:00Z"))))
        val output = mutableListOf<String>()
        AssignmentCommands(service, clock, output::add, Messages(Locale.ENGLISH)).run(listOf("tasks"))
        assertTrue(output.any { "2026-06-01 12:00 +02:00 (Europe/Zurich)" in it })
    }

    @Test
    fun `invalid arguments do not access Moodle and empty lists have a clear message`() {
        val commands = AssignmentCommands(service, clock, messages = Messages(Locale.ENGLISH))
        val invalid = listOf(
            listOf("tasks", "--course", "0"),
            listOf("tasks", "--course", "abc"),
            listOf("upcoming"),
            listOf("upcoming", "--days", "0"),
            listOf("upcoming", "--days", "2147483648"),
            listOf("tasks", "--course"),
            listOf("tasks", "extra")
        )
        invalid.forEach { assertFailsWith<MoodleException.Input> { commands.run(it) } }
        assertEquals(0, repository.listCalls)
        val output = mutableListOf<String>()
        AssignmentCommands(service, clock, output::add, Messages(Locale.ENGLISH)).run(listOf("tasks"))
        assertEquals(listOf("No accessible assignments found."), output)
    }
}
