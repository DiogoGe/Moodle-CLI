package moodle.cli

import moodle.application.CourseService
import moodle.domain.entities.CourseModule
import moodle.domain.exceptions.MoodleException

class CourseCommands(private val service: CourseService, private val output: (String) -> Unit = ::println) {
    private val text = TerminalText()

    fun run(args: List<String>): Int {
        when {
            args == listOf("courses") -> showCourses()
            args.size == 2 && args[0] == "course" -> showContents(id(args[1]))
            args.size == 3 && args[0] == "read" -> output(text.render(service.readText(id(args[1]), id(args[2]))))
            args.size in 4..5 && args[0] == "download" -> download(args)
            else -> throw MoodleException.Input("Use courses, course <id>, read <course-id> <module-id>, or $DOWNLOAD_USAGE.")
        }
        return 0
    }

    private fun showCourses() {
        val courses = service.getCourses()
        if (courses.isEmpty()) output("No enrolled courses found.")
        courses.forEach { output("${it.id}  ${text.label(it.name)}") }
    }

    private fun showContents(courseId: Long) {
        val sections = service.getContents(courseId)
        if (sections.isEmpty()) output("No accessible course contents found.")
        sections.forEach { section ->
            output("\n${text.label(section.name)}")
            showText(section.description)
            section.modules.forEach { showModule(courseId, it) }
        }
    }

    private fun showModule(courseId: Long, module: CourseModule) {
        output("  [${module.id}] ${text.label(module.name)} (${text.label(module.type)})")
        showText(module.description)
        module.texts.forEach(::showText)
        if (module.type == "page") output("    Read page: read $courseId ${module.id}")
        module.files.forEachIndexed { index, file ->
            output("    ${index + 1}. ${text.label(file.name)} (${file.size} bytes)")
            output("       download $courseId ${module.id} ${index + 1}")
        }
    }

    private fun showText(html: String) {
        val rendered = text.render(html)
        if (rendered.isNotBlank()) output(rendered)
    }

    private fun download(args: List<String>) {
        val number = args[3].toIntOrNull()?.takeIf { it > 0 } ?: throw MoodleException.Input("File number must be positive.")
        val result = service.download(id(args[1]), id(args[2]), number, args.getOrElse(4) { "downloads" })
        output("Saved ${result.bytes} bytes to ${text.label(result.path)}")
    }

    private fun id(value: String): Long = value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw MoodleException.Input("IDs must be positive numbers.")

    private companion object {
        const val DOWNLOAD_USAGE = "download <course-id> <module-id> <file-number> [directory]"
    }
}
