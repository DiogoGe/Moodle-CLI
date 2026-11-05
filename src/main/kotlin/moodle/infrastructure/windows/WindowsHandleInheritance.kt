package moodle.infrastructure.windows

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTRByReference

/** Only explicitly listed protocol and null-device handles may cross the process boundary. */
internal class WindowsHandleInheritance(private val kernel: WindowsKernel, handles: List<WindowsHandle>) : AutoCloseable {
    private val size = ULONG_PTRByReference()
    private val values = Memory((handles.size * Native.POINTER_SIZE).toLong())
    val attributes: Memory

    init {
        kernel.InitializeProcThreadAttributeList(null, 1, 0, size)
        require(size.value.toLong() in 1..65536) { "Browser handle list unavailable" }
        attributes = Memory(size.value.toLong())
        var initialized = false
        try {
            check(kernel.InitializeProcThreadAttributeList(attributes, 1, 0, size)) { "Browser handle list failed" }
            initialized = true
            handles.forEachIndexed { index, handle -> values.setPointer((index * Native.POINTER_SIZE).toLong(), handle.pointer) }
            check(
                kernel.UpdateProcThreadAttribute(attributes, 0, ULONG_PTR(0x20002), values, SIZE_T(values.size()), null, null)
            ) {
                "Browser handle inheritance failed"
            }
        } catch (error: Exception) {
            if (initialized) kernel.DeleteProcThreadAttributeList(attributes)
            attributes.close()
            values.close()
            throw error
        }
    }

    override fun close() {
        kernel.DeleteProcThreadAttributeList(attributes)
        attributes.close()
        values.close()
    }
}
