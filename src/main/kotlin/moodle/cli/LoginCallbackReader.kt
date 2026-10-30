package moodle.cli

import moodle.application.BrowserLoginService
import moodle.application.LoginAttempt
import moodle.cli.i18n.Messages
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException

internal class LoginCallbackReader(
    private val browserLogin: BrowserLoginService,
    private val readSecret: () -> CharArray?,
    private val output: (String) -> Unit,
    private val messages: Messages = Messages()
) {
    fun read(attempt: LoginAttempt): AccessToken {
        while (true) {
            val input = readSecret() ?: throw MoodleException.Authentication("error.login.cancelled")
            try {
                val value = String(input).trim()
                if (value.equals("cancel", ignoreCase = true)) {
                    throw MoodleException.Authentication("error.login.cancelled")
                }
                return browserLogin.complete(attempt, value)
            } catch (error: MoodleException.InvalidCallback) {
                output(messages.error(error))
                output(messages.text("callback.retry"))
            } finally {
                input.fill('\u0000')
            }
        }
    }
}
