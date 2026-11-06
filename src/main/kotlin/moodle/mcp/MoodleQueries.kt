package moodle.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moodle.MoodleApplication
import moodle.domain.exceptions.MoodleException

internal class MoodleQueries(private val application: MoodleApplication) {
    private val content = McpContent()

    fun execute(name: String, arguments: McpArguments): JsonObject = when (name) {
        "health" -> health()
        "list_courses" -> McpPage(arguments).render(application.courses.getCourses(), content::course)
        "get_course_contents" -> courseContents(arguments)
        "read_course_text" -> readText(arguments)
        "list_assignments" -> assignments(arguments)
        "upcoming_assignments" -> upcoming(arguments)
        "list_quizzes" -> quizzes(arguments)
        else -> throw MoodleException.Input("error.mcp.arguments")
    }

    private fun health(): JsonObject {
        val public = application.access.getPublicStatus()
        val authenticated = application.access.getAuthenticatedStatus() != null
        return buildJsonObject {
            put("site_url", public.siteUrl)
            put("web_services_enabled", public.webServicesEnabled)
            put("mobile_service_enabled", public.mobileServiceEnabled)
            put("browser_login_enabled", public.browserLogin)
            put("maintenance", public.maintenance)
            put("authenticated", authenticated)
        }
    }

    private fun courseContents(arguments: McpArguments): JsonObject {
        val courseId = arguments.id("course_id")
        val pagination = McpPage(arguments)
        val sections = application.courses.getContents(courseId)
        val activities = sections.flatMap { section -> section.modules.map { McpActivity(section, it) } }
        val page = pagination.render(activities, content::activity)
        return JsonObject(page + ("course_id" to JsonPrimitive(courseId)))
    }

    private fun readText(arguments: McpArguments): JsonObject {
        // Validate bounds before any remote request, even though text formatting also validates them.
        arguments.number("offset", 0, 0, Int.MAX_VALUE)
        arguments.number("limit", 12000, 1, 20000)
        return content.read(application.courses.readText(arguments.id("course_id"), arguments.id("module_id")), arguments)
    }

    private fun assignments(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val result = application.assignments.getAssignments(arguments.optionalId("course_id"))
        return JsonObject(page.render(result.items, content::assignment) + ("partial" to JsonPrimitive(result.partial)))
    }

    private fun upcoming(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val days = arguments.number("days", 0, 1, 366)
        if (days == 0) throw MoodleException.Input("error.mcp.arguments")
        val result = application.assignments.getUpcoming(days)
        return JsonObject(page.render(result.items, content::assignment) + ("partial" to JsonPrimitive(result.partial)))
    }

    private fun quizzes(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val result = application.quizzes.getQuizzes(arguments.optionalId("course_id"))
        return JsonObject(page.render(result.items, content::quiz) + ("partial" to JsonPrimitive(result.partial)))
    }
}
