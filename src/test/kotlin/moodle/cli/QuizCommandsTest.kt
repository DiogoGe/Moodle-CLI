package moodle.cli

import moodle.application.QuizService
import moodle.cli.i18n.Messages
import moodle.domain.entities.Quiz
import moodle.domain.entities.QuizList
import moodle.domain.entities.QuizTiming
import moodle.domain.exceptions.MoodleException
import moodle.fixtures.FakeQuizRepository
import moodle.fixtures.authenticatedAccess
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizCommandsTest {
    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zurich"))
    private val repository = FakeQuizRepository(QuizList(emptyList()))
    private val service = QuizService(authenticatedAccess(), repository)

    @Test
    fun `German quiz list shows sanitized titles separate times and time limits`() {
        repository.result = QuizList(
            listOf(
                Quiz(100, 42, "<b>Lernkontrolle</b>\u001b", QuizTiming(now, now.plusSeconds(86400), Duration.ofSeconds(95)))
            ),
            partial = true
        )
        val output = mutableListOf<String>()
        QuizCommands(service, clock, output::add, Messages(Locale.GERMAN)).run(listOf("quizzes", "--course", "42"))
        val result = output.joinToString("\n")
        assertTrue(result.contains("[100] Lernkontrolle"))
        assertTrue(result.contains("Kurs-ID: 42"))
        assertTrue(result.contains("Öffnet: 05.11.2026 11:00 +01:00 (Europe/Zurich)"))
        assertTrue(result.contains("Schließt: 06.11.2026 11:00 +01:00 (Europe/Zurich)"))
        assertTrue(result.contains("1 Min 35 s"))
        assertTrue(result.contains("unvollständig"))
        assertFalse(result.contains("<b>") || result.contains('\u001b'))
    }

    @Test
    fun `unknown timing does not claim there are no restrictions`() {
        repository.result = QuizList(listOf(Quiz(1, 1, "Unknown")))
        val output = mutableListOf<String>()
        QuizCommands(service, clock, output::add, Messages(Locale.ENGLISH)).run(listOf("quizzes"))
        assertTrue(output.any { it.contains("not available") })
        assertFalse(output.any { it.contains("no closing-time restriction") || it.contains("no time limit") })
    }

    @Test
    fun `known unrestricted timing and empty lists have distinct readable output`() {
        repository.result = QuizList(listOf(Quiz(1, 1, "Practice", QuizTiming(null, null, null))))
        val output = mutableListOf<String>()
        val commands = QuizCommands(service, clock, output::add, Messages(Locale.ENGLISH))
        commands.run(listOf("quizzes"))
        assertTrue(output.any { it.contains("no closing-time restriction") })
        assertTrue(output.any { it.contains("no time limit") })
        repository.result = QuizList(emptyList())
        output.clear()
        commands.run(listOf("quizzes"))
        assertEquals(listOf("No accessible quizzes found."), output)
    }

    @Test
    fun `invalid quiz arguments fail before calling Moodle`() {
        val commands = QuizCommands(service, clock)
        val cases = listOf(
            listOf("quizzes", "--course", "0"),
            listOf("quizzes", "--course", "abc"),
            listOf("quizzes", "--course"),
            listOf("quizzes", "extra"),
            listOf("quizzes", "--days", "7")
        )
        cases.forEach { assertFailsWith<MoodleException.Input> { commands.run(it) } }
        assertEquals(0, repository.calls)
    }
}
