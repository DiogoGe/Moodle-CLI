package moodle.application

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import moodle.domain.interfaces.TokenStore

class AccessService(private val access: MoodleAccess, private val siteUrl: String, private val tokens: TokenStore? = null) {
    private var token: AccessToken? = null
    private var userId: Long? = null
    private var availableFunctions: Set<String> = emptySet()

    fun getPublicStatus(): PublicStatus = access.getPublicStatus()

    fun checkStorage() {
        tokens?.checkAvailable()
    }

    fun login(candidate: AccessToken): SiteInfo {
        val info = verify(candidate)
        tokens?.save(siteUrl, candidate)
        activate(candidate, info)
        return info
    }

    fun getAuthenticatedStatus(): SiteInfo? {
        if (token == null) return restore()
        return authenticated { current, _ -> verify(current) }
    }

    fun <T> withSession(action: (AccessToken, Long) -> T): T {
        if (token == null) restore()
        return authenticated(action)
    }

    fun getAvailableFunctions(): Set<String> = availableFunctions

    fun endSession() {
        token = null
        userId = null
        availableFunctions = emptySet()
    }

    fun logout() {
        endSession()
        tokens?.delete(siteUrl)
    }

    private fun restore(): SiteInfo? {
        val candidate = tokens?.load(siteUrl) ?: return null
        try {
            val info = verify(candidate)
            activate(candidate, info)
            return info
        } catch (error: MoodleException.Authentication) {
            logout()
            throw error
        }
    }

    private fun verify(candidate: AccessToken): SiteInfo {
        val info = access.getSiteInfo(candidate)
        if (info.siteUrl.trimEnd('/') != siteUrl.trimEnd('/')) {
            throw MoodleException.Authentication("error.session.siteMismatch")
        }
        return info
    }

    private fun activate(candidate: AccessToken, info: SiteInfo) {
        token = candidate
        userId = info.userId
        availableFunctions = info.functions.toSet()
    }

    private fun <T> authenticated(action: (AccessToken, Long) -> T): T {
        val current = token ?: throw MoodleException.Authentication("error.session.required")
        val user = userId ?: throw MoodleException.Authentication("error.session.required")
        try {
            return action(current, user)
        } catch (error: MoodleException.Authentication) {
            logout()
            throw error
        }
    }
}
