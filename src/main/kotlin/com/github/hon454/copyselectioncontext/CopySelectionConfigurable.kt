package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class CopySelectionConfigurable : Configurable {
    private val settings = CopySelectionSettings.getInstance()
    private var dialogPanel: DialogPanel? = null

    override fun getDisplayName() = CopySelectionBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val state = settings.state
        return panel {
            group(CopySelectionBundle.message("settings.path.type")) {
                buttonsGroup {
                    row { radioButton(CopySelectionBundle.message("settings.path.absolute"), PathType.ABSOLUTE) }
                    row { radioButton(CopySelectionBundle.message("settings.path.relative"), PathType.RELATIVE) }
                }.bind(state::defaultPathType)
            }
            group(CopySelectionBundle.message("settings.group.output")) {
                row(CopySelectionBundle.message("settings.format.output.label")) {
                    comboBox(listOf("claude", "pathline"))
                        .bindItem(
                            { state.outputFormat },
                            { state.outputFormat = it ?: "claude" }
                        )
                        .comment("claude = @path#L format, pathline = path:line format")
                }
                row {
                    checkBox(CopySelectionBundle.message("settings.include.code"))
                        .bindSelected(state::includeCodeContent)
                }
                row {
                    checkBox(CopySelectionBundle.message("settings.trimming.enable"))
                        .bindSelected(state::codeTrimming)
                }
            }
            group(CopySelectionBundle.message("settings.group.behavior")) {
                row {
                    checkBox(CopySelectionBundle.message("settings.notification.enable"))
                        .bindSelected(state::enableNotification)
                }
            }
            group(CopySelectionBundle.message("settings.history.size")) {
                row(CopySelectionBundle.message("settings.history.size.label")) {
                    spinner(1..100)
                        .bindIntValue(state::copyHistorySize)
                }
            }
            group(CopySelectionBundle.message("settings.group.analytics")) {
                row {
                    checkBox(CopySelectionBundle.message("settings.analytics.enable"))
                        .bindSelected(state::analyticsEnabled)
                }
            }
        }.also { dialogPanel = it }
    }

    override fun isModified() = dialogPanel?.isModified() ?: false
    override fun apply() { dialogPanel?.apply() }
    override fun reset() { dialogPanel?.reset() }
}
