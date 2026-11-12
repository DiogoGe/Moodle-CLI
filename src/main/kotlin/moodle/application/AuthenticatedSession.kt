package moodle.application

import moodle.domain.AccessToken
import moodle.domain.SiteInfo

/** Immutable snapshot; a generation identifies one login across status refreshes. */
internal class AuthenticatedSession(val generation: Long, val token: AccessToken, val info: SiteInfo) {
    override fun toString(): String = "AuthenticatedSession([redacted])"
}
