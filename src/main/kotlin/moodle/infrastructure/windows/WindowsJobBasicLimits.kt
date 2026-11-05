package moodle.infrastructure.windows

import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR

@Structure.FieldOrder(
    "processTime", "jobTime", "flags", "minimumWorkingSet", "maximumWorkingSet",
    "activeLimit", "affinity", "priority", "scheduling"
)
internal class WindowsJobBasicLimits : Structure() {
    @JvmField var processTime = 0L

    @JvmField var jobTime = 0L

    @JvmField var flags = 0x2000

    // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    @JvmField var minimumWorkingSet = SIZE_T()

    @JvmField var maximumWorkingSet = SIZE_T()

    @JvmField var activeLimit = 0

    @JvmField var affinity = ULONG_PTR()

    @JvmField var priority = 0

    @JvmField var scheduling = 0
}
