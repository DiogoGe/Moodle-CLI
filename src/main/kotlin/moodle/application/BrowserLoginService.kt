package moodle.application

import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.LoginBrowser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

/** Implements Moodle's mobile launch protocol without handling the user's password. */
class BrowserLoginService(private val baseUrl: String, private val clock: Clock) {
    fun authenticate(browser: LoginBrowser): AccessToken {
        val attempt = begin()
        return complete(attempt, browser.receiveCallback(attempt.launchUrl))
    }

    fun begin(): LoginAttempt {
        val passport = ByteArray(32).also { SecureRandom().nextBytes(it) }.toHex()
        val url = "${baseUrl}admin/tool/mobile/launch.php" +
            "?service=moodle_mobile_app&passport=$passport&urlscheme=moodlecli&confirmed=1"
        return LoginAttempt(URI(url), baseUrl.trimEnd('/'), passport, clock.instant())
    }

    fun complete(attempt: LoginAttempt, callback: String): AccessToken {
        validateAttempt(attempt)
        val match = validateCallbackInput(callback)
        val parts = decodeCallback(match.groupValues[1])
        validateSignature(attempt, parts)
        val token = createToken(parts[1])
        attempt.used = true
        return token
    }

    private fun validateAttempt(attempt: LoginAttempt) {
        if (attempt.used || clock.instant() > attempt.startedAt.plus(Duration.ofMinutes(15))) {
            throw MoodleException.Authentication("Login attempt expired or already used. Start login again.")
        }
    }

    private fun validateCallbackInput(callback: String): MatchResult {
        val value = callback.trim()
        if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
            throw MoodleException.InvalidCallback(
                "That is the browser page address. On the final Moodle page, right-click the app launch link " +
                    "and choose Copy Link. Paste that app link here, not the address bar URL."
            )
        }
        return CALLBACK.matchEntire(value) ?: throw MoodleException.InvalidCallback(
            "Expected an app link such as moodlecli://token=... . Copy the app launch link from the Moodle page."
        )
    }

    private fun decodeCallback(payload: String): List<String> {
        try {
            // Form decoding would turn a Base64 '+' into a space unless it is protected first.
            val encoded = URLDecoder.decode(payload.replace("+", "%2B"), StandardCharsets.UTF_8)
            return String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8).split(":::")
        } catch (_: IllegalArgumentException) {
            throw MoodleException.InvalidCallback("Invalid login callback encoding.")
        }
    }

    private fun validateSignature(attempt: LoginAttempt, parts: List<String>) {
        val expected = createSiteHash(attempt)
        if (parts.size !in 2..3 || !MessageDigest.isEqual(expected.toByteArray(), parts[0].toByteArray())) {
            throw MoodleException.InvalidCallback("The callback does not belong to this site and login attempt.")
        }
    }

    // Moodle's mobile launch protocol requires MD5(wwwroot + passport); changing it breaks interoperability.
    // This correlates a callback with a random, expiring, single-use attempt; it is not a digital signature.
    // Protocol: https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/admin/tool/mobile/launch.php
    @Suppress("kotlin:S4790")
    private fun createSiteHash(attempt: LoginAttempt): String {
        val input = (attempt.siteUrl + attempt.passport).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("MD5").digest(input).toHex()
    }

    private fun createToken(value: String): AccessToken {
        try {
            return AccessToken(value)
        } catch (_: IllegalArgumentException) {
            throw MoodleException.InvalidCallback("The callback contains an invalid Moodle token.")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        val CALLBACK = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://token=([a-zA-Z0-9+/=%_-]{1,4096})")
    }
}
