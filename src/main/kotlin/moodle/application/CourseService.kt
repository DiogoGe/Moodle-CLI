package moodle.application

import moodle.domain.entities.Course
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.CourseSearchResult
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

    fun search(query: String): CourseSearchResult {
        val search = CourseSearch(query)
        return access.withSession { token, userId ->
            val hits = mutableListOf<CourseSearchHit>()
            var skipped = 0
            for (course in repository.getCourses(token, userId).sortedBy { it.id }) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                search.courseTitle(course)?.let(hits::add)
                val sections = try {
                    repository.getContents(token, course.id)
                } catch (_: MoodleException.Permission) {
                    skipped++
                    continue
                }
                hits.addAll(search.activities(course, sections))
            }
            CourseSearchResult(hits, skipped)
        }
    }

    fun readText(courseId: Long, moduleId: Long): String {
        val module = findModule(courseId, moduleId)
        val inline = listOf(module.description) + module.texts
        val page = module.files.firstOrNull { module.type == "page" && it.name == "index.html" }
        val pageText = page?.let { file -> access.withSession { token, _ -> files.readText(token, file) } }
        val text = (inline + listOfNotNull(pageText)).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) throw MoodleException.Input("error.course.noText")
        return text
    }

    fun download(
        courseId: Long,
        moduleId: Long,
        fileNumber: Int,
        directory: String
    ): DownloadedFile {
        if (fileNumber <= 0) throw MoodleException.Input("error.input.fileNumber")
        val module = findModule(courseId, moduleId)
        val file = module.files.getOrNull(fileNumber - 1)
            ?: throw MoodleException.Input("error.course.fileNumber", courseId)
        return access.withSession { token, _ -> files.download(token, file, directory) }
    }

    private fun findModule(courseId: Long, moduleId: Long): CourseModule {
        validateId(moduleId)
        return getContents(courseId)
            .asSequence()
            .flatMap { it.modules }
            .firstOrNull { it.id == moduleId }
            ?: throw MoodleException.Input("error.course.module")
    }

    private fun validateId(id: Long) {
        if (id <= 0) throw MoodleException.Input("error.input.id")
    }
}
