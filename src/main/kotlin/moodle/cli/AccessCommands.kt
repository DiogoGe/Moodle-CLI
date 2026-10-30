package moodle.cli

import moodle.application.AccessService
import moodle.application.BrowserLoginService
import moodle.application.LoginAttempt
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
    private val consoleProvider: () -> Console? = System::console
) {
    fun run(command: String): Int = when (command) {
        "doctor" -> {
            doctor()
            0
        }
        "login" -> login(manual = false)
        "login-manual" -> login(manual = true)
        else -> 2
    }

    private fun doctor() {
        showPublicStatus(service.getPublicStatus())
        val info = service.getAuthenticatedStatus()
        if (info == null) {
            output("Authentication not checked. Run login to start a process-local session.")
        } else {
            showSiteInfo(info)
        }
    }

    private fun showPublicStatus(status: PublicStatus) {
        output("Site: ${status.siteName} (${status.siteUrl})")
        output("Web services enabled: ${status.webServicesEnabled}")
        output("Mobile service enabled: ${status.mobileServiceEnabled}")
        output("Browser login configured: ${status.browserLogin}")
        output("Maintenance mode: ${status.maintenance}")
    }

    private fun login(manual: Boolean): Int {
        val console = requireConsole()
        checkLoginAvailability()
        if (manual) manualLogin(console) else automaticLogin()
        runSession(console)
        return 0
    }

    private fun requireConsole(): Console = consoleProvider() ?: throw MoodleException.Authentication(
        "Login requires a terminal with hidden input. Run the installed moodle launcher, not Gradle run."
    )

    private fun checkLoginAvailability() {
        val status = service.getPublicStatus()
        if (!status.webServicesEnabled || !status.mobileServiceEnabled || status.maintenance) {
            throw MoodleException.Authentication("Mobile web services are unavailable or Moodle is in maintenance.")
        }
        if (!status.browserLogin) {
            throw MoodleException.Authentication("This instance is not configured for the supported browser login.")
        }
    }

    private fun automaticLogin() {
        output("Sign in through SWITCH in the separate private Chrome window.")
        output("The final app link is received automatically; do not paste or share it.")
        val token = browserLogin.authenticate(loginBrowser)
        showSiteInfo(service.login(token))
    }

    private fun manualLogin(console: Console) {
        val attempt = browserLogin.begin()
        showManualLoginInstructions(attempt)
        openBrowser(attempt.launchUrl)
        val reader = LoginCallbackReader(
            browserLogin,
            { console.readPassword("App callback URL (hidden, or cancel): ") },
            output
        )
        showSiteInfo(service.login(reader.read(attempt)))
    }

    private fun openBrowser(url: URI) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(url)
            }
        } catch (_: Exception) {
            output("Open the login page above manually.")
        }
    }

    private fun showManualLoginInstructions(attempt: LoginAttempt) {
        output("Sign in through SWITCH in your browser. Passwords stay in the browser.")
        output("On the final Moodle page, right-click the app launch link and choose Copy Link.")
        output("Do not copy the https:// address from the browser's address bar.")
        output("If the browser asks to open an app, cancel that prompt and copy the link on the page.")
        output("Paste the app link at the hidden prompt below. Enter cancel to stop. Do not share the link in chat.")
        output("Login page: ${attempt.launchUrl}")
    }

    private fun runSession(console: Console) {
        output("Session is held in memory only. Commands: doctor, logout, exit.")
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
            "doctor" -> runDoctorSafely()
            "" -> Unit
            else -> output("Commands: doctor, logout, exit.")
        }
        return true
    }

    private fun runDoctorSafely() {
        try {
            doctor()
        } catch (error: MoodleException) {
            output(error.message ?: "Moodle check failed.")
        }
    }

    private fun showSiteInfo(info: SiteInfo) {
        output("Authenticated as ${info.fullName} (user ID ${info.userId}).")
        output("Available API functions: ${info.functions.size}")
        REQUIRED_FUNCTIONS.forEach { (label, function) ->
            val availability = if (function in info.functions) "available" else "not exposed by this token"
            output("$label: $availability")
        }
        output("Function exposure does not guarantee permission for every course or activity.")
    }

    private companion object {
        val REQUIRED_FUNCTIONS = mapOf(
            "Courses" to "core_enrol_get_users_courses",
            "Assignments" to "mod_assign_get_assignments",
            "Submission status" to "mod_assign_get_submission_status"
        )
    }
}
