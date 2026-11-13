package moodle.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moodle.domain.entities.Course
import moodle.fixtures.McpFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoodleMcpToolsTest {
    private val fixture = McpFixture()

    @Test
    fun `only explicit tools are exposed and pagination preserves IDs`() = runBlocking {
        assertEquals(
            setOf(
                "health",
                "list_courses",
                "search_course_materials",
                "get_course_contents",
                "read_course_text",
                "download_course_file",
                "download_course_files",
                "list_assignments",
                "upcoming_assignments",
                "list_quizzes"
            ),
            fixture.tools.tools
                .map { it.name }
                .toSet()
        )
        assertTrue(
            fixture.tools.tools
                .filterNot { it.name.startsWith("download_course_file") }
                .all { it.annotations?.readOnlyHint == true }
        )
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
        val text = call(
            "read_course_text",
            """{"course_id":1,"module_id":5}"""
        ).toString()
        // A page's protected index.html is read through the normal course service, not exposed as a URL.
        assertFalse(text.contains("private script"))
    }

    @Test
    fun `search pages expose reusable file selectors without file URLs`() = runBlocking {
        fixture.courses = listOf(Course(2, "<b>Second course</b>"), Course(1, "First course"))
        val first = call("search_course_materials", """{"query":"notes pdf","limit":1}""")
        assertEquals("1", first["next_offset"].toString())
        val second = call("search_course_materials", """{"query":"notes pdf","limit":1,"offset":1}""")
        val item = second["items"]?.jsonArray?.single()?.jsonObject
        assertEquals("2", item?.get("course_id").toString())
        assertEquals("5", item?.get("module_id").toString())
        assertEquals("1", item?.get("file_number").toString())
        assertEquals("Second course", item?.get("course_name")?.jsonPrimitive?.content)
        assertFalse(second.toString().contains("pluginfile.php"))
        assertFalse(second.toString().contains(fixture.token))
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

    @Test
    fun `download selects a known file and reports only its local path and size`() = runBlocking {
        val result = call("download_course_file", """{"course_id":1,"module_id":5,"file_number":1}""")
        assertEquals("notes.pdf", fixture.downloadedFile)
        assertEquals(
            java.nio.file.Path
                .of(System.getProperty("user.home"), "Downloads", "Moodle", "notes.pdf")
                .toString(),
            result["path"]?.jsonPrimitive?.content
        )
        assertEquals("123", result["bytes"].toString())
        assertFalse(result.toString().contains(fixture.token))
        assertFalse(result.toString().contains("pluginfile.php"))
        val annotations = fixture.tools.tools
            .single { it.name == "download_course_file" }
            .annotations
        assertEquals(false, annotations?.readOnlyHint)
        assertEquals(false, annotations?.destructiveHint)
        assertEquals(false, annotations?.idempotentHint)
    }

    @Test
    fun `invalid download arguments cannot cause network or filesystem access`() = runBlocking {
        for (extra in listOf("", ""","file_number":0""", ""","file_number":"1" """, ""","file_number":1,"directory":"/tmp" """)) {
            val result = fixture.tools.call("download_course_file", objectOf("""{"course_id":1,"module_id":5$extra}"""))
            assertEquals(true, result.isError)
        }
        assertEquals(0, fixture.courseCalls)
        assertEquals(null, fixture.downloadedFile)
    }

    @Test
    fun `batch tool reports a result per distinct selector without exposing file URLs`() = runBlocking {
        val result = call(
            "download_course_files",
            """{"files":[{"course_id":1,"module_id":5,"file_number":3},{"course_id":1,"module_id":5,"file_number":1},
                {"course_id":1,"module_id":5,"file_number":1}]}"""
        )
        val items = result["items"]?.jsonArray.orEmpty().map { it.jsonObject }
        assertEquals(listOf("failed", "saved"), items.map { it["status"]?.jsonPrimitive?.content })
        assertEquals(
            "error.course.fileNumber",
            items
                .first()["error"]
                ?.jsonObject
                ?.get("code")
                ?.jsonPrimitive
                ?.content
        )
        assertTrue(
            items
                .last()["path"]
                ?.jsonPrimitive
                ?.content
                .orEmpty()
                .endsWith("notes.pdf")
        )
        assertEquals(1, fixture.courseCalls)
        assertFalse(result.toString().contains(fixture.token))
        assertFalse(result.toString().contains("pluginfile.php"))
        val annotations = fixture.tools.tools
            .single { it.name == "download_course_files" }
            .annotations
        assertEquals(false, annotations?.readOnlyHint)
    }

    @Test
    fun `invalid batch selections are all rejected before requests or writes`() = runBlocking {
        val valid = """{"course_id":1,"module_id":5,"file_number":1}"""
        val invalid = listOf(
            "[]",
            List(11) { valid }.joinToString(",", "[", "]"),
            """[$valid,{"course_id":1,"module_id":5,"file_number":"2"}]""",
            """[$valid,{"course_id":1,"module_id":5,"file_number":2,"url":"https://fixture.invalid"}]""",
            """[$valid,{"course_id":1,"module_id":5}]""",
            "[$valid,null]"
        )
        for (files in invalid) {
            val result = fixture.tools.call("download_course_files", objectOf("""{"files":$files}"""))
            assertEquals(true, result.isError)
        }
        assertEquals(0, fixture.courseCalls)
        assertEquals(null, fixture.downloadedFile)
    }

    private suspend fun call(name: String, arguments: String = "{}"): JsonObject {
        val result = fixture.tools.call(name, objectOf(arguments))
        assertEquals(false, result.isError, result.toString())
        return requireNotNull(result.structuredContent)
    }

    private fun objectOf(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
}
