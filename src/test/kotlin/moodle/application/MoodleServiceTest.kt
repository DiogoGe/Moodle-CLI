package moodle.application

import moodle.domain.entities.Assignment
import moodle.domain.interfaces.MoodleRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoodleServiceTest {
    private val now = Instant.parse("2026-10-30T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `upcoming excludes past missing and distant deadlines and sorts results`() {
        val assignments = listOf(
            assignment(1, now.plusSeconds(86_400)),
            assignment(2, null),
            assignment(3, now.minusSeconds(1)),
            assignment(4, now.plusSeconds(86_401)),
            assignment(5, now)
        )
        val service = MoodleService(FakeRepository(assignments), clock)

        assertEquals(listOf(5L, 1L), service.getUpcoming(1).map { it.id })
    }

    @Test
    fun `non-positive day counts are rejected before accessing repository`() {
        val service = MoodleService(FakeRepository(emptyList()), clock)

        assertFailsWith<IllegalArgumentException> { service.getUpcoming(0) }
        assertFailsWith<IllegalArgumentException> { service.getUpcoming(-1) }
    }

    @Test
    fun `course filter returns only assignments belonging to selected course`() {
        val assignments = listOf(
            assignment(1, now),
            assignment(2, now).copy(courseId = 42)
        )
        val service = MoodleService(FakeRepository(assignments), clock)

        assertEquals(listOf(2L), service.getAssignments(42).map { it.id })
        assertEquals(assignments, service.getAssignments())
    }

    private fun assignment(id: Long, dueDate: Instant?): Assignment =
        Assignment(id = id, courseId = 1, name = "Task $id", dueDate = dueDate)

    private class FakeRepository(private val assignments: List<Assignment>) : MoodleRepository {
        override fun getAssignments(): List<Assignment> = assignments
    }
}
