package moodle.infrastructure.credentials

import moodle.domain.interfaces.TokenStore

internal fun platformTokenStore(osName: String = System.getProperty("os.name")): TokenStore = when {
    osName.startsWith("Windows", ignoreCase = true) -> WindowsCredentialTokenStore()
    else -> MacKeychainTokenStore()
}
