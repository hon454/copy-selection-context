package com.github.hon454.copyselectioncontext

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class CopySelectionGutterIconRenderer : GutterIconRenderer() {
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/copyContext.svg", CopySelectionGutterIconRenderer::class.java)
    override fun getTooltipText(): String = "Copied to clipboard"
    override fun equals(other: Any?): Boolean = other is CopySelectionGutterIconRenderer
    override fun hashCode(): Int = javaClass.hashCode()
    override fun getAlignment(): Alignment = Alignment.LEFT
}
