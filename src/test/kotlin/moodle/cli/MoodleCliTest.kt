package moodle.cli

import moodle.cli.i18n.Messages
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoodleCliTest {
    @Test
    fun `help succeeds without contacting the repository`() {
        val output = mutableListOf<String>()
        val cli = MoodleCli(output = output::add, messages = Messages(Locale.ENGLISH))

        assertEquals(0, cli.run(arrayOf("--help")))
        assertTrue(output.single().contains("Usage: moodle"))
    }

    @Test
    fun `unavailable integration is reported as a failure instead of an empty course list`() {
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val cli = MoodleCli(
            output = output::add,
            errorOutput = errors::add,
            messages = Messages(Locale.ENGLISH)
        )

        assertEquals(1, cli.run(arrayOf("courses")))
        assertTrue(output.isEmpty())
        assertTrue(errors.single().contains("not implemented"))
    }

    @Test
    fun `access commands dispatch explicitly`() {
        val calls = mutableListOf<String>()
        val cli = MoodleCli(
            accessCommand = {
                calls.add(it)
                0
            }
        )
        assertEquals(0, cli.run(arrayOf("login")))
        assertEquals(0, cli.run(arrayOf("login", "--manual")))
        assertEquals(0, cli.run(arrayOf("health")))
        assertEquals(0, cli.run(arrayOf("logout")))
        assertEquals(listOf("login", "login-manual", "health", "logout"), calls)
    }

    @Test
    fun `study commands dispatch with their arguments`() {
        val calls = mutableListOf<List<String>>()
        val cli = MoodleCli(studyCommand = {
            calls.add(it)
            0
        })
        val commands = listOf(
            listOf("search", "recursion", "slides"),
            listOf("download-many", "42:7:1", "42:7:2"),
            listOf("tasks"),
            listOf("tasks", "--course", "42"),
            listOf("assignments", "--course", "42"),
            listOf("quizzes"),
            listOf("quizzes", "--course", "42"),
            listOf("upcoming", "--days", "7")
        )
        commands.forEach { assertEquals(0, cli.run(it.toTypedArray())) }
        assertEquals(commands, calls)
    }
}
