package moodle.domain.interfaces

import java.net.URI

/** Returns a callback to the caller without logging or persisting it. */
fun interface LoginBrowser {
    fun receiveCallback(launchUrl: URI): String
}
