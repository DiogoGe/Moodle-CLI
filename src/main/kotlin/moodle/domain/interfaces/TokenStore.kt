package moodle.domain.interfaces

import moodle.domain.AccessToken

/** An OS credential store, keyed by the configured Moodle site. Never a plain-text configuration file. */
interface TokenStore {
    fun checkAvailable()

    fun load(siteUrl: String): AccessToken?

    fun save(siteUrl: String, token: AccessToken)

    fun delete(siteUrl: String)
}
