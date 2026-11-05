@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package moodle.infrastructure.windows

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

internal interface WindowsProfileSecurity : StdCallLibrary {
    fun ConvertStringSecurityDescriptorToSecurityDescriptorW(
        text: WString,
        revision: Int,
        descriptor: PointerByReference,
        size: Pointer?
    ): Boolean
}
