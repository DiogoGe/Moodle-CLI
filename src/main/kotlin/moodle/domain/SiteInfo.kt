package moodle.domain

data class SiteInfo(
    val siteUrl: String,
    val siteName: String,
    val userId: Long,
    val fullName: String,
    val functions: Set<String>
)
