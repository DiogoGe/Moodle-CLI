package moodle.application

import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserLoginServiceTest {
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val service = BrowserLoginService("https://moodle.zhaw.ch/", clock)
    private val tokenValue = "a".repeat(32)

    @Test
    fun `login URLs use independent random passports and the Moodle mobile service`() {
        val first = service.begin()
        val second = service.begin()
        assertTrue(first.launchUrl.toString().startsWith("https://moodle.zhaw.ch/admin/tool/mobile/launch.php?"))
        assertTrue(first.launchUrl.query.contains("service=moodle_mobile_app"))
        assertTrue(first.launchUrl.query.contains("confirmed=1"))
        assertFalse(first.passport == second.passport)
        assertFalse(first.toString().contains(first.passport))
    }

    @Test
    fun `valid callback returns only the web service token and is single use`() {
        val attempt = service.begin()
        val callback = callback(attempt)
        assertEquals(tokenValue, service.complete(attempt, callback).value)
        assertFailsWith<MoodleException.Authentication> { service.complete(attempt, callback) }
    }

    @Test
    fun `callback for another login attempt is rejected`() {
        val first = service.begin()
        assertFailsWith<MoodleException.InvalidCallback> { service.complete(service.begin(), callback(first)) }
    }

    @Test
    fun `automatic browser result must belong to the newly generated attempt`() {
        val oldCallback = callback(service.begin())
        assertFailsWith<MoodleException.InvalidCallback> {
            service.authenticate { oldCallback }
        }
    }

    @Test
    fun `callback for another site is rejected`() {
        val other = BrowserLoginService("https://other.example/", clock).begin()
        assertFailsWith<MoodleException.InvalidCallback> { service.complete(service.begin(), callback(other)) }
    }

    @Test
    fun `expired login attempts are rejected`() {
        val attempt = service.begin()
        val later = BrowserLoginService("https://moodle.zhaw.ch/", Clock.fixed(now.plusSeconds(901), ZoneOffset.UTC))
        assertFailsWith<MoodleException.Authentication> { later.complete(attempt, callback(attempt)) }
    }

    @Test
    fun `malformed callbacks never expose pasted credentials in errors`() {
        val attempt = service.begin()
        for (value in listOf("not-a-link", "moodlecli://token=bad", "moodlecli://token=" + "a".repeat(5000))) {
            val error = assertFailsWith<MoodleException.InvalidCallback> { service.complete(attempt, value) }
            assertFalse(error.message.orEmpty().contains(value))
        }
    }

    @Test
    fun `site forced scheme and percent encoded padding are accepted`() {
        val attempt = service.begin()
        val value = callback(attempt)
            .replace("moodlecli:", "zhawmoodle:")
            .replace("=", "%3D")
            .replace("token%3D", "token=")
        assertEquals(tokenValue, service.complete(attempt, value).value)
    }

    @Test
    fun `tokens are redacted and callback token formats are validated`() {
        assertFalse(AccessToken(tokenValue).toString().contains(tokenValue))
        val attempt = service.begin()
        assertFailsWith<MoodleException.InvalidCallback> {
            service.complete(attempt, callback(attempt, "not-a-valid-token"))
        }
    }

    private fun callback(attempt: LoginAttempt, token: String = tokenValue): String {
        val hash = MessageDigest
            .getInstance("MD5")
            .digest((attempt.siteUrl + attempt.passport).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val payload = "$hash:::$token:::unused-private-token"
        return "moodlecli://token=" + Base64.getEncoder().encodeToString(payload.toByteArray())
    }
}
