package moodle.cli

import moodle.application.CourseService
import moodle.cli.i18n.Messages
import moodle.domain.entities.CourseDownloadOutcome
import moodle.domain.entities.CourseFileSelection
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.exceptions.MoodleException
import moodle.presentation.PlainText

class CourseCommands(
    private val service: CourseService,
    private val output: (String) -> Unit = ::println,
    private val messages: Messages = Messages()
) {
    private val text = PlainText()

    fun run(args: List<String>): Int {
        when {
            args == listOf("courses") -> showCourses()
            args.size == 2 && args[0] == "course" -> showContents(id(args[1]))
            args.firstOrNull() == "search" -> search(args.drop(1).joinToString(" "))
            args.size == 3 && args[0] == "read" -> output(text.render(service.readText(id(args[1]), id(args[2]))))
            args.size in 4..5 && args[0] == "download" -> download(args)
            args.firstOrNull() == "download-many" -> return downloadMany(args.drop(1))
            else -> throw MoodleException.Input("error.course.usage")
        }
        return 0
    }

    private fun showCourses() {
        val courses = service.getCourses()
        if (courses.isEmpty()) output(messages.text("course.empty"))
        courses.forEach { output(messages.text("course.row", it.id, text.label(it.name))) }
    }

    private fun showContents(courseId: Long) {
        val sections = service.getContents(courseId)
        if (sections.isEmpty()) output(messages.text("course.emptyContents"))
        sections.forEach { section ->
            output(messages.text("course.section", text.label(section.name)))
            showText(section.description)
            section.modules.forEach { showModule(courseId, it) }
        }
    }

    private fun search(query: String) {
        val result = service.search(query)
        if (result.skippedCourses > 0) output(messages.text("search.partial", result.skippedCourses))
        if (result.items.isEmpty()) output(messages.text("search.empty"))
        result.items.forEach(::showSearchHit)
    }

    private fun showSearchHit(hit: CourseSearchHit) {
        when (hit) {
            is CourseSearchHit.CourseTitle ->
                output(messages.text("search.course", text.label(hit.course.name), hit.course.id))
            is CourseSearchHit.Activity ->
                output(messages.text("search.activity", text.label(hit.name), text.label(hit.course.name), hit.course.id, hit.id))
            is CourseSearchHit.File -> {
                val activity = hit.activity
                val selection = CourseFileSelection(activity.course.id, activity.id, hit.number)
                output(
                    messages.text(
                        "search.file",
                        text.label(hit.name),
                        text.label(activity.name),
                        text.label(activity.course.name),
                        selector(selection)
                    )
                )
                output(messages.text("course.download", activity.course.id, activity.id, hit.number))
            }
        }
    }

    private fun showModule(courseId: Long, module: CourseModule) {
        output(messages.text("course.module", module.id, text.label(module.name), text.label(module.type)))
        showText(module.description)
        module.texts.forEach(::showText)
        if (module.type == "page") output(messages.text("course.read", courseId, module.id))
        module.files.forEachIndexed { index, file ->
            output(messages.text("course.file", index + 1, text.label(file.name), file.size))
            output(messages.text("course.download", courseId, module.id, index + 1))
        }
    }

    private fun showText(html: String) {
        val rendered = text.render(html)
        if (rendered.isNotBlank()) output(rendered)
    }

    private fun download(args: List<String>) {
        val number = args[3].toIntOrNull()?.takeIf { it > 0 } ?: throw MoodleException.Input("error.input.fileNumber")
        val result = service.download(id(args[1]), id(args[2]), number, args.getOrElse(4) { "downloads" })
        output(messages.text("download.saved", result.bytes, text.label(result.path)))
    }

    private fun downloadMany(args: List<String>): Int {
        val hasDirectory = args.getOrNull(args.size - 2) == "--directory"
        val directory = if (hasDirectory) args.last() else "downloads"
        if (directory.isBlank()) throw MoodleException.Input("error.download.directory")
        val selectors = if (hasDirectory) args.dropLast(2) else args
        val results = service.downloadMany(selectors.map(::parseSelection), directory)
        results.forEach(::showDownloadOutcome)
        return if (results.all { it is CourseDownloadOutcome.Saved }) 0 else 1
    }

    private fun parseSelection(value: String): CourseFileSelection {
        val parts = value.split(':', limit = 4)
        if (parts.size != 3) throw MoodleException.Input("error.download.batchSelector")
        val number = parts[2].toIntOrNull() ?: throw MoodleException.Input("error.download.batchSelector")
        return CourseFileSelection(id(parts[0]), id(parts[1]), number)
    }

    private fun showDownloadOutcome(outcome: CourseDownloadOutcome) {
        val reference = selector(outcome.selection)
        when (outcome) {
            is CourseDownloadOutcome.Saved ->
                output(messages.text("download.batchSaved", reference, outcome.file.bytes, text.label(outcome.file.path)))
            is CourseDownloadOutcome.Failed -> output(
                messages.text("download.batchFailed", reference, messages.error(outcome.error))
            )
            is CourseDownloadOutcome.Skipped -> output(messages.text("download.batchSkipped", reference))
        }
    }

    private fun selector(selection: CourseFileSelection): String =
        "${selection.courseId}:${selection.moduleId}:${selection.fileNumber}"

    private fun id(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw MoodleException.Input("error.input.id")
}
