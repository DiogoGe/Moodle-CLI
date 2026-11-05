package moodle.infrastructure.credentials

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.TokenStore

internal class WindowsCredentialTokenStore(
    private val service: String = "moodle-cli",
    nativeProvider: () -> WindowsCredentials = { Native.load("Advapi32", WindowsCredentials::class.java) },
    private val lastError: () -> Int = Native::getLastError
) : TokenStore {
    private val native by lazy(nativeProvider)

    override fun checkAvailable() {
        safely("error.credentials.unavailable") { native }
    }

    override fun load(siteUrl: String): AccessToken? = safely("error.credentials.read") {
        val result = PointerByReference()
        if (!native.CredReadW(target(siteUrl), GENERIC, 0, result)) {
            if (lastError() == NOT_FOUND) return@safely null
            throw MoodleException.Credentials("error.credentials.read")
        }
        val pointer = result.value ?: throw MoodleException.Credentials("error.credentials.invalid")
        try {
            val credential = WindowsCredential(pointer).apply { read() }
            readToken(credential)
        } finally {
            native.CredFree(pointer)
        }
    }

    override fun save(siteUrl: String, token: AccessToken) {
        safely("error.credentials.write") {
            val bytes = token.value.toByteArray(Charsets.UTF_8)
            try {
                Memory(bytes.size.toLong()).use { memory ->
                    try {
                        memory.write(0, bytes, 0, bytes.size)
                        val credential = WindowsCredential().apply {
                            targetName = target(siteUrl)
                            blobSize = bytes.size
                            blob = memory
                        }
                        if (!native.CredWriteW(credential, 0)) throw MoodleException.Credentials("error.credentials.write")
                    } finally {
                        memory.clear()
                    }
                }
            } finally {
                bytes.fill(0)
            }
        }
    }

    override fun delete(siteUrl: String) {
        safely("error.credentials.delete") {
            if (!native.CredDeleteW(target(siteUrl), GENERIC, 0) && lastError() != NOT_FOUND) {
                throw MoodleException.Credentials("error.credentials.delete")
            }
        }
    }

    private fun readToken(credential: WindowsCredential): AccessToken {
        val blob = credential.blob
        if (credential.type != GENERIC || credential.blobSize != TOKEN_BYTES || blob == null) {
            throw MoodleException.Credentials("error.credentials.invalid")
        }
        val bytes = blob.getByteArray(0, TOKEN_BYTES)
        try {
            return AccessToken(bytes.toString(Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            throw MoodleException.Credentials("error.credentials.invalid")
        } finally {
            bytes.fill(0)
            blob.clear(TOKEN_BYTES.toLong())
        }
    }

    private fun target(siteUrl: String): WString = WString("$service:${siteUrl.trimEnd('/')}")

    private fun <T> safely(key: String, action: () -> T): T = try {
        action()
    } catch (error: MoodleException) {
        throw error
    } catch (_: Exception) {
        throw MoodleException.Credentials(key)
    } catch (_: LinkageError) {
        throw MoodleException.Credentials("error.credentials.unavailable")
    }

    private companion object {
        const val GENERIC = 1
        const val NOT_FOUND = 1168
        const val TOKEN_BYTES = 32
    }
}
