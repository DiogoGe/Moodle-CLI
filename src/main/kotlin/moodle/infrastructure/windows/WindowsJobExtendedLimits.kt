package moodle.infrastructure.windows

import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD.SIZE_T

@Structure.FieldOrder("basic", "ioCounters", "processMemory", "jobMemory", "peakProcessMemory", "peakJobMemory")
internal class WindowsJobExtendedLimits : Structure() {
    @JvmField var basic = WindowsJobBasicLimits()

    @JvmField var ioCounters = LongArray(6)

    @JvmField var processMemory = SIZE_T()

    @JvmField var jobMemory = SIZE_T()

    @JvmField var peakProcessMemory = SIZE_T()

    @JvmField var peakJobMemory = SIZE_T()
}
