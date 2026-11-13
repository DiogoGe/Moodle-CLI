package moodle.domain.entities

data class CourseSection(
    val id: Long,
    val name: String,
    val description: String,
    val modules: List<CourseModule>,
    val userVisible: Boolean = true
)
