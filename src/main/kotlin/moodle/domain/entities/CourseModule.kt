package moodle.domain.entities

data class CourseModule(
    val id: Long,
    val name: String,
    val type: String,
    val description: String,
    val texts: List<String>,
    val files: List<CourseFile>,
    val noViewLink: Boolean = false
)
