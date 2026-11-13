package moodle.cli

import moodle.application.CourseService
import moodle.cli.i18n.Messages
import moodle.config.MoodleConfig
import moodle.domain.entities.CourseDownloadOutcome
import moodle.domain.entities.CourseFileSelection
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.exceptions.MoodleException
import moodle.presentation.PlainText
import java.net.URI

class CourseCommands(
    private val service: CourseService,
    private val output: (String) -> Unit = ::println,
    private val messages: Messages = Messages(),
    config: MoodleConfig = MoodleConfig(),
    private val browse: (URI) -> Boolean = ::openInDefaultBrowser
) {
    private val text = PlainText()
    private val site = URI(config.baseUrl)

    fun run(args: List<String>): Int {
        when {
            args == listOf("courses") -> showCourses()
            args.size == 2 && args[0] == "course" -> showContents(id(args[1]))
            args.firstOrNull() == "search" -> search(args.drop(1))
            args.size == 3 && args[0] == "open" -> return openActivity(id(args[1]), id(args[2]))
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

    private fun search(args: List<String>) {
        val keywords = mutableListOf<String>()
        var courseId: Long? = null
        var pageOffset: Int? = null
        val arguments = args.iterator()
        while (arguments.hasNext()) {
            when (val argument = arguments.next()) {
                "--course" -> {
                    if (courseId != null || !arguments.hasNext()) throw MoodleException.Input("error.search.usage")
                    courseId = id(arguments.next())
                }
                "--pages" -> if (pageOffset == null) pageOffset = 0
                "--page-offset" -> {
                    if (!arguments.hasNext()) throw MoodleException.Input("error.search.usage")
                    pageOffset = arguments.next().toIntOrNull() ?: throw MoodleException.Input("error.search.pageOffset")
                }
                else -> {
                    if (argument.startsWith("--")) throw MoodleException.Input("error.search.usage")
                    keywords.add(argument)
                }
            }
        }
        val result = service.search(keywords.joinToString(" "), courseId, pageOffset)
        if (result.skippedCourses > 0) output(messages.text("search.partial", result.skippedCourses))
        if (result.skippedPages > 0) output(messages.text("search.pagesSkipped", result.skippedPages))
        if (result.items.isEmpty()) output(messages.text("search.empty"))
        result.items.forEach(::showSearchHit)
        result.nextPageOffset?.let { output(messages.text("search.morePages", it)) }
    }

    private fun showSearchHit(hit: CourseSearchHit) {
        when (hit) {
            is CourseSearchHit.CourseTitle -> {
                output(messages.text("search.course", text.label(hit.course.name), hit.course.id))
                hit.snippet?.let { output(messages.text("search.excerpt", it)) }
            }
            is CourseSearchHit.Activity -> showActivitySearchHit(hit, hit.snippet)
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
                output(messages.text("course.open", activity.course.id, activity.id))
            }
            is CourseSearchHit.SectionText -> {
                output(messages.text("search.section", text.label(hit.sectionName), text.label(hit.course.name), hit.course.id))
                output(messages.text("search.excerpt", hit.snippet))
            }
            is CourseSearchHit.PageText -> showActivitySearchHit(hit.activity, hit.snippet, page = true)
        }
    }

    private fun showActivitySearchHit(hit: CourseSearchHit.Activity, snippet: String?, page: Boolean = false) {
        val title = if (page) "search.page" else "search.activity"
        output(messages.text(title, text.label(hit.name), text.label(hit.course.name), hit.course.id, hit.id))
        snippet?.let { output(messages.text("search.excerpt", it)) }
        output(messages.text("course.open", hit.course.id, hit.id))
        if (page) output(messages.text("course.read", hit.course.id, hit.id))
    }

    private fun showModule(courseId: Long, module: CourseModule) {
        output(messages.text("course.module", module.id, text.label(module.name), text.label(module.type)))
        output(messages.text("course.open", courseId, module.id))
        showText(module.description)
        module.texts.forEach(::showText)
        if (module.type == "page") output(messages.text("course.read", courseId, module.id))
        module.files.forEachIndexed { index, file ->
            output(messages.text("course.file", index + 1, text.label(file.name), file.size))
            output(messages.text("course.download", courseId, module.id, index + 1))
        }
    }

    private fun openActivity(courseId: Long, moduleId: Long): Int {
        val module = service.getModule(courseId, moduleId)
        val path = if (module.noViewLink || !MODULE_TYPE.matches(module.type)) {
            "course/view.php?id=$courseId#module-$moduleId"
        } else {
            "mod/${module.type}/view.php?id=$moduleId&forceview=1"
        }
        val url = site.resolve(path)
        val opened = browse(url)
        output(messages.text(if (opened) "course.opened" else "course.openManual", url))
        return if (opened) 0 else 1
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
        var directory: String? = null
        var organize = false
        val selections = mutableListOf<CourseFileSelection>()
        val arguments = args.iterator()
        while (arguments.hasNext()) {
            when (val argument = arguments.next()) {
                "--organize" -> {
                    if (organize) throw MoodleException.Input("error.download.batchSelector")
                    organize = true
                }
                "--directory" -> {
                    if (directory != null || !arguments.hasNext()) throw MoodleException.Input("error.download.batchSelector")
                    directory = arguments.next()
                    if (directory.isBlank()) throw MoodleException.Input("error.download.directory")
                }
                else -> selections.add(parseSelection(argument))
            }
        }
        val results = service.downloadMany(selections, directory ?: "downloads", organize)
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

    private companion object {
        val MODULE_TYPE = Regex("[a-z][a-z0-9_]*")
    }
}
