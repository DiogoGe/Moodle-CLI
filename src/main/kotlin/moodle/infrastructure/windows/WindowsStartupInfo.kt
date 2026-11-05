package moodle.infrastructure.windows

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinBase.STARTUPINFO

@Structure.FieldOrder("startup", "attributes")
internal class WindowsStartupInfo(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var startup = STARTUPINFO()

    @JvmField var attributes: Pointer? = null
}
