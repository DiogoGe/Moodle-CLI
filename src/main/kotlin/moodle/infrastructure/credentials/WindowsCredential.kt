package moodle.infrastructure.credentials

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase.FILETIME

@Structure.FieldOrder(
    "flags", "type", "targetName", "comment", "lastWritten", "blobSize", "blob", "persist",
    "attributeCount", "attributes", "targetAlias", "userName"
)
internal class WindowsCredential(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var flags = 0

    @JvmField var type = 1

    @JvmField var targetName: WString? = null

    @JvmField var comment: WString? = null

    @JvmField var lastWritten = FILETIME()

    @JvmField var blobSize = 0

    @JvmField var blob: Pointer? = null

    @JvmField var persist = 2

    // CRED_PERSIST_LOCAL_MACHINE: current user, retained across logons, no roaming.
    @JvmField var attributeCount = 0

    @JvmField var attributes: Pointer? = null

    @JvmField var targetAlias: WString? = null

    @JvmField var userName: WString? = null
}
