package moodle.cli

import moodle.domain.exceptions.MoodleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalTextTest {
    @Test
    fun `HTML preserves readable paragraphs decodes entities and removes scripts and terminal controls`() {
        val html = "<h2>Week 1</h2><p>A &amp; B<br>Next</p><ul><li>One</li><li>Two</li></ul>" +
            "<script>secret-script()</script><style>hidden-style</style>\u001b[31m"
        val result = TerminalText().render(html)
        assertTrue(result.contains("A & B\nNext"))
        assertTrue(result.contains("One\n\nTwo"))
        assertFalse(result.contains("secret-script"))
        assertFalse(result.contains("hidden-style"))
        assertFalse(result.contains('\u001b'))
    }

    @Test
    fun `session arguments keep quoted directory names without shell expansion`() {
        assertEquals(
            listOf("download", "12", "34", "1", "/tmp/My Files"),
            SessionArguments.parse("download 12 34 1 \"/tmp/My Files\"")
        )
        assertEquals(listOf("course", "12"), SessionArguments.parse("  course  12 "))
        assertFailsWith<MoodleException.Input> { SessionArguments.parse("download 12 34 1 \"unfinished") }
    }
}
