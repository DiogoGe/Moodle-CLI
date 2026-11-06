package moodle.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainTextTest {
    @Test
    fun `HTML preserves readable paragraphs decodes entities and removes scripts and terminal controls`() {
        val html = "<h2>Week 1</h2><p>A &amp; B<br>Next</p><ul><li>One</li><li>Two</li></ul>" +
            "<script>secret-script()</script><style>hidden-style</style>\u001b[31m"
        val result = PlainText().render(html)
        assertTrue(result.contains("A & B\nNext"))
        assertTrue(result.contains("One\n\nTwo"))
        assertFalse(result.contains("secret-script"))
        assertFalse(result.contains("hidden-style"))
        assertFalse(result.contains('\u001b'))
    }
}
