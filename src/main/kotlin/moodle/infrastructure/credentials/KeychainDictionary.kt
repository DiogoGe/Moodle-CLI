package moodle.infrastructure.credentials

import com.sun.jna.NativeLong
import com.sun.jna.Pointer

/** Owns a retaining CFDictionary. Values created here are released after the dictionary retains them. */
internal class KeychainDictionary(private val native: MacKeychainBindings) : AutoCloseable {
    val pointer: Pointer = native.core.CFDictionaryCreateMutable(
        null,
        NativeLong(0),
        native.dictionaryCallbacks("kCFTypeDictionaryKeyCallBacks"),
        native.dictionaryCallbacks("kCFTypeDictionaryValueCallBacks")
    )
    private var closed = false

    fun putConstant(key: String, value: String) {
        put(key, native.securityConstant(value))
    }

    fun returnData() {
        put("kSecReturnData", native.foundationConstant("kCFBooleanTrue"))
    }

    fun putString(key: String, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val reference = native.core.CFStringCreateWithBytes(null, bytes, NativeLong(bytes.size.toLong()), UTF8_ENCODING, 0)
        try {
            put(key, reference)
        } finally {
            native.core.CFRelease(reference)
        }
    }

    fun putBytes(key: String, value: ByteArray) {
        val reference = native.core.CFDataCreate(null, value, NativeLong(value.size.toLong()))
        try {
            put(key, reference)
        } finally {
            native.core.CFRelease(reference)
        }
    }

    private fun put(key: String, value: Pointer) {
        native.core.CFDictionarySetValue(pointer, native.securityConstant(key), value)
    }

    override fun close() {
        if (!closed) {
            closed = true
            native.core.CFRelease(pointer)
        }
    }

    private companion object {
        const val UTF8_ENCODING = 0x08000100
    }
}
