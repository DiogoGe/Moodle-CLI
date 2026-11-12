package moodle.application

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import moodle.domain.interfaces.TokenStore

class AccessService(private val access: MoodleAccess, private val siteUrl: String, private val tokens: TokenStore? = null) {
    private val sessionLock = Any()
    private var session: AuthenticatedSession? = null
    private var generation = 0L

    fun getPublicStatus(): PublicStatus = access.getPublicStatus()

    fun checkStorage() = synchronized(sessionLock) {
        tokens?.checkAvailable()
        Unit
    }

    fun login(candidate: AccessToken): SiteInfo = synchronized(sessionLock) {
        val info = verify(candidate)
        tokens?.save(siteUrl, candidate)
        activate(candidate, info)
        info
    }

    fun getAuthenticatedStatus(): SiteInfo? {
        val current = synchronized(sessionLock) {
            session ?: return restore()?.info
        }
        return authenticated(current) {
            val info = verify(current.token)
            synchronized(sessionLock) {
                if (session?.generation == current.generation) {
                    session = AuthenticatedSession(current.generation, current.token, info)
                }
            }
            info
        }
    }

    fun <T> withSession(action: (AccessToken, Long) -> T): T {
        val current = synchronized(sessionLock) {
            session ?: restore() ?: throw MoodleException.Authentication("error.session.required")
        }
        return authenticated(current) { action(current.token, current.info.userId) }
    }

    fun getAvailableFunctions(): Set<String> = synchronized(sessionLock) {
        session
            ?.info
            ?.functions
            .orEmpty()
            .toSet()
    }

    fun endSession() = synchronized(sessionLock) {
        session = null
    }

    fun logout() = synchronized(sessionLock) {
        session = null
        tokens?.delete(siteUrl)
        Unit
    }

    // Called under sessionLock so concurrent first requests restore and verify a saved login only once.
    private fun restore(): AuthenticatedSession? {
        val candidate = tokens?.load(siteUrl) ?: return null
        try {
            activate(candidate, verify(candidate))
            return session
        } catch (error: MoodleException.Authentication) {
            invalidate(null, error)
        }
    }

    private fun invalidate(current: AuthenticatedSession?, error: MoodleException.Authentication): Nothing {
        synchronized(sessionLock) {
            if (session?.generation == current?.generation) {
                try {
                    logout()
                } catch (cleanupError: MoodleException.Credentials) {
                    error.addSuppressed(cleanupError)
                }
            }
        }
        throw error
    }

    private fun verify(candidate: AccessToken): SiteInfo {
        val info = access.getSiteInfo(candidate)
        if (info.siteUrl.trimEnd('/') != siteUrl.trimEnd('/')) {
            throw MoodleException.Authentication("error.session.siteMismatch")
        }
        return info.copy(functions = info.functions.toSet())
    }

    private fun activate(candidate: AccessToken, info: SiteInfo) {
        session = AuthenticatedSession(++generation, candidate, info)
    }

    private fun <T> authenticated(current: AuthenticatedSession, action: () -> T): T {
        try {
            return action()
        } catch (error: MoodleException.Authentication) {
            invalidate(current, error)
        }
    }
}
