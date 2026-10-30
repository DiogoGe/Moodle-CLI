package moodle.domain

data class PublicStatus(
    val siteUrl: String,
    val siteName: String,
    val webServicesEnabled: Boolean,
    val mobileServiceEnabled: Boolean,
    val browserLogin: Boolean,
    val maintenance: Boolean
)
