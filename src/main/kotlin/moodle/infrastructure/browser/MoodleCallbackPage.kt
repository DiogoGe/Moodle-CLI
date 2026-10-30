package moodle.infrastructure.browser

import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** Inspect only the expected Moodle launch page and its final link, never login forms or cookies. */
internal class MoodleCallbackPage(launchUrl: URI) {
    private val origin = JsonPrimitive(URI(launchUrl.scheme, launchUrl.rawAuthority, null, null, null).toString())
    private val path = JsonPrimitive(launchUrl.path)
    private val passport = JsonPrimitive(
        launchUrl.rawQuery
            .split('&')
            .single { it.startsWith("passport=") }
            .substringAfter('=')
    )
    private val guard =
        "window.top === window && location.origin === $origin && location.pathname === $path && " +
            "new URLSearchParams(location.search).get('passport') === $passport"

    // Prevent the final link from handing the secret to the registered ZHAW app.
    val preventAppLaunch: String =
        """
        document.addEventListener('click', event => {
            if (!($guard)) return;
            const anchor = event.target instanceof Element ? event.target.closest('a#launchapp') : null;
            if (anchor) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }
        }, true);
        """.trimIndent()

    val readCallback: String =
        """
        (() => {
            if (!($guard)) return null;
            const link = document.querySelector('a#launchapp');
            const value = link ? link.getAttribute('href') : null;
            return value && /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/token=/.test(value) ? value : null;
        })()
        """.trimIndent()
}
