package moodle.config

import java.net.URI

/** Non-secret connection settings. Credentials must be stored separately. */
data class MoodleConfig(val baseUrl: String = "https://moodle.zhaw.ch/") {
    init {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "Moodle URL must be an absolute HTTPS URL."
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Moodle URL must not contain credentials, a query, or a fragment."
        }
        require(baseUrl.endsWith('/')) { "Moodle base URL must end with '/'." }
    }
}
