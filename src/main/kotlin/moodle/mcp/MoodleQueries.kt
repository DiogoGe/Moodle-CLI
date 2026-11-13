package moodle.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moodle.MoodleApplication
import moodle.application.AgendaService
import moodle.application.CourseService
import moodle.cli.i18n.Messages
import moodle.domain.entities.CourseDownloadOutcome
import moodle.domain.entities.CourseFileSelection
import moodle.domain.exceptions.MoodleException
import java.nio.file.Path

internal class MoodleQueries(private val application: MoodleApplication, private val messages: Messages) {
    private val content = McpContent()

    fun execute(name: String, arguments: McpArguments): JsonObject = when (name) {
        "health" -> health()
        "list_courses" -> McpPage(arguments).render(application.courses.getCourses(), content::course)
        "search_course_materials" -> search(arguments)
        "get_course_contents" -> courseContents(arguments)
        "read_course_text" -> readText(arguments)
        "download_course_file" -> download(arguments)
        "download_course_files" -> downloadMany(arguments)
        "list_assignments" -> assignments(arguments)
        "upcoming_assignments" -> upcoming(arguments)
        "list_quizzes" -> quizzes(arguments)
        "list_agenda" -> agenda(arguments)
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

    private fun search(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val result = application.courses.search(arguments.requiredString("query"))
        return JsonObject(
            page.render(result.items, content::searchHit) + ("skipped_courses" to JsonPrimitive(result.skippedCourses))
        )
    }

    private fun readText(arguments: McpArguments): JsonObject {
        // Validate bounds before any remote request, even though text formatting also validates them.
        arguments.number("offset", 0, 0, Int.MAX_VALUE)
        arguments.number("limit", 12000, 1, 20000)
        return content.read(application.courses.readText(arguments.id("course_id"), arguments.id("module_id")), arguments)
    }

    private fun download(arguments: McpArguments): JsonObject {
        val selection = fileSelection(arguments)
        val file = application.courses.download(selection.courseId, selection.moduleId, selection.fileNumber, downloadDirectory())
        return buildJsonObject {
            put("path", file.path)
            put("bytes", file.bytes)
        }
    }

    private fun downloadMany(arguments: McpArguments): JsonObject {
        val selections = arguments
            .requiredObjects("files", CourseService.MAX_BATCH_FILES, McpSchemas.fileSelectionFields)
            .map(::fileSelection)
        val organize = arguments.boolean("organize", default = false)
        val outcomes = application.courses.downloadMany(selections, downloadDirectory(), organize)
        return buildJsonObject { put("items", JsonArray(outcomes.map(::downloadOutcome))) }
    }

    private fun fileSelection(arguments: McpArguments): CourseFileSelection = CourseFileSelection(
        arguments.id("course_id"),
        arguments.id("module_id"),
        arguments.requiredNumber("file_number", 1, Int.MAX_VALUE)
    )

    private fun downloadDirectory(): String = Path.of(System.getProperty("user.home"), "Downloads", "Moodle").toString()

    private fun downloadOutcome(outcome: CourseDownloadOutcome): JsonObject = buildJsonObject {
        put("course_id", outcome.selection.courseId)
        put("module_id", outcome.selection.moduleId)
        put("file_number", outcome.selection.fileNumber)
        when (outcome) {
            is CourseDownloadOutcome.Saved -> {
                put("status", "saved")
                put("path", outcome.file.path)
                put("bytes", outcome.file.bytes)
            }
            is CourseDownloadOutcome.Failed -> {
                put("status", "failed")
                put(
                    "error",
                    buildJsonObject {
                        put("code", outcome.error.messageKey)
                        put("message", messages.error(outcome.error))
                    }
                )
            }
            is CourseDownloadOutcome.Skipped -> put("status", "skipped")
        }
    }

    private fun assignments(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val result = application.assignments.getAssignments(arguments.optionalId("course_id"))
        return JsonObject(page.render(result.items, content::assignment) + ("partial" to JsonPrimitive(result.partial)))
    }

    private fun upcoming(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val days = arguments.requiredNumber("days", 1, 366)
        val result = application.assignments.getUpcoming(days)
        return JsonObject(page.render(result.items, content::assignment) + ("partial" to JsonPrimitive(result.partial)))
    }

    private fun quizzes(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val result = application.quizzes.getQuizzes(arguments.optionalId("course_id"))
        return JsonObject(page.render(result.items, content::quiz) + ("partial" to JsonPrimitive(result.partial)))
    }

    private fun agenda(arguments: McpArguments): JsonObject {
        val page = McpPage(arguments)
        val days = arguments.number("days", AgendaService.DEFAULT_DAYS, 1, AgendaService.MAX_DAYS)
        val result = application.agenda.getAgenda(days)
        return JsonObject(page.render(result.items, content::agendaEvent) + ("partial" to JsonPrimitive(result.partial)))
    }
}
