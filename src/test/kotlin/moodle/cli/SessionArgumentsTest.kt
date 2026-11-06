package moodle.cli

import moodle.domain.exceptions.MoodleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionArgumentsTest {
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
