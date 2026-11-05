@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package moodle.infrastructure.credentials

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/** Explicit wide-character exports; credential bytes never pass through a shell. */
internal interface WindowsCredentials : StdCallLibrary {
    fun CredReadW(
        target: WString,
        type: Int,
        flags: Int,
        result: PointerByReference
    ): Boolean

    fun CredWriteW(credential: WindowsCredential, flags: Int): Boolean

    fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean

    fun CredFree(buffer: Pointer)
}
