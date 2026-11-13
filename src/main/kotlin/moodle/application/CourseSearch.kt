package moodle.application

import moodle.domain.entities.Course
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.CourseSection
import moodle.domain.exceptions.MoodleException

internal class CourseSearch(query: String, private val renderText: (String) -> String) {
    private val terms: List<String>

    init {
        if (query.length > MAX_QUERY_LENGTH) throw MoodleException.Input("error.search.query", MAX_QUERY_LENGTH)
        terms = separators.split(query).filter { it.isNotEmpty() }
        if (terms.isEmpty()) throw MoodleException.Input("error.search.query", MAX_QUERY_LENGTH)
    }

    fun courseHit(course: Course): CourseSearchHit.CourseTitle? {
        val excerpt = snippet(course.description)
        return if (matches(course.name) || excerpt != null) CourseSearchHit.CourseTitle(course, excerpt) else null
    }

    fun activities(course: Course, sections: List<CourseSection>): Sequence<CourseSearchHit> = sections
        .asSequence()
        .flatMap { sectionHits(course, it) }

    private fun sectionHits(course: Course, section: CourseSection): Sequence<CourseSearchHit> = sequence {
        snippet(section.description)?.let { yield(CourseSearchHit.SectionText(course, section.id, section.name, it)) }
        for (module in section.modules) yieldAll(activityHits(course, module))
    }

    private fun activityHits(course: Course, module: CourseModule): Sequence<CourseSearchHit> = sequence {
        val activity = CourseSearchHit.Activity(course, module.id, module.name)
        val excerpt = sequenceOf(module.description).plus(module.texts).firstNotNullOfOrNull(::snippet)
        if (matches(module.name) || excerpt != null) yield(activity.copy(snippet = excerpt))
        module.files.forEachIndexed { index, file ->
            if (matches(file.name)) yield(CourseSearchHit.File(activity, index + 1, file.name))
        }
    }

    fun snippet(html: String): String? {
        if (html.isBlank()) return null
        val text = whitespace.replace(renderText(html), " ")
        // Keep character positions aligned with the excerpt while treating filename separators as spaces.
        val normalized = text.replace('.', ' ').replace('_', ' ').replace('-', ' ')
        val positions = terms.map { normalized.indexOf(it, ignoreCase = true) }
        if (positions.any { it < 0 }) return null

        var start = (positions.min() - SNIPPET_CONTEXT).coerceAtLeast(0)
        if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start--
        var end = (start + SNIPPET_LENGTH).coerceAtMost(text.length)
        if (end < text.length && text[end].isLowSurrogate()) end--
        return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
    }

    private fun matches(name: String): Boolean {
        val normalized = separators.replace(name, " ")
        return terms.all { normalized.contains(it, ignoreCase = true) }
    }

    companion object {
        const val MAX_QUERY_LENGTH = 120
        private const val SNIPPET_CONTEXT = 50
        private const val SNIPPET_LENGTH = 200
        private val separators = Regex("[\\s\\p{Z}_.-]+")
        private val whitespace = Regex("[\\s\\p{Z}]+")
    }
}
