package moodle.fixtures

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTRByReference
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.PointerByReference
import moodle.infrastructure.windows.WindowsKernel
import moodle.infrastructure.windows.WindowsStartupInfo
import java.lang.reflect.Proxy

internal class FakeWindowsKernel {
    val calls = mutableListOf<String>()
    val closed = mutableListOf<Long>()
    val protectedHandles = mutableListOf<Long>()
    var inheritedHandles = emptyList<Long>()
    var command = ""
    var failsAt = ""
    var creationFlags = 0
    var startupSize = 0
    var jobFlags = 0
    var consoleFlags = 0
    var consoleHandles = emptyList<Long>()
    private var nextHandle = 10L
    val native: WindowsKernel = Proxy.newProxyInstance(
        WindowsKernel::class.java.classLoader,
        arrayOf(WindowsKernel::class.java)
    ) { _, method, args ->
        calls.add(method.name)
        if (method.name == failsAt) return@newProxyInstance false
        invoke(method.name, args.orEmpty())
    } as WindowsKernel

    private fun invoke(method: String, args: Array<out Any?>): Any? = when (method) {
        "CreatePipe" -> {
            (args[0] as PointerByReference).value = Pointer(nextHandle++)
            (args[1] as PointerByReference).value = Pointer(nextHandle++)
            true
        }
        "SetHandleInformation" -> {
            protectedHandles.add(Pointer.nativeValue(args[0] as Pointer))
            true
        }
        "InitializeProcThreadAttributeList" -> {
            (args[3] as ULONG_PTRByReference).value = ULONG_PTR(128)
            args[0] != null
        }
        "UpdateProcThreadAttribute" -> {
            val memory = args[3] as Pointer
            val count = (args[4] as SIZE_T).toInt() / Native.POINTER_SIZE
            inheritedHandles = (0 until count).map { Pointer.nativeValue(memory.getPointer((it * Native.POINTER_SIZE).toLong())) }
            true
        }
        "CreateProcessW" -> createProcess(args)
        "CreateJobObjectW", "CreateFileW" -> Pointer(nextHandle++)
        "SetInformationJobObject" -> {
            jobFlags = (args[2] as Pointer).getInt(16)
            true
        }
        "QueryInformationJobObject" -> {
            (args[2] as Pointer).setInt(40, 0)
            true
        }
        "CloseHandle" -> {
            closed.add(Pointer.nativeValue(args[0] as Pointer))
            true
        }
        "WaitForSingleObject" -> 258
        "ResumeThread" -> 0
        "DeleteProcThreadAttributeList" -> null
        else -> true
    }

    private fun createProcess(args: Array<out Any?>): Boolean {
        command = (args[1] as CharArray).concatToString()
        creationFlags = args[5] as Int
        val startup = WindowsStartupInfo(args[8] as Pointer).apply { read() }.startup
        startupSize = startup.cb.toInt()
        consoleFlags = startup.dwFlags
        consoleHandles = listOf(startup.hStdInput, startup.hStdOutput, startup.hStdError).map { Pointer.nativeValue(it.pointer) }
        (args[9] as PROCESS_INFORMATION).apply {
            hProcess = HANDLE(Pointer(nextHandle++))
            hThread = HANDLE(Pointer(nextHandle++))
        }
        return true
    }
}
