package moodle.cli

import moodle.application.BrowserLoginService
import moodle.application.LoginAttempt
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException

internal class LoginCallbackReader(
    private val browserLogin: BrowserLoginService,
    private val readSecret: () -> CharArray?,
    private val output: (String) -> Unit
) {
    fun read(attempt: LoginAttempt): AccessToken {
        while (true) {
            val input = readSecret() ?: throw MoodleException.Authentication("Login cancelled.")
            try {
                val value = String(input).trim()
                if (value.equals("cancel", ignoreCase = true)) {
                    throw MoodleException.Authentication("Login cancelled.")
                }
                return browserLogin.complete(attempt, value)
            } catch (error: MoodleException.InvalidCallback) {
                output(error.message ?: "Invalid app link.")
                output("Try again with the app launch link from the same login page, or enter cancel.")
            } finally {
                input.fill('\u0000')
            }
        }
    }
}
