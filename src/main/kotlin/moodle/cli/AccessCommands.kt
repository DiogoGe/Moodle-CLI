package moodle.cli

import moodle.application.AccessService
import moodle.application.BrowserLoginService
import moodle.application.LoginAttempt
import moodle.cli.i18n.Messages
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.LoginBrowser
import java.awt.Desktop
import java.io.Console
import java.net.URI

class AccessCommands(
    private val service: AccessService,
    private val browserLogin: BrowserLoginService,
    private val loginBrowser: LoginBrowser,
    private val output: (String) -> Unit = ::println,
    private val consoleProvider: () -> Console? = System::console,
    private val courseCommand: (List<String>) -> Int = { throw MoodleException.FeatureUnavailable("courses") },
    private val messages: Messages = Messages()
) {
    fun run(command: String): Int = when (command) {
        "health" -> {
            health()
            0
        }
        "login" -> login(manual = false)
        "login-manual" -> login(manual = true)
        else -> 2
    }

    private fun health() {
        showPublicStatus(service.getPublicStatus())
        val info = service.getAuthenticatedStatus()
        if (info == null) {
            output(messages.text("health.anonymous"))
        } else {
            showSiteInfo(info)
        }
    }

    private fun showPublicStatus(status: PublicStatus) {
        output(messages.text("health.site", status.siteName, status.siteUrl))
        output(messages.text("health.webservices", messages.boolean(status.webServicesEnabled)))
        output(messages.text("health.mobile", messages.boolean(status.mobileServiceEnabled)))
        output(messages.text("health.browser", messages.boolean(status.browserLogin)))
        output(messages.text("health.maintenance", messages.boolean(status.maintenance)))
    }

    private fun login(manual: Boolean): Int {
        val console = requireConsole()
        checkLoginAvailability()
        if (manual) manualLogin(console) else automaticLogin()
        runSession(console)
        return 0
    }

    private fun requireConsole(): Console = consoleProvider() ?: throw MoodleException.Authentication(
        "error.login.console"
    )

    private fun checkLoginAvailability() {
        val status = service.getPublicStatus()
        if (!status.webServicesEnabled || !status.mobileServiceEnabled || status.maintenance) {
            throw MoodleException.Authentication("error.login.unavailable")
        }
        if (!status.browserLogin) {
            throw MoodleException.Authentication("error.login.configuration")
        }
    }

    private fun automaticLogin() {
        output(messages.text("login.automatic"))
        output(messages.text("login.capture"))
        val token = browserLogin.authenticate(loginBrowser)
        showSiteInfo(service.login(token))
    }

    private fun manualLogin(console: Console) {
        val attempt = browserLogin.begin()
        showManualLoginInstructions(attempt)
        openBrowser(attempt.launchUrl)
        val reader = LoginCallbackReader(
            browserLogin,
            { console.readPassword("%s", messages.text("login.prompt")) },
            output,
            messages
        )
        showSiteInfo(service.login(reader.read(attempt)))
    }

    private fun openBrowser(url: URI) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(url)
            }
        } catch (_: Exception) {
            output(messages.text("login.open"))
        }
    }

    private fun showManualLoginInstructions(attempt: LoginAttempt) {
        output(messages.text("login.manual"))
        output(messages.text("login.copy"))
        output(messages.text("login.address"))
        output(messages.text("login.cancelApp"))
        output(messages.text("login.paste"))
        output(messages.text("login.page", attempt.launchUrl))
    }

    private fun runSession(console: Console) {
        output(messages.text("session.commands"))
        try {
            while (runSessionCommand(console.readLine("moodle> ")?.trim())) {
                // Commands are handled individually; the session owns credential cleanup.
            }
        } finally {
            service.logout()
        }
    }

    private fun runSessionCommand(command: String?): Boolean {
        when (command) {
            "logout", "exit", null -> return false
            "" -> Unit
            else -> runSessionSafely(command)
        }
        return true
    }

    private fun runSessionSafely(command: String) {
        try {
            val args = SessionArguments.parse(command)
            if (args == listOf("health")) health() else courseCommand(args)
        } catch (error: MoodleException) {
            output(messages.error(error))
        }
    }

    private fun showSiteInfo(info: SiteInfo) {
        output(messages.text("login.authenticated", info.fullName, info.userId))
        output(messages.text("health.functions", info.functions.size))
        REQUIRED_FUNCTIONS.forEach { (label, function) ->
            val availability = messages.text(if (function in info.functions) "health.available" else "health.unavailable")
            output(messages.text("health.function", messages.text(label), availability))
        }
        output(messages.text("health.permissions"))
    }

    private companion object {
        val REQUIRED_FUNCTIONS = mapOf(
            "feature.courses" to "core_enrol_get_users_courses",
            "feature.contents" to "core_course_get_contents",
            "feature.assignments" to "mod_assign_get_assignments",
            "feature.submissions" to "mod_assign_get_submission_status"
        )
    }
}
