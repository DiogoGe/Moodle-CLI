package moodle.cli

import moodle.application.BrowserLoginService
import moodle.cli.i18n.Messages
import moodle.domain.exceptions.MoodleException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginCallbackReaderTest {
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val service = BrowserLoginService("https://moodle.zhaw.ch/", clock)

    @Test
    fun `browser page URL explains the mistake and allows valid callback on second input`() {
        val attempt = service.begin()
        val hash = MessageDigest
            .getInstance("MD5")
            .digest((attempt.siteUrl + attempt.passport).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val tokenValue = "a".repeat(32)
        val payload = Base64.getEncoder().encodeToString("$hash:::$tokenValue".toByteArray())
        val wrongInput = (attempt.launchUrl.toString() + "&secret=never-echo-this").toCharArray()
        val correctInput = "moodlecli://token=$payload".toCharArray()
        val inputs = ArrayDeque(listOf(wrongInput, correctInput))
        val output = mutableListOf<String>()
        val reader = LoginCallbackReader(service, { inputs.removeFirstOrNull() }, output::add, Messages(Locale.ENGLISH))

        assertEquals(tokenValue, reader.read(attempt).value)
        assertTrue(output.any { "browser page address" in it })
        assertFalse(output.any { "never-echo-this" in it || tokenValue in it || attempt.passport in it })
        assertTrue(wrongInput.all { it == '\u0000' })
        assertTrue(correctInput.all { it == '\u0000' })
    }

    @Test
    fun `cancel stops retrying and clears the input buffer`() {
        val input = "cancel".toCharArray()
        val reader = LoginCallbackReader(service, { input }, {})

        val error = assertFailsWith<MoodleException.Authentication> { reader.read(service.begin()) }

        assertEquals("error.login.cancelled", error.messageKey)
        assertTrue(input.all { it == '\u0000' })
    }

    @Test
    fun `end of input cancels after an invalid callback`() {
        var calls = 0
        val reader = LoginCallbackReader(service, { if (calls++ == 0) "bad-link".toCharArray() else null }, {})

        assertFailsWith<MoodleException.Authentication> { reader.read(service.begin()) }
        assertEquals(2, calls)
    }

    @Test
    fun `an expired attempt ends instead of retrying forever`() {
        val attempt = service.begin()
        val later = BrowserLoginService("https://moodle.zhaw.ch/", Clock.fixed(now.plusSeconds(901), ZoneOffset.UTC))
        var calls = 0
        val reader = LoginCallbackReader(later, {
            calls++
            "bad-link".toCharArray()
        }, {})

        assertFailsWith<MoodleException.Authentication> { reader.read(attempt) }
        assertEquals(1, calls)
    }
}
