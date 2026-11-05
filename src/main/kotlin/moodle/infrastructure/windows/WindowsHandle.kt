package moodle.infrastructure.windows

import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean

internal class WindowsHandle(val pointer: Pointer, private val kernel: WindowsKernel) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val isClosed: Boolean get() = closed.get()

    override fun close() {
        if (closed.compareAndSet(false, true)) kernel.CloseHandle(pointer)
    }
}
