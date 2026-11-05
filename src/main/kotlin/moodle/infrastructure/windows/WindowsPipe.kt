package moodle.infrastructure.windows

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES
import com.sun.jna.ptr.PointerByReference

internal class WindowsPipe private constructor(val parent: WindowsHandle, val child: WindowsHandle) : AutoCloseable {
    override fun close() {
        parent.close()
        child.close()
    }

    companion object {
        fun create(kernel: WindowsKernel, childReads: Boolean): WindowsPipe {
            val read = PointerByReference()
            val write = PointerByReference()
            val security = SECURITY_ATTRIBUTES().apply { bInheritHandle = true }
            check(kernel.CreatePipe(read, write, security, 0)) { "Browser pipe creation failed" }
            val parent = WindowsHandle(if (childReads) write.value else read.value, kernel)
            val child = WindowsHandle(if (childReads) read.value else write.value, kernel)
            try {
                check(kernel.SetHandleInformation(parent.pointer, 1, 0)) { "Browser pipe isolation failed" }
                return WindowsPipe(parent, child)
            } catch (error: Exception) {
                parent.close()
                child.close()
                throw error
            }
        }

        fun serialize(handle: WindowsHandle): String = (Pointer.nativeValue(handle.pointer) and 0xffff_ffffL).toString()
    }
}
