@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package moodle.infrastructure.windows

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTRByReference
import com.sun.jna.platform.win32.WinBase.PROCESS_INFORMATION
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

internal interface WindowsKernel : StdCallLibrary {
    fun CreateDirectoryW(path: WString, security: SECURITY_ATTRIBUTES): Boolean
    fun CreateFileW(
        path: WString,
        access: Int,
        sharing: Int,
        security: SECURITY_ATTRIBUTES,
        disposition: Int,
        flags: Int,
        template: Pointer?
    ): Pointer?
    fun LocalFree(memory: Pointer): Pointer?
    fun CreatePipe(
        read: PointerByReference,
        write: PointerByReference,
        security: SECURITY_ATTRIBUTES,
        size: Int
    ): Boolean
    fun SetHandleInformation(handle: Pointer, mask: Int, flags: Int): Boolean
    fun CloseHandle(handle: Pointer): Boolean
    fun ReadFile(
        handle: Pointer,
        buffer: ByteArray,
        size: Int,
        read: IntByReference,
        overlapped: Pointer?
    ): Boolean
    fun WriteFile(
        handle: Pointer,
        buffer: ByteArray,
        size: Int,
        written: IntByReference,
        overlapped: Pointer?
    ): Boolean
    fun InitializeProcThreadAttributeList(
        list: Pointer?,
        count: Int,
        flags: Int,
        size: ULONG_PTRByReference
    ): Boolean
    fun UpdateProcThreadAttribute(
        list: Pointer,
        flags: Int,
        attribute: ULONG_PTR,
        value: Pointer,
        size: SIZE_T,
        previous: Pointer?,
        returned: Pointer?
    ): Boolean
    fun DeleteProcThreadAttributeList(list: Pointer)
    fun CreateProcessW(
        application: WString,
        command: CharArray,
        processSecurity: Pointer?,
        threadSecurity: Pointer?,
        inherit: Boolean,
        flags: Int,
        environment: Pointer?,
        directory: WString?,
        startup: Pointer,
        info: PROCESS_INFORMATION
    ): Boolean
    fun ResumeThread(thread: Pointer): Int
    fun TerminateProcess(process: Pointer, code: Int): Boolean
    fun WaitForSingleObject(handle: Pointer, milliseconds: Int): Int
    fun CreateJobObjectW(security: Pointer?, name: WString?): Pointer?
    fun SetInformationJobObject(
        job: Pointer,
        kind: Int,
        information: Pointer,
        size: Int
    ): Boolean
    fun AssignProcessToJobObject(job: Pointer, process: Pointer): Boolean
    fun TerminateJobObject(job: Pointer, code: Int): Boolean
    fun QueryInformationJobObject(
        job: Pointer,
        kind: Int,
        information: Pointer,
        size: Int,
        returned: Pointer?
    ): Boolean
}
