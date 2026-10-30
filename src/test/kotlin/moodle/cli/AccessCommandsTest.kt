package moodle.cli

import moodle.application.AccessService
import moodle.application.BrowserLoginService
import moodle.domain.AccessToken
import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.MoodleAccess
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccessCommandsTest {
    @Test
    fun `login without a console fails clearly before calling Moodle or opening a browser`() {
        var apiCalls = 0
        val access = object : MoodleAccess {
            override fun getPublicStatus(): PublicStatus {
                apiCalls++
                error("No API request expected")
            }

            override fun getSiteInfo(token: AccessToken): SiteInfo = error("No authenticated request expected")
        }
        val commands = AccessCommands(
            AccessService(access, "https://moodle.zhaw.ch/"),
            BrowserLoginService("https://moodle.zhaw.ch/", Clock.systemUTC()),
            loginBrowser = { error("No browser expected") },
            consoleProvider = { null }
        )

        for (command in listOf("login", "login-manual")) {
            val error = assertFailsWith<MoodleException.Authentication> { commands.run(command) }
            assertEquals("error.login.console", error.messageKey)
        }
        assertEquals(0, apiCalls)
    }
}
