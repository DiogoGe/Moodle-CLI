package moodle.infrastructure.windows

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path
import java.util.UUID

/** A protected DACL prevents broad permissions from the temporary directory being inherited. */
internal fun createWindowsPrivateProfile(parent: Path?): Path {
    val kernel = Native.load("kernel32", WindowsKernel::class.java)
    val security = Native.load("Advapi32", WindowsProfileSecurity::class.java)
    val descriptor = PointerByReference()
    val sid = currentWindowsUserSid()
    check(security.ConvertStringSecurityDescriptorToSecurityDescriptorW(WString("D:P(A;OICI;FA;;;$sid)"), 1, descriptor, null)) {
        "Private browser permissions unavailable"
    }
    try {
        val attributes = SECURITY_ATTRIBUTES().apply { lpSecurityDescriptor = descriptor.value }
        val root = (parent ?: Path.of(System.getProperty("java.io.tmpdir"))).toRealPath()
        val profile = root.resolve("moodle-cli-login-${UUID.randomUUID()}")
        check(kernel.CreateDirectoryW(WString(profile.toString()), attributes)) { "Private browser profile creation failed" }
        return profile
    } finally {
        kernel.LocalFree(descriptor.value)
    }
}

private fun currentWindowsUserSid(): String {
    val token = HANDLEByReference()
    check(Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_QUERY, token)) {
        "Browser profile owner unavailable"
    }
    try {
        return Advapi32Util.getTokenAccount(token.value).sidString
    } finally {
        Kernel32.INSTANCE.CloseHandle(token.value)
    }
}
