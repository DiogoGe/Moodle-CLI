@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package moodle.fixtures

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import moodle.infrastructure.credentials.WindowsCredential
import moodle.infrastructure.credentials.WindowsCredentials

internal class FakeWindowsCredentials : WindowsCredentials {
    var errorCode = 1168
    var fails = false
    var target = ""
    var freed = false
    var clearedBeforeFree = false
    var saved: String? = null
    var savedPersistence = 0
    private var blob: Memory? = null
    private var credential: WindowsCredential? = null

    override fun CredReadW(
        target: WString,
        type: Int,
        flags: Int,
        result: PointerByReference
    ): Boolean {
        this.target = target.toString()
        val value = saved ?: return false
        if (fails) return false
        val memory = Memory(value.length.toLong()).apply { write(0, value.toByteArray(), 0, value.length) }
        blob = memory
        credential = WindowsCredential().apply {
            blobSize = value.length
            blob = memory
            write()
        }
        result.value = credential?.pointer
        return true
    }

    override fun CredWriteW(credential: WindowsCredential, flags: Int): Boolean {
        target = credential.targetName.toString()
        savedPersistence = credential.persist
        if (fails) return false
        saved = credential.blob?.getByteArray(0, credential.blobSize)?.toString(Charsets.UTF_8)
        return true
    }

    override fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean {
        this.target = target.toString()
        if (fails || saved == null) return false
        saved = null
        return true
    }

    override fun CredFree(buffer: Pointer) {
        freed = true
        clearedBeforeFree = blob?.getByteArray(0, 32)?.all { it == 0.toByte() } == true
        blob?.close()
    }
}
