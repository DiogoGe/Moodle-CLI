package moodle.domain.entities

/** Authentication is added only when making a request; resource URLs are never included in diagnostic output. */
data class CourseFile(
    val name: String,
    val url: String,
    val size: Long,
    val mimeType: String?
)
