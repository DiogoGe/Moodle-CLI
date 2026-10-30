package moodle.domain.interfaces

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo

interface MoodleAccess {
    fun getPublicStatus(): PublicStatus

    fun getSiteInfo(token: AccessToken): SiteInfo
}
