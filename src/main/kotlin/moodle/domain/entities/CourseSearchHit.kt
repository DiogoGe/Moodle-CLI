package moodle.domain.entities

/** A search hit and the metadata needed to navigate to its source. */
sealed interface CourseSearchHit {
    data class CourseTitle(val course: Course, val snippet: String? = null) : CourseSearchHit

    data class Activity(
        val course: Course,
        val id: Long,
        val name: String,
        val snippet: String? = null
    ) : CourseSearchHit

    data class File(val activity: Activity, val number: Int, val name: String) : CourseSearchHit

    data class SectionText(
        val course: Course,
        val sectionId: Long,
        val sectionName: String,
        val snippet: String
    ) : CourseSearchHit

    data class PageText(val activity: Activity, val snippet: String) : CourseSearchHit
}
