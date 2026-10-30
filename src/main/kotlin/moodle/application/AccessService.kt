package moodle.application

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess

class AccessService(private val access: MoodleAccess, private val siteUrl: String) {
    private var token: AccessToken? = null
    private var userId: Long? = null

    fun getPublicStatus(): PublicStatus = access.getPublicStatus()

    fun login(candidate: AccessToken): SiteInfo {
        val info = access.getSiteInfo(candidate)
        if (info.siteUrl.trimEnd('/') != siteUrl.trimEnd('/')) {
            throw MoodleException.Authentication("Moodle returned a different site. Login was not accepted.")
        }
        token = candidate
        userId = info.userId
        return info
    }

    fun getAuthenticatedStatus(): SiteInfo? = token?.let { candidate ->
        try {
            access.getSiteInfo(candidate)
        } catch (error: MoodleException.Authentication) {
            logout()
            throw error
        }
    }

    fun <T> withSession(action: (AccessToken, Long) -> T): T {
        val currentToken = token ?: throw MoodleException.Authentication("Sign in first using login.")
        val currentUser = userId ?: throw MoodleException.Authentication("Sign in first using login.")
        try {
            return action(currentToken, currentUser)
        } catch (error: MoodleException.Authentication) {
            logout()
            throw error
        }
    }

    fun logout() {
        token = null
        userId = null
    }
}
