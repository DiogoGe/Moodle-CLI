package moodle.application

import moodle.domain.entities.Course
import moodle.domain.entities.CourseModule
import moodle.domain.entities.CourseSearchHit
import moodle.domain.entities.CourseSection
import moodle.domain.exceptions.MoodleException

internal class CourseSearch(query: String) {
    private val terms: List<String>

    init {
        if (query.length > MAX_QUERY_LENGTH) throw MoodleException.Input("error.search.query", MAX_QUERY_LENGTH)
        terms = separators.split(query).filter { it.isNotEmpty() }
        if (terms.isEmpty()) throw MoodleException.Input("error.search.query", MAX_QUERY_LENGTH)
    }

    fun courseTitle(course: Course): CourseSearchHit.CourseTitle? =
        if (matches(course.name)) CourseSearchHit.CourseTitle(course) else null

    fun activities(course: Course, sections: List<CourseSection>): Sequence<CourseSearchHit> = sections
        .asSequence()
        .flatMap { it.modules }
        .flatMap { activityHits(course, it) }

    private fun activityHits(course: Course, module: CourseModule): Sequence<CourseSearchHit> = sequence {
        val activity = CourseSearchHit.Activity(course, module.id, module.name)
        if (matches(module.name)) yield(activity)
        module.files.forEachIndexed { index, file ->
            if (matches(file.name)) yield(CourseSearchHit.File(activity, index + 1, file.name))
        }
    }

    private fun matches(name: String): Boolean {
        val normalized = separators.replace(name, " ")
        return terms.all { normalized.contains(it, ignoreCase = true) }
    }

    companion object {
        const val MAX_QUERY_LENGTH = 120
        private val separators = Regex("[\\s\\p{Z}_.-]+")
    }
}
