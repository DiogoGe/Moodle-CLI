@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package moodle.infrastructure.credentials

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/** Minimal bindings for Apple's current SecItem API. */
internal interface MacSecurity : Library {
    fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int

    fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int

    fun SecItemUpdate(query: Pointer, attributes: Pointer): Int

    fun SecItemDelete(query: Pointer): Int
}
