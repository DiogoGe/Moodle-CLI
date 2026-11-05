package moodle.infrastructure.windows

import com.sun.jna.ptr.IntByReference
import java.io.IOException
import java.io.OutputStream

internal class WindowsPipeOutput(private val handle: WindowsHandle, private val kernel: WindowsKernel) : OutputStream() {
    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
        if (handle.isClosed) throw IOException("Browser pipe closed")
        var position = offset
        while (position < offset + length) {
            val buffer = bytes.copyOfRange(position, minOf(position + 65536, offset + length))
            try {
                val written = IntByReference()
                if (!kernel.WriteFile(handle.pointer, buffer, buffer.size, written, null) || written.value <= 0) {
                    throw IOException("Browser pipe write failed")
                }
                position += written.value
            } finally {
                buffer.fill(0)
            }
        }
    }

    override fun close() = handle.close()
}
