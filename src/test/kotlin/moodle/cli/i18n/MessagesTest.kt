package moodle.cli.i18n

import moodle.domain.exceptions.MoodleException
import java.text.MessageFormat
import java.util.Locale
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessagesTest {
    @Test
    fun `Swiss German uses German and unsupported languages fall back to English`() {
        assertEquals("Keine eingeschriebenen Kurse gefunden.", Messages(Locale.forLanguageTag("de-CH")).text("course.empty"))
        assertEquals("No enrolled courses found.", Messages(Locale.FRENCH).text("course.empty"))
        assertEquals("No enrolled courses found.", Messages(Locale.ENGLISH).text("course.empty"))
    }

    @Test
    fun `errors translate at presentation time and identifiers are never grouped`() {
        val failure = MoodleException.Input("error.course.fileNumber", 12345)
        assertEquals("Unbekannte Dateinummer. Liste die Dateien mit course 12345 auf.", Messages(Locale.GERMAN).error(failure))
        assertEquals("Unknown file number. Run course 12345 to list files.", Messages(Locale.ENGLISH).error(failure))
        assertEquals("error.course.fileNumber", failure.message)
        assertFalse(failure.toString().contains("12345"))
    }

    @Test
    fun `apostrophes argument braces percent signs and indentation survive formatting`() {
        val english = Messages(Locale.ENGLISH)
        assertTrue(english.text("login.address").contains("browser's address bar"))
        val path = "/tmp/Student's {notes} 100%"
        assertEquals("Saved 1234 bytes to $path", english.text("download.saved", 1234, path))
        assertTrue(english.text("course.file", 1, "notes.pdf", 1234).startsWith("    1."))
        assertEquals("\nWeek 1", english.text("course.section", "Week 1"))
    }

    @Test
    fun `German and English resources have the same keys and argument placeholders`() {
        val english = load("messages.properties")
        val german = load("messages_de.properties")
        assertEquals(english.stringPropertyNames(), german.stringPropertyNames())
        val placeholder = Regex("\\{\\d+}")
        english.stringPropertyNames().forEach { key ->
            val englishPattern = english.getProperty(key)
            val germanPattern = german.getProperty(key)
            assertEquals(
                placeholder.findAll(englishPattern).map { it.value }.toSet(),
                placeholder.findAll(germanPattern).map { it.value }.toSet(),
                key
            )
            MessageFormat(englishPattern, Locale.ENGLISH)
            MessageFormat(germanPattern, Locale.GERMAN)
            assertTrue(germanPattern.isNotBlank(), key)
        }
    }

    private fun load(name: String): Properties = Properties().also { properties ->
        val stream = checkNotNull(javaClass.getResourceAsStream("/i18n/$name"))
        stream.reader(Charsets.UTF_8).use(properties::load)
    }
}
