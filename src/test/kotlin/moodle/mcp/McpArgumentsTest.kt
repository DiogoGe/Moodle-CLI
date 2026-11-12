package moodle.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import moodle.domain.exceptions.MoodleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class McpArgumentsTest {
    @Test
    fun `required numbers reject missing invalid and overflowing values`() {
        val invalid = listOf("null", "0", "-1", "2147483648", "9223372036854775808", "1.5", "true", "\"1\"")
        assertFailsWith<MoodleException.Input> { arguments("{}").requiredNumber("value", 1, Int.MAX_VALUE) }
        for (value in invalid) {
            assertFailsWith<MoodleException.Input> {
                arguments("""{"value":$value}""").requiredNumber("value", 1, Int.MAX_VALUE)
            }
        }
    }

    @Test
    fun `required numbers preserve inclusive boundaries`() {
        assertEquals(1, arguments("""{"value":1}""").requiredNumber("value", 1, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, arguments("""{"value":2147483647}""").requiredNumber("value", 1, Int.MAX_VALUE))
    }

    @Test
    fun `only absent optional numbers use a validated default`() {
        assertEquals(25, arguments("{}").number("value", 25, 1, 100))
        assertFailsWith<MoodleException.Input> { arguments("{}").number("value", 0, 1, 100) }
        assertFailsWith<MoodleException.Input> { arguments("""{"value":null}""").number("value", 25, 1, 100) }
    }

    private fun arguments(json: String): McpArguments = McpArguments(Json.parseToJsonElement(json).jsonObject, setOf("value"))
}
