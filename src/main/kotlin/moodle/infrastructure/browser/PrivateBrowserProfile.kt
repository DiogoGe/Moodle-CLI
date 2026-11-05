package moodle.infrastructure.browser

import moodle.infrastructure.windows.createWindowsPrivateProfile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

internal fun createPrivateBrowserProfile(parent: Path?): Path {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return createWindowsPrivateProfile(parent)
    val permissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    return if (parent == null) {
        Files.createTempDirectory("moodle-cli-login-", permissions)
    } else {
        Files.createTempDirectory(parent, "moodle-cli-login-", permissions)
    }
}
