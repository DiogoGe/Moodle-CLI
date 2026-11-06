package moodle.cli

import moodle.application.CourseService
import moodle.cli.i18n.Messages
import moodle.domain.entities.CourseModule
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
            args.size == 3 && args[0] == "read" -> output(text.render(service.readText(id(args[1]), id(args[2]))))
            args.size in 4..5 && args[0] == "download" -> download(args)
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

    private fun id(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw MoodleException.Input("error.input.id")
}
