package moodle.infrastructure.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinNT.HANDLE
import moodle.infrastructure.browser.ChromeTransport
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

internal class WindowsChromeTransport private constructor(
    private val kernel: WindowsKernel,
    private val process: WindowsHandle,
    private val job: WindowsBrowserJob,
    private val incoming: WindowsPipe,
    private val outgoing: WindowsPipe
) : ChromeTransport {
    override val input: InputStream = BufferedInputStream(WindowsPipeInput(incoming.parent, kernel))
    override val output: OutputStream = WindowsPipeOutput(outgoing.parent, kernel)
    override val isAlive: Boolean get() = !process.isClosed && kernel.WaitForSingleObject(process.pointer, 0) == 258

    override fun close() {
        try {
            job.close()
        } finally {
            incoming.close()
            outgoing.close()
            process.close()
        }
    }

    companion object {
        fun start(
            arguments: List<String>,
            kernel: WindowsKernel = Native.load("kernel32", WindowsKernel::class.java)
        ): WindowsChromeTransport {
            val outgoing = WindowsPipe.create(kernel, childReads = true)
            var incoming: WindowsPipe? = null
            var job: WindowsBrowserJob? = null
            try {
                incoming = WindowsPipe.create(kernel, childReads = false)
                job = WindowsBrowserJob(kernel)
                val process = launch(kernel, arguments, outgoing, incoming, job)
                outgoing.child.close()
                incoming.child.close()
                return WindowsChromeTransport(kernel, process, job, incoming, outgoing)
            } catch (error: Throwable) {
                try {
                    job?.close()
                } finally {
                    incoming?.close()
                    outgoing.close()
                }
                throw error
            }
        }

        private fun launch(
            kernel: WindowsKernel,
            arguments: List<String>,
            outgoing: WindowsPipe,
            incoming: WindowsPipe,
            job: WindowsBrowserJob
        ): WindowsHandle = discardConsole(kernel).use { discard ->
            WindowsHandleInheritance(kernel, listOf(outgoing.child, incoming.child, discard)).use { inheritance ->
                launchIsolated(kernel, arguments, outgoing, incoming, job, startupInfo(inheritance, discard))
            }
        }

        private fun launchIsolated(
            kernel: WindowsKernel,
            arguments: List<String>,
            outgoing: WindowsPipe,
            incoming: WindowsPipe,
            job: WindowsBrowserJob,
            startup: WindowsStartupInfo
        ): WindowsHandle {
            val readHandle = WindowsPipe.serialize(outgoing.child)
            val writeHandle = WindowsPipe.serialize(incoming.child)
            val command = windowsCommandLine(arguments + "--remote-debugging-io-pipes=$readHandle,$writeHandle")
            val info = createSuspendedProcess(kernel, arguments.first(), command, startup)
            val process = WindowsHandle(info.hProcess.pointer, kernel)
            WindowsHandle(info.hThread.pointer, kernel).use { thread ->
                try {
                    job.attach(process)
                    check(kernel.ResumeThread(thread.pointer) != -1) { "Browser process start failed" }
                } catch (error: Exception) {
                    kernel.TerminateProcess(process.pointer, 1)
                    kernel.WaitForSingleObject(process.pointer, 3000)
                    process.close()
                    throw error
                }
            }
            return process
        }

        private fun discardConsole(kernel: WindowsKernel): WindowsHandle {
            val security = SECURITY_ATTRIBUTES().apply { bInheritHandle = true }
            val pointer = kernel.CreateFileW(WString("NUL"), 0xc0000000.toInt(), 3, security, 3, 0x80, null)
            check(pointer != null && Pointer.nativeValue(pointer) != -1L) { "Browser output isolation failed" }
            return WindowsHandle(pointer, kernel)
        }

        private fun startupInfo(inheritance: WindowsHandleInheritance, discard: WindowsHandle): WindowsStartupInfo =
            WindowsStartupInfo().apply {
                startup.cb = DWORD(size().toLong())
                startup.dwFlags = 0x100 // STARTF_USESTDHANDLES
                startup.hStdInput = HANDLE(discard.pointer)
                startup.hStdOutput = HANDLE(discard.pointer)
                startup.hStdError = HANDLE(discard.pointer)
                attributes = inheritance.attributes
                write()
            }

        private fun createSuspendedProcess(
            kernel: WindowsKernel,
            executable: String,
            command: CharArray,
            startup: WindowsStartupInfo
        ): PROCESS_INFORMATION {
            val info = PROCESS_INFORMATION()
            windowsBrowserEnvironment().use { environment ->
                try {
                    // EXTENDED_STARTUPINFO_PRESENT | CREATE_UNICODE_ENVIRONMENT | CREATE_SUSPENDED
                    val created = kernel.CreateProcessW(
                        WString(executable),
                        command,
                        null,
                        null,
                        true,
                        0x80404,
                        environment,
                        null,
                        startup.pointer,
                        info
                    )
                    check(created) { "Browser process creation failed" }
                } finally {
                    environment.clear()
                }
            }
            return info
        }
    }
}
