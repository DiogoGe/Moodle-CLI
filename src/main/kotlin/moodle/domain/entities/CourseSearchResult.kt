package moodle.domain.entities

data class CourseSearchResult(val items: List<CourseSearchHit>, val skippedCourses: Int)
