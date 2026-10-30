package moodle.cli

import moodle.cli.i18n.Messages
import moodle.domain.exceptions.MoodleException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliOptionsTest {
    @Test
    fun `language option overrides the system language and preserves command arguments`() {
        val options = CliOptions.parse(arrayOf("--lang", "en", "login", "--manual"), Locale.GERMAN)
        assertEquals(Locale.ENGLISH, options.locale)
        assertEquals(listOf("login", "--manual"), options.arguments)
        assertEquals(Locale.GERMAN, CliOptions.parse(arrayOf("courses"), Locale.GERMAN).locale)
    }

    @Test
    fun `missing and unsupported language options fail without echoing supplied values`() {
        assertEquals(
            "error.language.missing",
            assertFailsWith<MoodleException.Input> { CliOptions.parse(arrayOf("--lang")) }.messageKey
        )
        val error = assertFailsWith<MoodleException.Input> { CliOptions.parse(arrayOf("--lang", "private-value")) }
        assertEquals("error.language.unsupported", error.messageKey)
        assertFalse(Messages(Locale.GERMAN).error(error).contains("private-value"))
    }

    @Test
    fun `German help and errors use the same language without changing command names`() {
        val output = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val cli = MoodleCli(
            output = output::add,
            errorOutput = errors::add,
            courseCommand = { throw MoodleException.Authentication("error.session.required") },
            messages = Messages(Locale.GERMAN)
        )
        assertEquals(0, cli.run(arrayOf("--help")))
        assertTrue(output.single().contains("Aufruf: moodle"))
        assertTrue(output.single().contains("courses"))
        assertEquals(1, cli.run(arrayOf("courses")))
        assertEquals("Melde dich zuerst mit login an.", errors.single())
    }
}
