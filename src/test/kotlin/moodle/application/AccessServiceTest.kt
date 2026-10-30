package moodle.application

import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccessServiceTest {
    private val site = "https://moodle.zhaw.ch"
    private val info = SiteInfo(site, "Test Moodle", 42, "Test User", setOf("core_enrol_get_users_courses"))
    private val token = AccessToken("a".repeat(32))

    @Test
    fun `login verifies candidate before session can be used and logout clears it`() {
        val access = FakeAccess(info)
        val service = AccessService(access, site)
        assertNull(service.getAuthenticatedStatus())
        assertEquals(info, service.login(token))
        assertEquals(info, service.getAuthenticatedStatus())
        service.logout()
        assertNull(service.getAuthenticatedStatus())
        assertEquals(2, access.calls)
    }

    @Test
    fun `a token returning another site is not retained`() {
        val service = AccessService(FakeAccess(info.copy(siteUrl = "https://other.example")), site)
        assertFailsWith<MoodleException.Authentication> { service.login(token) }
        assertNull(service.getAuthenticatedStatus())
    }

    @Test
    fun `expired credentials are removed from the session`() {
        val access = FakeAccess(info)
        val service = AccessService(access, site)
        service.login(token)
        access.reject = true
        assertFailsWith<MoodleException.Authentication> { service.getAuthenticatedStatus() }
        assertNull(service.getAuthenticatedStatus())
    }

    private class FakeAccess(private val info: SiteInfo) : MoodleAccess {
        var calls = 0
        var reject = false

        override fun getPublicStatus(): PublicStatus = error("Not used in this test")

        override fun getSiteInfo(token: AccessToken): SiteInfo {
            calls++
            if (reject) throw MoodleException.Authentication("Expired token")
            return info
        }
    }
}
