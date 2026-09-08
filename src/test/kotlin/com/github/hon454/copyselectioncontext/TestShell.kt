package com.github.hon454.copyselectioncontext

internal object TestShell {
    fun bashExecutable(): String =
        System.getenv("BASH_EXE")
            ?.takeIf(String::isNotBlank)
            ?: "bash"
}
