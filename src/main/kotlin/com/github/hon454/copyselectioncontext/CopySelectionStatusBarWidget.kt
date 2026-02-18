package com.github.hon454.copyselectioncontext

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import java.util.concurrent.atomic.AtomicReference

class CopySelectionStatusBarWidget(private val project: Project) : StatusBarWidget {
    companion object {
        const val ID = "CopySelectionStatusBarWidget"
    }

    private val lastCopied = AtomicReference("")

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
    }

    override fun dispose() {
    }

    fun update(content: String) {
        lastCopied.set(content)
    }
}
