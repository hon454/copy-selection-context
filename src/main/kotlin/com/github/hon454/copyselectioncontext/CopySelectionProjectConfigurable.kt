package com.github.hon454.copyselectioncontext

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

    override fun getDisplayName(): String = "Copy Selection Context (Project)"

    override fun createComponent(): JComponent = panel {
        group("Project Settings") {
            row {
                checkBox("Use project-specific settings (overrides application settings)")
                    .bindSelected({ useProjectSettings }, { useProjectSettings = it })
            }
            row("Output format:") {
                comboBox(listOf("claude", "pathline", "template"))
                    .bindItem({ outputFormat }, { outputFormat = it ?: "claude" })
            }
            row("Path type:") {
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
