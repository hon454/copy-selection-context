package com.github.hon454.copyselectioncontext

import com.intellij.openapi.vfs.VirtualFile
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SelectionContextTest {
    @Test
    fun `immutable context carries every captured formatter input`() {
        val file = mockk<VirtualFile>()
        val context = SelectionContext(
            path = "src/App.kt",
            file = file,
            startLine = 7,
            endLine = 9,
            code = "println(1)",
            language = "kotlin",
            filename = "App.kt",
        )

        assertEquals("src/App.kt", context.path)
        assertSame(file, context.file)
        assertEquals(Pair(7, 9), context.lineNumbers)
        assertEquals("println(1)", context.code)
        assertEquals("kotlin", context.language)
        assertEquals("App.kt", context.filename)
    }

    @Test
    fun `format context is derived only from captured values`() {
        val context = SelectionContext(
            path = "src/App.kt",
            file = mockk(),
            startLine = 7,
            endLine = 7,
            code = "captured()",
            language = "kotlin",
            filename = "App.kt",
        )

        assertEquals(
            FormatContext(
                path = "src/App.kt",
                startLine = 7,
                endLine = 7,
                code = "captured()",
                language = "kotlin",
                filename = "App.kt",
            ),
            context.toFormatContext(context.code),
        )
    }
}
