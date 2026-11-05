package moodle.infrastructure.windows

import com.sun.jna.Memory
import java.time.Duration

internal class WindowsBrowserJob(private val kernel: WindowsKernel) : AutoCloseable {
    private val handle = WindowsHandle(checkNotNull(kernel.CreateJobObjectW(null, null)), kernel)

    init {
        try {
            val limits = WindowsJobExtendedLimits().apply { write() }
            check(
                kernel.SetInformationJobObject(handle.pointer, 9, limits.pointer, limits.size())
            ) { "Browser job isolation failed" }
        } catch (error: Exception) {
            handle.close()
            throw error
        }
    }

    fun attach(process: WindowsHandle) {
        check(kernel.AssignProcessToJobObject(handle.pointer, process.pointer)) { "Browser job assignment failed" }
    }

    override fun close() {
        if (handle.isClosed) return
        try {
            check(kernel.TerminateJobObject(handle.pointer, 0)) { "Browser shutdown failed" }
            awaitEmpty()
        } finally {
            handle.close()
        }
    }

    private fun awaitEmpty() {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        // JOBOBJECT_BASIC_ACCOUNTING_INFORMATION has four LARGE_INTEGERs and four DWORDs.
        Memory(48).use { information ->
            while (System.nanoTime() < deadline) {
                check(
                    kernel.QueryInformationJobObject(handle.pointer, 1, information, 48, null)
                ) { "Browser shutdown check failed" }
                if (information.getInt(40) == 0) return
                Thread.sleep(25)
            }
        }
        error("Browser processes did not exit")
    }
}
