package moodle.application

import moodle.domain.entities.Course
import moodle.domain.entities.CourseDownloadOutcome
import moodle.domain.entities.CourseFile
import moodle.domain.entities.CourseFileSelection
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

    internal fun getModule(courseId: Long, moduleId: Long): CourseModule {
        validateId(moduleId)
        return findModule(getContents(courseId), moduleId)
    }

    fun readText(courseId: Long, moduleId: Long): String {
        val module = getModule(courseId, moduleId)
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
        val selection = CourseFileSelection(courseId, moduleId, fileNumber)
        validateSelection(selection)
        return access.withSession { token, _ ->
            val sections = repository.getContents(token, courseId)
            files.download(token, selectFile(sections, selection), directory)
        }
    }

    fun downloadMany(selections: List<CourseFileSelection>, directory: String): List<CourseDownloadOutcome> {
        if (selections.size !in 1..MAX_BATCH_FILES) throw MoodleException.Input("error.download.batchSize", MAX_BATCH_FILES)
        val selected = selections.distinct()
        selected.forEach(::validateSelection)
        val outcomes = mutableListOf<CourseDownloadOutcome>()
        var started = false
        try {
            access.withSession { token, _ ->
                started = true
                val contents = mutableMapOf<Long, Result<List<CourseSection>>>()
                for (selection in selected) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    outcomes += recordDownload(selection) {
                        val sections = contents
                            .getOrPut(selection.courseId) {
                                runCatching { repository.getContents(token, selection.courseId) }
                            }.getOrThrow()
                        files.download(token, selectFile(sections, selection), directory)
                    }
                }
            }
        } catch (error: MoodleException.Authentication) {
            if (!started) throw error
            outcomes += CourseDownloadOutcome.Failed(selected[outcomes.size], error)
            outcomes += selected.drop(outcomes.size).map { CourseDownloadOutcome.Skipped(it) }
        }
        return outcomes
    }

    private fun recordDownload(selection: CourseFileSelection, save: () -> DownloadedFile): CourseDownloadOutcome = try {
        CourseDownloadOutcome.Saved(selection, save())
    } catch (error: MoodleException) {
        when (error) {
            is MoodleException.Input, is MoodleException.Permission, is MoodleException.Download, is MoodleException.Connection ->
                CourseDownloadOutcome.Failed(selection, error)
            else -> throw error
        }
    }

    private fun selectFile(sections: List<CourseSection>, selection: CourseFileSelection): CourseFile =
        findModule(sections, selection.moduleId).files.getOrNull(selection.fileNumber - 1)
            ?: throw MoodleException.Input("error.course.fileNumber", selection.courseId)

    private fun findModule(sections: List<CourseSection>, moduleId: Long): CourseModule = sections
        .asSequence()
        .flatMap { it.modules }
        .firstOrNull { it.id == moduleId }
        ?: throw MoodleException.Input("error.course.module")

    private fun validateSelection(selection: CourseFileSelection) {
        if (selection.fileNumber <= 0) throw MoodleException.Input("error.input.fileNumber")
        validateId(selection.moduleId)
        validateId(selection.courseId)
    }

    private fun validateId(id: Long) {
        if (id <= 0) throw MoodleException.Input("error.input.id")
    }

    companion object {
        internal const val MAX_BATCH_FILES = 10
    }
}
