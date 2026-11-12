package moodle.application

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import moodle.fixtures.FakeTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentAccessServiceTest {
    private val site = "https://moodle.example"
    private val token = AccessToken("a".repeat(32))
    private val store = FakeTokenStore()
    private var validations = 0
    private var responseSite = site
    private var expired = false
    private var functions = setOf("mod_quiz_get_quizzes_by_courses")
    private var disconnected = false
    private val backend = object : MoodleAccess {
        override fun getPublicStatus(): PublicStatus = error("Not used")

        override fun getSiteInfo(token: AccessToken): SiteInfo {
            validations++
            if (expired) throw MoodleException.Authentication("error.session.expired")
            if (disconnected) throw MoodleException.Connection("error.api.connection")
            return SiteInfo(responseSite, "Moodle", 42, "User", functions)
        }
    }

    @Test
    fun `verified login persists once and another process restores and validates it before use`() {
        val first = service()
        first.login(token)
        assertEquals(1, store.writes)
        first.endSession()
        assertEquals(token, store.values[site])
        val second = service()
        assertEquals(
            42L,
            second.withSession { candidate, user ->
                assertEquals(token, candidate)
                user
            }
        )
        assertEquals(2, validations)
        assertEquals(setOf("mod_quiz_get_quizzes_by_courses"), second.getAvailableFunctions())
        assertEquals(1, store.writes)
    }

    @Test
    fun `wrong-site candidates are never saved and missing credentials remain signed out`() {
        responseSite = "https://untrusted.example"
        assertFailsWith<MoodleException.Authentication> { service().login(token) }
        assertTrue(store.values.isEmpty())
        assertEquals(0, store.writes)
        assertNull(service().getAuthenticatedStatus())
    }

    @Test
    fun `exit keeps credentials while logout removes them without contacting Moodle`() {
        val access = service()
        access.login(token)
        access.endSession()
        assertEquals(token, store.values[site])
        val callsBeforeLogout = validations
        service().logout()
        assertTrue(store.values.isEmpty())
        assertEquals(callsBeforeLogout, validations)
        assertEquals(1, store.deletes)
    }

    @Test
    fun `expired persisted credentials are deleted but network failures preserve them`() {
        store.values[site] = token
        disconnected = true
        assertFailsWith<MoodleException.Connection> { service().withSession { _, _ -> Unit } }
        assertEquals(token, store.values[site])
        assertEquals(0, store.deletes)
        disconnected = false
        expired = true
        assertFailsWith<MoodleException.Authentication> { service().withSession { _, _ -> Unit } }
        assertTrue(store.values.isEmpty())
        assertEquals(1, store.deletes)
    }

    @Test
    fun `a stored token is never sent to feature endpoints until site validation succeeds`() {
        store.values[site] = token
        responseSite = "https://other.example"
        var featureCalls = 0
        assertFailsWith<MoodleException.Authentication> {
            service().withSession { _, _ -> featureCalls++ }
        }
        assertEquals(0, featureCalls)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `locked or denied storage never silently falls back and logout clears process state on deletion failure`() {
        store.rejectWrites = true
        val access = service()
        assertFailsWith<MoodleException.Credentials> { access.login(token) }
        assertNull(access.getAuthenticatedStatus())
        store.rejectWrites = false
        access.login(token)
        store.rejectDeletes = true
        assertFailsWith<MoodleException.Credentials> { access.logout() }
        assertEquals(emptySet(), access.getAvailableFunctions())
        store.rejectReads = true
        assertFailsWith<MoodleException.Credentials> { access.withSession { _, _ -> Unit } }
    }

    @Test
    fun `memory-only access never reads or overwrites a stored login`() {
        store.values[site] = token
        val access = AccessService(backend, site)
        assertFailsWith<MoodleException.Authentication> { access.withSession { _, _ -> Unit } }
        access.login(AccessToken("b".repeat(32)))
        access.logout()
        assertEquals(token, store.values[site])
        assertEquals(0, store.deletes)
        assertEquals(0, store.writes)
    }

    @Test
    fun `health refreshes the capabilities of an existing session`() {
        val access = service()
        access.login(token)
        functions = setOf("core_enrol_get_users_courses")
        access.getAuthenticatedStatus()
        assertEquals(functions, access.getAvailableFunctions())
        assertEquals(1, store.writes)
    }

    @Test
    fun `expired authentication remains primary when deleting saved credentials fails`() {
        val access = service()
        access.login(token)
        store.rejectDeletes = true
        expired = true
        val error = assertFailsWith<MoodleException.Authentication> { access.getAuthenticatedStatus() }
        assertEquals("error.session.expired", error.messageKey)
        assertEquals("error.credentials.delete", (error.suppressed.single() as MoodleException).messageKey)
        assertTrue(access.getAvailableFunctions().isEmpty())

        var featureCalled = false
        assertFailsWith<MoodleException.Authentication> { access.withSession { _, _ -> featureCalled = true } }
        assertEquals(false, featureCalled)
    }

    private fun service(): AccessService = AccessService(backend, site, store)
}
