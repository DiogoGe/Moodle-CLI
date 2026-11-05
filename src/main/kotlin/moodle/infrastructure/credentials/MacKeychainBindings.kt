package moodle.infrastructure.credentials

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import moodle.domain.exceptions.MoodleException

internal class MacKeychainBindings {
    init {
        if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            throw MoodleException.Credentials("error.credentials.unsupported")
        }
    }

    private val securityPath = "/System/Library/Frameworks/Security.framework/Security"
    private val foundationPath = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"
    val security: MacSecurity = Native.load(securityPath, MacSecurity::class.java)
    val core: MacCoreFoundation = Native.load(foundationPath, MacCoreFoundation::class.java)
    private val securityLibrary = NativeLibrary.getInstance(securityPath)
    private val foundationLibrary = NativeLibrary.getInstance(foundationPath)

    fun securityConstant(name: String): Pointer = securityLibrary.getGlobalVariableAddress(name).getPointer(0)

    fun foundationConstant(name: String): Pointer = foundationLibrary.getGlobalVariableAddress(name).getPointer(0)

    fun dictionaryCallbacks(name: String): Pointer = foundationLibrary.getGlobalVariableAddress(name)
}
