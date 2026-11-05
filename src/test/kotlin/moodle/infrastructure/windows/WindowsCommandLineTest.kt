package moodle.infrastructure.windows

import moodle.domain.exceptions.MoodleException
import moodle.infrastructure.browser.findChromeExecutable
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowsCommandLineTest {
    @Test
    fun `Windows arguments preserve spaces quotes unicode and trailing backslashes without shell interpretation`() {
        val arguments = listOf("C:\\Program Files\\Chrome\\chrome.exe", "a\"b", "C:\\Profil ü\\", "", "a&b|c%USER%")
        val result = windowsCommandLine(arguments).concatToString()
        assertEquals(
            "\"C:\\Program Files\\Chrome\\chrome.exe\" \"a\\\"b\" \"C:\\Profil ü\\\\\" \"\" \"a&b|c%USER%\"\u0000",
            result
        )
        assertFailsWith<IllegalArgumentException> { windowsCommandLine(listOf("bad\u0000argument")) }
    }

    @Test
    fun `Chrome discovery checks system x86 and per user installations on Windows`() {
        val environment = mapOf("PROGRAMFILES" to "system", "PROGRAMFILES(X86)" to "x86", "LOCALAPPDATA" to "user")
        val expected = Path.of("user", "Google", "Chrome", "Application", "chrome.exe")
        assertEquals(expected, findChromeExecutable("Windows 11", environment::get) { it == expected })
        assertFailsWith<MoodleException.Authentication> { findChromeExecutable("Windows 11", environment::get) { false } }
    }
}
