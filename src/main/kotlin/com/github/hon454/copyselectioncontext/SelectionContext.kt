package com.github.hon454.copyselectioncontext

import com.intellij.openapi.vfs.VirtualFile

data class SelectionContext(
    val path: String,
    val file: VirtualFile,
    val startLine: Int,
    val endLine: Int,
    val code: String,
    val language: String,
    val filename: String,
) {
    val lineRange: String
        get() = if (startLine == endLine) "$startLine" else "$startLine-$endLine"

    val lineNumbers: Pair<Int, Int>
        get() = Pair(startLine, endLine)

    fun toFormatContext(code: String? = null): FormatContext = FormatContext(
        path = path,
        startLine = startLine,
        endLine = endLine,
        code = code,
        language = language,
        filename = filename,
    )
}
