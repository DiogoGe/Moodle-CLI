package moodle.application

import moodle.domain.AccessToken
import moodle.domain.entities.Assignment
import moodle.domain.entities.AssignmentList
import moodle.domain.entities.SubmissionDetails
import moodle.domain.enums.SubmissionStatus
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.FakeAssignmentRepository
import moodle.fixtures.authenticatedAccess
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssignmentServiceTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val access = authenticatedAccess()
    private val repository = FakeAssignmentRepository(AssignmentList(emptyList()))
    private val service = AssignmentService(access, repository, clock)

    @Test
    fun `upcoming includes window boundaries and excludes past missing and distant deadlines`() {
        repository.result = AssignmentList(
            listOf(
                assignment(1, now.plusSeconds(86_400)),
                assignment(2, null),
                assignment(3, now.minusSeconds(1)),
                assignment(4, now.plusSeconds(86_401)),
                assignment(5, now)
            )
        )
        assertEquals(listOf(5L, 1L), service.getUpcoming(1).items.map { it.id })
    }

    @Test
    fun `invalid filters are rejected before any repository request`() {
        for (days in listOf(0, -1)) assertFailsWith<MoodleException.Input> { service.getUpcoming(days) }
        for (course in listOf(0L, -1L)) assertFailsWith<MoodleException.Input> { service.getAssignments(course) }
        assertEquals(0, repository.listCalls)
    }

    @Test
    fun `course filtering precedes status requests and partial results remain marked`() {
        repository.result = AssignmentList(listOf(assignment(1, now), assignment(2, now).copy(courseId = 42)), partial = true)
        val result = service.getAssignments(42)
        assertEquals(listOf(2L), result.items.map { it.id })
        assertEquals(listOf(2L), repository.statusCalls)
        assertEquals(42L, repository.requestedCourse)
        assertTrue(result.partial)
    }

    @Test
    fun `individual extensions apply before filtering and never shorten the course deadline`() {
        repository.result = AssignmentList(listOf(assignment(1, now.minusSeconds(1)), assignment(2, now.plusSeconds(60))))
        repository.details[1] = SubmissionDetails(SubmissionStatus.DRAFT, now.plusSeconds(120))
        repository.details[2] = SubmissionDetails(SubmissionStatus.SUBMITTED, now.minusSeconds(120))
        val result = service.getUpcoming(1).items
        assertEquals(listOf(2L, 1L), result.map { it.id })
        assertEquals(now.plusSeconds(120), result.last().dueDate)
        assertEquals(SubmissionStatus.SUBMITTED, result.first().submissionStatus)
        assertEquals(SubmissionStatus.DRAFT, result.last().submissionStatus)
    }

    @Test
    fun `missing status stays unknown and offline assignments can still receive personal extensions`() {
        repository.result = AssignmentList(listOf(assignment(1, now).copy(submissionsEnabled = false), assignment(2, now)))
        repository.details[1] = SubmissionDetails(extensionDueDate = now.plusSeconds(60))
        val items = service.getAssignments().items
        assertTrue(items.all { it.submissionStatus == SubmissionStatus.UNKNOWN })
        assertEquals(now.plusSeconds(60), items.single { it.id == 1L }.dueDate)
        assertEquals(listOf(1L, 2L), repository.statusCalls)
    }

    @Test
    fun `authentication is required and expired credentials clear the session`() {
        access.logout()
        assertFailsWith<MoodleException.Authentication> { service.getAssignments() }
        assertEquals(0, repository.listCalls)
        access.login(AccessToken("a".repeat(32)))
        repository.expired = true
        assertFailsWith<MoodleException.Authentication> { service.getAssignments() }
        repository.expired = false
        assertFailsWith<MoodleException.Authentication> { service.getAssignments() }
        assertEquals(1, repository.listCalls)
    }

    private fun assignment(id: Long, deadline: Instant?): Assignment = Assignment(id, 1, "Task $id", deadline)
}
