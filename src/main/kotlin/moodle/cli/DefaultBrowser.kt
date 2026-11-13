package moodle.cli

import java.awt.Desktop
import java.io.IOException
import java.net.URI

internal fun openInDefaultBrowser(url: URI): Boolean = try {
    if (!Desktop.isDesktopSupported()) {
        false
    } else {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(url)
            true
        } else {
            false
        }
    }
} catch (_: IOException) {
    false
} catch (_: UnsupportedOperationException) {
    false
} catch (_: SecurityException) {
    false
}
