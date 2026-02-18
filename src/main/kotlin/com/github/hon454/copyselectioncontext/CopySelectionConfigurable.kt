package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class CopySelectionConfigurable : Configurable {
    private val settings = CopySelectionSettings.getInstance()
    private var dialogPanel: DialogPanel? = null

    override fun getDisplayName() = "Copy Selection Context"

    override fun createComponent(): JComponent {
        val state = settings.state
        return panel {
            group("Path Settings") {
                buttonsGroup {
                    row { radioButton("Absolute path", PathType.ABSOLUTE) }
                    row { radioButton("Relative path", PathType.RELATIVE) }
                }.bind(state::defaultPathType)
            }
            group("Output Settings") {
                row("Output format:") {
                    comboBox(listOf("claude", "pathline"))
                        .bindItem(
                            { state.outputFormat },
                            { state.outputFormat = it ?: "claude" }
                        )
                        .comment("claude = @path#L format, pathline = path:line format")
                }
                row {
                    checkBox("Include code content")
                        .bindSelected(state::includeCodeContent)
                }
                row {
                    checkBox("Trim code whitespace")
                        .bindSelected(state::codeTrimming)
                        .comment("Remove leading/trailing whitespace from copied code")
                }
            }
            group("Notification Settings") {
                row {
                    checkBox("Show copy notification")
                        .bindSelected(state::enableNotification)
                        .comment("Show toast notification after copying")
                }
            }
            group("History Settings") {
                row("History size:") {
                    spinner(1..100)
                        .bindIntValue(state::copyHistorySize)
                        .comment("Number of recent copies to remember")
                }
            }
        }.also { dialogPanel = it }
    }

    override fun isModified() = dialogPanel?.isModified() ?: false
    override fun apply() { dialogPanel?.apply() }
    override fun reset() { dialogPanel?.reset() }
}
