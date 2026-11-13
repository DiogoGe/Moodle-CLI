package moodle.application

import moodle.domain.entities.Course
import moodle.domain.entities.CourseFile
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.CourseSection
import moodle.domain.exceptions.MoodleException

/** Tracks one bounded pass over Page resources in course and module order. */
internal class CoursePageSearch(
    private val offset: Int,
    private val search: CourseSearch,
    private val read: (CourseFile) -> String
) {
    private var seen = 0
    private var attempted = 0
    var skipped = 0
        private set

    val nextOffset: Int? get() = (offset + attempted).takeIf { it < seen }

    fun hits(course: Course, sections: List<CourseSection>): Sequence<CourseSearchHit.PageText> = sections
        .asSequence()
        .flatMap { it.modules }
        .mapNotNull { module ->
            val file = module.pageContentFile() ?: return@mapNotNull null
            val index = seen++
            if (index < offset || attempted == MAX_PAGES_PER_SEARCH) return@mapNotNull null
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            attempted++
            val snippet = readSnippet(file) ?: return@mapNotNull null
            CourseSearchHit.PageText(CourseSearchHit.Activity(course, module.id, module.name), snippet)
        }

    private fun readSnippet(file: CourseFile): String? = try {
        search.snippet(read(file))
    } catch (_: MoodleException.Download) {
        skipped++
        null
    } catch (_: MoodleException.Permission) {
        skipped++
        null
    }

    companion object {
        const val MAX_PAGES_PER_SEARCH = 10
    }
}

internal fun CourseModule.pageContentFile(): CourseFile? = if (type ==
    "page"
) {
    files.firstOrNull { it.name == "index.html" }
} else {
    null
}
