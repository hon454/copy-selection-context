package com.github.hon454.copyselectioncontext

import com.github.hon454.copyselectioncontext.CopySelectionBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CopySelectionProjectConfigurable(private val project: Project) : Configurable {
    private var useProjectSettings = false
    private var outputFormat = "claude"
    private var pathType = "relative"

    override fun getDisplayName(): String = CopySelectionBundle.message("settings.project.title")

    override fun createComponent(): JComponent = panel {
        group(CopySelectionBundle.message("settings.project.group")) {
            row {
                checkBox(CopySelectionBundle.message("settings.project.use.project"))
                    .bindSelected({ useProjectSettings }, { useProjectSettings = it })
            }
            row(CopySelectionBundle.message("settings.format.output.label")) {
                comboBox(listOf("claude", "pathline", "template"))
                    .bindItem({ outputFormat }, { outputFormat = it ?: "claude" })
            }
            row(CopySelectionBundle.message("settings.project.path.label")) {
                comboBox(listOf("relative", "absolute"))
                    .bindItem({ pathType }, { pathType = it ?: "relative" })
            }
        }
    }

    override fun isModified(): Boolean {
        val state = CopySelectionProjectSettings.getInstance(project).state
        return useProjectSettings != state.useProjectSettings ||
               outputFormat != state.outputFormat ||
               pathType != state.pathType
    }

    override fun apply() {
        val state = CopySelectionProjectSettings.getInstance(project).state
        state.useProjectSettings = useProjectSettings
        state.outputFormat = outputFormat
        state.pathType = pathType
    }

    override fun reset() {
        val state = CopySelectionProjectSettings.getInstance(project).state
        useProjectSettings = state.useProjectSettings
        outputFormat = state.outputFormat
        pathType = state.pathType
    }
}
