package moodle.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moodle.domain.entities.Assignment
import moodle.domain.entities.Course
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.Quiz
import moodle.presentation.PlainText

/** Deliberate output allowlist: no tokens, file URLs, raw HTML, or API warning payloads. */
internal class McpContent {
    private val text = PlainText()

    fun course(course: Course): JsonObject = buildJsonObject {
        put("id", course.id)
        put("name", text.label(course.name).take(1000))
    }

    fun searchHit(hit: CourseSearchHit): JsonObject = buildJsonObject {
        when (hit) {
            is CourseSearchHit.CourseTitle -> {
                put("kind", "course")
                putCourseContext(hit.course)
            }
            is CourseSearchHit.Activity -> {
                put("kind", "activity")
                putActivityContext(hit)
            }
            is CourseSearchHit.File -> {
                put("kind", "file")
                putActivityContext(hit.activity)
                put("file_number", hit.number)
                put("file_name", text.label(hit.name).take(1000))
            }
        }
    }

    private fun JsonObjectBuilder.putCourseContext(course: Course) {
        put("course_id", course.id)
        put("course_name", text.label(course.name).take(1000))
    }

    private fun JsonObjectBuilder.putActivityContext(activity: CourseSearchHit.Activity) {
        putCourseContext(activity.course)
        put("module_id", activity.id)
        put("activity_name", text.label(activity.name).take(1000))
    }

    fun activity(activity: McpActivity): JsonObject = buildJsonObject {
        val module = activity.module
        put("section_id", activity.section.id)
        put("section_name", text.label(activity.section.name).take(1000))
        put("section_description", summary(activity.section.description))
        put("module_id", module.id)
        put("name", text.label(module.name).take(1000))
        put("type", text.label(module.type).take(100))
        put("description", summary(module.description))
        put("inline_text", summary(module.texts.joinToString("\n")))
        put(
            "files",
            JsonArray(
                module.files.take(20).mapIndexed { index, file ->
                    buildJsonObject {
                        put("number", index + 1)
                        put("name", text.label(file.name).take(1000))
                        put("bytes", file.size)
                    }
                }
            )
        )
        put("file_count", module.files.size)
        put("files_shortened", module.files.size > 20)
    }

    fun assignment(assignment: Assignment): JsonObject = buildJsonObject {
        put("id", assignment.id)
        put("course_id", assignment.courseId)
        put("course_name", text.label(assignment.courseName).take(1000))
        put("name", text.label(assignment.name).take(1000))
        put("due_at", assignment.dueDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("submission_status", assignment.submissionStatus.name)
        put("group_submission", assignment.groupSubmission)
    }

    fun quiz(quiz: Quiz): JsonObject = buildJsonObject {
        put("id", quiz.id)
        put("course_id", quiz.courseId)
        put("name", text.label(quiz.name).take(1000))
        put("timing_available", quiz.timing != null)
        put("opens_at", quiz.timing?.opensAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("closes_at", quiz.timing?.closesAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
        put("time_limit_seconds", quiz.timing?.timeLimit?.let { JsonPrimitive(it.seconds) } ?: JsonNull)
    }

    fun read(html: String, arguments: McpArguments): JsonObject {
        val plain = text.render(html)
        val offset = arguments.number("offset", 0, 0, Int.MAX_VALUE)
        val limit = arguments.number("limit", 12000, 1, 20000)
        val chunk = plain.drop(offset).take(limit)
        val next = (offset + chunk.length).takeIf { chunk.isNotEmpty() && it < plain.length }
        return buildJsonObject {
            put("text", chunk)
            put("total_characters", plain.length)
            put("offset", offset)
            put("next_offset", next?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    private fun summary(html: String): String {
        val plain = text.render(html)
        return if (plain.length <= 2000) plain else plain.take(2000) + "\n[Preview shortened; use read_course_text.]"
    }
}
