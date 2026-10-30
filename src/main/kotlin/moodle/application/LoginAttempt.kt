package moodle.application

import java.net.URI
import java.time.Instant

class LoginAttempt internal constructor(
    val launchUrl: URI,
    internal val siteUrl: String,
    internal val passport: String,
    internal val startedAt: Instant
) {
    internal var used: Boolean = false

    override fun toString(): String = "LoginAttempt([redacted])"
}
