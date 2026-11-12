package moodle.domain.entities

/** A searchable title and enough metadata to open the course, read the activity, or select the file. */
sealed interface CourseSearchHit {
    data class CourseTitle(val course: Course) : CourseSearchHit

    data class Activity(val course: Course, val id: Long, val name: String) : CourseSearchHit

    data class File(val activity: Activity, val number: Int, val name: String) : CourseSearchHit
}
