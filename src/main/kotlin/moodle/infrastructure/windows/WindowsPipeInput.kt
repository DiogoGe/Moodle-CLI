package moodle.infrastructure.windows

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import java.io.IOException
import java.io.InputStream

internal class WindowsPipeInput(private val handle: WindowsHandle, private val kernel: WindowsKernel) : InputStream() {
    override fun read(): Int {
        val bytes = ByteArray(1)
        return if (read(bytes, 0, 1) == -1) -1 else bytes[0].toInt() and 0xff
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
        if (length == 0) return 0
        if (handle.isClosed) return -1
        val buffer = ByteArray(minOf(length, 65536))
        val count = IntByReference()
        try {
            if (!kernel.ReadFile(handle.pointer, buffer, buffer.size, count, null)) {
                if (Native.getLastError() in setOf(109, 232, 995)) return -1
                throw IOException("Browser pipe read failed")
            }
            if (count.value == 0) return -1
            buffer.copyInto(bytes, offset, 0, count.value)
            return count.value
        } finally {
            buffer.fill(0)
        }
    }

    override fun close() = handle.close()
}
