package moodle.infrastructure.browser

import moodle.domain.exceptions.MoodleException
import java.nio.file.Files
import java.nio.file.Path

internal fun findChromeExecutable(
    osName: String = System.getProperty("os.name"),
    environment: (String) -> String? = System::getenv,
    exists: (Path) -> Boolean = { Files.isRegularFile(it) && Files.isExecutable(it) }
): Path {
    val candidates = if (osName.startsWith("Windows", ignoreCase = true)) {
        listOfNotNull(environment("PROGRAMFILES"), environment("PROGRAMFILES(X86)"), environment("LOCALAPPDATA"))
            .map { Path.of(it, "Google", "Chrome", "Application", "chrome.exe") }
    } else {
        listOf(
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/usr/bin/google-chrome",
            "/usr/bin/google-chrome-stable",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser"
        ).map(Path::of)
    }
    return candidates.firstOrNull(exists) ?: throw MoodleException.Authentication("error.browser.missing")
}
