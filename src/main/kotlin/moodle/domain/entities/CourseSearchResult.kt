package moodle.domain.entities

data class CourseSearchResult(
    val items: List<CourseSearchHit>,
    val skippedCourses: Int,
    val skippedPages: Int = 0,
    val nextPageOffset: Int? = null
)
