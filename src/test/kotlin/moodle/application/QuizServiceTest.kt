package moodle.application

import moodle.domain.AccessToken
import moodle.domain.entities.Quiz
import moodle.domain.entities.QuizList
import moodle.domain.entities.QuizTiming
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.FakeQuizRepository
import moodle.fixtures.authenticatedAccess
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuizServiceTest {
    private val access = authenticatedAccess()
    private val repository = FakeQuizRepository(QuizList(emptyList()))
    private val service = QuizService(access, repository)

    @Test
    fun `quiz course filtering preserves partial results and forwards the authenticated user token`() {
        repository.result = QuizList(listOf(Quiz(1, 42, "Quiz A"), Quiz(2, 43, "Quiz B")), partial = true)
        val result = service.getQuizzes(42)
        assertEquals(listOf(1L), result.items.map { it.id })
        assertEquals(42L, repository.requestedCourse)
        assertEquals(AccessToken("a".repeat(32)).value, repository.receivedToken?.value)
        assertTrue(result.partial)
    }

    @Test
    fun `quizzes sort by closing time with unrestricted and unknown schedules last`() {
        val first = Instant.parse("2026-11-06T10:00:00Z")
        repository.result = QuizList(
            listOf(
                Quiz(3, 1, "No close", QuizTiming(null, null, null)),
                Quiz(2, 1, "Later", QuizTiming(null, first.plusSeconds(60), null)),
                Quiz(4, 1, "Unknown"),
                Quiz(1, 1, "First", QuizTiming(null, first, null))
            )
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), service.getQuizzes().items.map { it.id })
    }

    @Test
    fun `invalid course IDs and missing authentication fail before accessing Moodle`() {
        assertFailsWith<MoodleException.Input> { service.getQuizzes(0) }
        assertFailsWith<MoodleException.Input> { service.getQuizzes(-1) }
        access.logout()
        assertFailsWith<MoodleException.Authentication> { service.getQuizzes() }
        assertEquals(0, repository.calls)
    }

    @Test
    fun `expired quiz credentials remove the session rather than returning an empty quiz list`() {
        repository.expired = true
        assertFailsWith<MoodleException.Authentication> { service.getQuizzes() }
        repository.expired = false
        assertFailsWith<MoodleException.Authentication> { service.getQuizzes() }
        assertEquals(1, repository.calls)
    }
}
