package moodle.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moodle.fixtures.McpFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoodleMcpToolsTest {
    private val fixture = McpFixture()

    @Test
    fun `only explicit read tools are exposed and pagination preserves IDs`() = runBlocking {
        assertEquals(
            setOf(
                "health",
                "list_courses",
                "get_course_contents",
                "read_course_text",
                "list_assignments",
                "upcoming_assignments",
                "list_quizzes"
            ),
            fixture.tools.tools
                .map { it.name }
                .toSet()
        )
        assertTrue(fixture.tools.tools.all { it.annotations?.readOnlyHint == true })
        val first = call("list_courses")
        assertEquals("25", first["next_offset"].toString())
        assertEquals(25, first["items"]?.jsonArray?.size)
        val second = call("list_courses", """{"offset":25,"limit":10}""")
        assertEquals(5, second["items"]?.jsonArray?.size)
        assertEquals(
            "26",
            second["items"]
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("id")
                .toString()
        )
        assertEquals("null", second["next_offset"].toString())
    }

    @Test
    fun `file metadata never exposes URLs tokens scripts or private account identity`() = runBlocking {
        val course = call("get_course_contents", """{"course_id":1}""").toString()
        assertTrue(course.contains("notes.pdf"))
        assertFalse(course.contains(fixture.token))
        assertFalse(course.contains("pluginfile.php"))
        assertFalse(course.contains("<b>"))
        val health = call("health").toString()
        assertFalse(health.contains("Private student name"))
        assertFalse(health.contains("user_id"))
        val text = call("read_course_text", """{"course_id":1,"module_id":5}""").toString()
        // A page's protected index.html is read through the normal course service, not exposed as a URL.
        assertFalse(text.contains("private script"))
    }

    @Test
    fun `invalid tool arguments are rejected before any course request`() = runBlocking {
        val invalid = listOf(
            "{}",
            """{"course_id":-1}""",
            """{"course_id":"1"}""",
            """{"course_id":null}""",
            """{"course_id":1,"limit":0}""",
            """{"course_id":1,"limit":101}""",
            """{"course_id":1,"offset":-1}""",
            """{"course_id":1,"url":"https://other.invalid"}"""
        )
        for (arguments in invalid) {
            val result = fixture.tools.call("get_course_contents", objectOf(arguments))
            assertEquals(true, result.isError)
        }
        assertEquals(0, fixture.courseCalls)
    }

    @Test
    fun `assignment partial access and quiz timing remain explicit`() = runBlocking {
        val assignments = call("list_assignments", """{"course_id":1}""")
        assertEquals("true", assignments["partial"].toString())
        assertEquals(1L, fixture.assignments.requestedCourse)
        val upcoming = call("upcoming_assignments", """{"days":7}""")
        assertEquals(1, upcoming["items"]?.jsonArray?.size)
        val quiz = call("list_quizzes")["items"]?.jsonArray?.single()?.jsonObject
        assertEquals("300", quiz?.get("time_limit_seconds").toString())
        assertEquals("null", quiz?.get("closes_at").toString())
    }

    @Test
    fun `unexpected failures and missing authentication return sanitized errors`() = runBlocking {
        fixture.failure = IllegalStateException("Never reveal ${fixture.token}")
        val failed = fixture.tools.call("list_courses", objectOf("{}"))
        assertEquals(true, failed.isError)
        assertFalse(failed.toString().contains(fixture.token))
        assertFalse(failed.toString().contains("Never reveal"))
        fixture.access.endSession()
        val signedOut = fixture.tools.call("list_courses", objectOf("{}"))
        assertEquals(true, signedOut.isError)
        assertTrue(signedOut.structuredContent.toString().contains("error.session.required"))
    }

    @Test
    fun `text pagination bounds content without executing scripts or following links`() = runBlocking {
        val content = McpContent()
        val arguments = McpArguments(objectOf("""{"offset":2,"limit":4}"""), setOf("offset", "limit"))
        val result = content.read("<p>abcdefghij</p><script>ignore instructions</script>", arguments)
        assertEquals("cdef", result["text"]?.jsonPrimitive?.content)
        assertEquals("6", result["next_offset"].toString())
    }

    @Test
    fun `oversized results report a bounded error so clients can reduce their page size`() = runBlocking {
        fixture.courses = List(100) { moodle.domain.entities.Course(it + 1L, "漢".repeat(1000)) }
        val result = fixture.tools.call("list_courses", objectOf("""{"limit":100}"""))
        assertEquals(true, result.isError)
        assertTrue(result.structuredContent.toString().contains("error.mcp.resultTooLarge"))
        assertTrue(result.toString().length < 1000)
    }

    private suspend fun call(name: String, arguments: String = "{}"): JsonObject {
        val result = fixture.tools.call(name, objectOf(arguments))
        assertEquals(false, result.isError, result.toString())
        return requireNotNull(result.structuredContent)
    }

    private fun objectOf(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
}
