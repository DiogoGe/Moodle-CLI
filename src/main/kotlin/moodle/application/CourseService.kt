package moodle.application

import moodle.domain.entities.Course
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSection
import moodle.domain.entities.DownloadedFile
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.CourseFiles
import moodle.domain.interfaces.CourseRepository

class CourseService(private val access: AccessService, private val repository: CourseRepository, private val files: CourseFiles) {
    fun getCourses(): List<Course> = access.withSession { token, userId -> repository.getCourses(token, userId) }

    fun getContents(courseId: Long): List<CourseSection> {
        validateId(courseId)
        return access.withSession { token, _ -> repository.getContents(token, courseId) }
    }

    fun readText(courseId: Long, moduleId: Long): String {
        val module = findModule(courseId, moduleId)
        val inline = listOf(module.description) + module.texts
        val page = module.files.firstOrNull { module.type == "page" && it.name == "index.html" }
        val pageText = page?.let { file -> access.withSession { token, _ -> files.readText(token, file) } }
        val text = (inline + listOfNotNull(pageText)).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) throw MoodleException.Input("This activity has no readable text exposed by Moodle.")
        return text
    }

    fun download(
        courseId: Long,
        moduleId: Long,
        fileNumber: Int,
        directory: String
    ): DownloadedFile {
        if (fileNumber <= 0) throw MoodleException.Input("File number must be positive.")
        val module = findModule(courseId, moduleId)
        val file = module.files.getOrNull(fileNumber - 1)
            ?: throw MoodleException.Input("Unknown file number. Run course $courseId to list files.")
        return access.withSession { token, _ -> files.download(token, file, directory) }
    }

    private fun findModule(courseId: Long, moduleId: Long): CourseModule {
        validateId(moduleId)
        return getContents(courseId).flatMap { it.modules }.firstOrNull { it.id == moduleId }
            ?: throw MoodleException.Input("Activity not found or unavailable in this course.")
    }

    private fun validateId(id: Long) {
        if (id <= 0) throw MoodleException.Input("IDs must be positive numbers.")
    }
}
