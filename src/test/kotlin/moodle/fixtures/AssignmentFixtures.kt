package moodle.fixtures

import moodle.application.AccessService
import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.interfaces.MoodleAccess

fun assignmentAccess(): AccessService {
    val site = "https://moodle.example"
    val adapter = object : MoodleAccess {
        override fun getPublicStatus(): PublicStatus = error("Not needed")

        override fun getSiteInfo(token: AccessToken): SiteInfo = SiteInfo(site, "Moodle", 42, "Student", emptySet())
    }
    return AccessService(adapter, site).also { it.login(AccessToken("a".repeat(32))) }
}
