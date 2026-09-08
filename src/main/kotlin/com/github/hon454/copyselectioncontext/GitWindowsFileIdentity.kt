package com.github.hon454.copyselectioncontext

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.W32APIOptions
import java.io.IOException
import java.nio.file.Path

/** JDK Windows fileKey is null. Read its native volume + 128-bit file ID through the IDE's bundled JNA. */
internal object GitWindowsFileIdentity {
    fun read(path: Path, checkCanceled: () -> Unit,
        open: (String) -> GitIdentityHandle? = ::openHandle): String? = try {
        checkCanceled()
        open(nativePath(path.toAbsolutePath().normalize().toString()))?.use { handle ->
            checkCanceled()
            val identity = handle.readIdentity()
            checkCanceled()
            identity?.takeIf { it.size == IDENTITY_BYTES && it.any { byte -> byte != 0.toByte() } }
                ?.joinToString("") { "%02x".format(it) }
        }
    } catch (_: LinkageError) {
        // Unavailable native support is a typed unavailable target, never an unverified copy.
        null
    }

    internal fun nativePath(absolute: String): String = when {
        absolute.startsWith("\\\\?\\") -> absolute
        absolute.startsWith("\\\\") -> "\\\\?\\UNC\\" + absolute.removePrefix("\\\\")
        else -> "\\\\?\\$absolute"
    }

    private fun openHandle(path: String): GitIdentityHandle? {
        // Explicit Unicode mapping also works when unrelated code selected JNA's ASCII default.
        val api = Native.load("kernel32", Kernel32::class.java, W32APIOptions.UNICODE_OPTIONS)
        // Existing metadata only; share every operation so this observation does not lock out edits or moves.
        val handle = api.CreateFile(path, 0, WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE or WinNT.FILE_SHARE_DELETE,
            null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_OPEN_REPARSE_POINT, null)
        if (handle == null || handle == WinBase.INVALID_HANDLE_VALUE) return null
        return object : GitIdentityHandle {
            override fun readIdentity(): ByteArray? = Memory(IDENTITY_BYTES.toLong()).use { buffer ->
                if (api.GetFileInformationByHandleEx(handle, WinBase.FileIdInfo, buffer, DWORD(IDENTITY_BYTES.toLong()))) {
                    buffer.getByteArray(0, IDENTITY_BYTES)
                } else null
            }
            override fun close() {
                if (!api.CloseHandle(handle)) throw IOException("Cannot close source identity handle")
            }
        }
    }

    private const val IDENTITY_BYTES = 24 // FILE_ID_INFO: ULONGLONG VolumeSerialNumber + FILE_ID_128
}

internal interface GitIdentityHandle : AutoCloseable {
    fun readIdentity(): ByteArray?
}
