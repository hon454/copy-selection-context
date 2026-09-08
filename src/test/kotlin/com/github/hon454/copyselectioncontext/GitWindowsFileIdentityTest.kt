package com.github.hon454.copyselectioncontext

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Native-handle failure injection; actual Windows calls are covered by the real Git/source fixtures. */
class GitWindowsFileIdentityTest {
    @Test fun `native paths preserve unicode spaces and long path prefixes literally`() {
        assertEquals("\\\\?\\C:\\한글 folder\\a & [x].txt", GitWindowsFileIdentity.nativePath("C:\\한글 folder\\a & [x].txt"))
        assertEquals("\\\\?\\UNC\\server\\share\\한글.txt", GitWindowsFileIdentity.nativePath("\\\\server\\share\\한글.txt"))
        assertEquals("\\\\?\\C:\\already.txt", GitWindowsFileIdentity.nativePath("\\\\?\\C:\\already.txt"))
    }

    @Test fun `native identity retains volume and full file id and closes the handle`() {
        val handle = Handle(ByteArray(24) { (it + 1).toByte() })
        assertEquals((1..24).joinToString("") { "%02x".format(it) }, GitWindowsFileIdentity.read(Path.of("source"), {}, { handle }))
        assertTrue(handle.closed)
    }

    @Test fun `unavailable native identity is not accepted and opened handles close`() {
        assertNull(GitWindowsFileIdentity.read(Path.of("source"), {}, { null }))
        for (bytes in listOf(null, ByteArray(24), ByteArray(23) { 1 })) {
            val handle = Handle(bytes)
            assertNull(GitWindowsFileIdentity.read(Path.of("source"), {}, { handle }))
            assertTrue(handle.closed)
        }
        assertNull(GitWindowsFileIdentity.read(Path.of("source"), {}, { throw UnsatisfiedLinkError("injected") }))
    }

    @Test fun `cancellation after opening propagates unchanged after handle cleanup`() {
        for (canceled in listOf(ProcessCanceledException(), CancellationException("injected"))) for (cancelAt in listOf(2, 3)) {
            val handle = Handle(ByteArray(24) { 1 })
            var checks = 0
            val actual = assertFailsWith<RuntimeException> {
                GitWindowsFileIdentity.read(Path.of("source"), { if (++checks == cancelAt) throw canceled }, { handle })
            }
            assertSame(canceled, actual)
            assertTrue(handle.closed)
        }
    }

    @Test fun `native read failure propagates after closing its handle`() {
        var closed = false
        val failure = java.io.IOException("injected")
        val handle = object : GitIdentityHandle {
            override fun readIdentity(): ByteArray? = throw failure
            override fun close() { closed = true }
        }
        assertSame(failure, assertFailsWith<java.io.IOException> { GitWindowsFileIdentity.read(Path.of("source"), {}, { handle }) })
        assertTrue(closed)
    }

    private class Handle(private val bytes: ByteArray?) : GitIdentityHandle {
        var closed = false
        override fun readIdentity() = bytes
        override fun close() { closed = true }
    }
}
