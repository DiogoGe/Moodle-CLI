@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package moodle.infrastructure.credentials

import com.sun.jna.Library
import com.sun.jna.NativeLong
import com.sun.jna.Pointer

internal interface MacCoreFoundation : Library {
    fun CFDictionaryCreateMutable(
        allocator: Pointer?,
        capacity: NativeLong,
        keys: Pointer,
        values: Pointer
    ): Pointer

    fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer)

    fun CFStringCreateWithBytes(
        allocator: Pointer?,
        bytes: ByteArray,
        length: NativeLong,
        encoding: Int,
        external: Byte
    ): Pointer

    fun CFDataCreate(allocator: Pointer?, bytes: ByteArray, length: NativeLong): Pointer

    fun CFDataGetLength(data: Pointer): NativeLong

    fun CFDataGetBytePtr(data: Pointer): Pointer

    fun CFDataGetTypeID(): NativeLong

    fun CFGetTypeID(value: Pointer): NativeLong

    fun CFRelease(value: Pointer)
}
