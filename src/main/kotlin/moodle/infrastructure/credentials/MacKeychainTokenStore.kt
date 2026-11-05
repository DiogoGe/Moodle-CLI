package moodle.infrastructure.credentials

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.TokenStore

/** Direct SecItem calls: no shell, process arguments, clipboard, or application-owned secret files. */
internal class MacKeychainTokenStore(private val service: String = "moodle-cli") : TokenStore {
    private val native by lazy { MacKeychainBindings() }

    override fun checkAvailable() {
        safely("error.credentials.unavailable") { native.security }
    }

    override fun load(siteUrl: String): AccessToken? = safely("error.credentials.read") {
        query(siteUrl) { query ->
            query.returnData()
            val result = PointerByReference()
            val status = native.security.SecItemCopyMatching(query.pointer, result)
            if (status == ITEM_NOT_FOUND) return@query null
            checkStatus(status, "error.credentials.read")
            val value = result.value ?: throw MoodleException.Credentials("error.credentials.invalid")
            try {
                readToken(value)
            } finally {
                native.core.CFRelease(value)
            }
        }
    }

    override fun save(siteUrl: String, token: AccessToken) {
        safely("error.credentials.write") {
            val bytes = token.value.toByteArray(Charsets.UTF_8)
            try {
                query(siteUrl) { query ->
                    KeychainDictionary(native).use { changes ->
                        changes.putBytes("kSecValueData", bytes)
                        val status = native.security.SecItemUpdate(query.pointer, changes.pointer)
                        if (status == ITEM_NOT_FOUND) {
                            query.putBytes("kSecValueData", bytes)
                            checkStatus(native.security.SecItemAdd(query.pointer, null), "error.credentials.write")
                        } else {
                            checkStatus(status, "error.credentials.write")
                        }
                    }
                }
            } finally {
                bytes.fill(0)
            }
        }
    }

    override fun delete(siteUrl: String) {
        safely("error.credentials.delete") {
            query(siteUrl) { query ->
                val status = native.security.SecItemDelete(query.pointer)
                if (status != ITEM_NOT_FOUND) checkStatus(status, "error.credentials.delete")
            }
        }
    }

    private fun readToken(value: Pointer): AccessToken {
        val core = native.core
        if (core.CFGetTypeID(value) != core.CFDataGetTypeID() || core.CFDataGetLength(value).toLong() != TOKEN_BYTES) {
            throw MoodleException.Credentials("error.credentials.invalid")
        }
        val bytes = core.CFDataGetBytePtr(value).getByteArray(0, TOKEN_BYTES.toInt())
        try {
            return AccessToken(bytes.toString(Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            throw MoodleException.Credentials("error.credentials.invalid")
        } finally {
            bytes.fill(0)
        }
    }

    private fun <T> query(siteUrl: String, action: (KeychainDictionary) -> T): T = KeychainDictionary(native).use { query ->
        query.putConstant("kSecClass", "kSecClassGenericPassword")
        query.putString("kSecAttrService", service)
        query.putString("kSecAttrAccount", siteUrl.trimEnd('/'))
        action(query)
    }

    private fun checkStatus(status: Int, key: String) {
        if (status != 0) throw MoodleException.Credentials(key)
    }

    private fun <T> safely(key: String, action: () -> T): T {
        try {
            return action()
        } catch (error: MoodleException) {
            throw error
        } catch (_: Exception) {
            throw MoodleException.Credentials(key)
        } catch (_: LinkageError) {
            throw MoodleException.Credentials("error.credentials.unavailable")
        }
    }

    private companion object {
        const val ITEM_NOT_FOUND = -25300
        const val TOKEN_BYTES = 32L
    }
}
