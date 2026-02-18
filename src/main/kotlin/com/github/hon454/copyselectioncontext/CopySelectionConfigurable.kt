package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CopySelectionConfigurable : Configurable {
    private val settings = CopySelectionSettings.getInstance()
    private var dialogPanel: DialogPanel? = null
    private var outputFormatCombo: JComboBox<String>? = null
    private var presetCombo: JComboBox<String>? = null
    private var templateTextField: JTextField? = null
    private var previewLabel: JLabel? = null
    private var validationLabel: JLabel? = null

    override fun getDisplayName() = CopySelectionBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val state = settings.state
        val panel = panel {
            group(CopySelectionBundle.message("settings.path.type")) {
                buttonsGroup {
                    row { radioButton(CopySelectionBundle.message("settings.path.absolute"), PathType.ABSOLUTE) }
                    row { radioButton(CopySelectionBundle.message("settings.path.relative"), PathType.RELATIVE) }
                }.bind(state::defaultPathType)
            }
            group(CopySelectionBundle.message("settings.group.output")) {
                row(CopySelectionBundle.message("settings.format.output.label")) {
                    comboBox(listOf("claude", "pathline", "template"))
                        .bindItem(
                            { state.outputFormat },
                            { state.outputFormat = it ?: "claude" }
                        )
                        .comment(CopySelectionBundle.message("settings.format.output.comment"))
                        .also { cell -> outputFormatCombo = cell.component }
                        .onChanged { updateTemplateControls() }
                }
                row(CopySelectionBundle.message("settings.template.preset.label")) {
                    val presetNames = TemplateFormatter.PRESETS.map { it.first }
                    val items = listOf(CopySelectionBundle.message("settings.template.preset.custom")) + presetNames
                    comboBox(items)
                        .also { cell ->
                            presetCombo = cell.component
                            cell.component.addActionListener {
                                applySelectedPreset()
                            }
                        }
                }
                row(CopySelectionBundle.message("settings.template.label")) {
                    textField()
                        .bindText(state::customFormatTemplate)
                        .comment(CopySelectionBundle.message("settings.template.variables.comment"))
                        .also { cell ->
                            templateTextField = cell.component
                            cell.component.document.addDocumentListener(object : DocumentListener {
                                override fun insertUpdate(e: DocumentEvent) = updatePreviewAndValidation()
                                override fun removeUpdate(e: DocumentEvent) = updatePreviewAndValidation()
                                override fun changedUpdate(e: DocumentEvent) = updatePreviewAndValidation()
                            })
                        }
                }
                row(CopySelectionBundle.message("settings.template.preview.label")) {
                    label("")
                        .also { cell -> previewLabel = cell.component }
                }
                row {
                    label("")
                        .also { cell ->
                            validationLabel = cell.component
                            cell.component.isVisible = false
                        }
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
        }
        dialogPanel = panel
        updatePresetSelection()
        updatePreviewAndValidation()
        updateTemplateControls()
        return panel
    }

    override fun isModified() = dialogPanel?.isModified() ?: false
    override fun apply() { dialogPanel?.apply() }

    override fun reset() {
        dialogPanel?.reset()
        updatePresetSelection()
        updatePreviewAndValidation()
        updateTemplateControls()
    }

    private fun applySelectedPreset() {
        val customLabel = CopySelectionBundle.message("settings.template.preset.custom")
        val selected = presetCombo?.selectedItem as? String ?: return
        if (selected == customLabel) {
            return
        }

        val presetTemplate = TemplateFormatter.PRESETS.find { it.first == selected }?.second ?: return
        templateTextField?.text = presetTemplate
        updatePreviewAndValidation()
    }

    private fun updatePresetSelection() {
        val currentTemplate = templateTextField?.text ?: settings.state.customFormatTemplate
        val customLabel = CopySelectionBundle.message("settings.template.preset.custom")
        val presetName = TemplateFormatter.PRESETS.find { it.second == currentTemplate }?.first ?: customLabel
        if (presetCombo?.selectedItem != presetName) {
            presetCombo?.selectedItem = presetName
        }
    }

    private fun updateTemplateControls() {
        val isTemplate = (outputFormatCombo?.selectedItem as? String) == "template"
        presetCombo?.isEnabled = isTemplate
        templateTextField?.isEnabled = isTemplate
        previewLabel?.isEnabled = isTemplate
        validationLabel?.isEnabled = isTemplate
    }

    private fun updatePreviewAndValidation() {
        val template = templateTextField?.text ?: settings.state.customFormatTemplate
        val sampleContext = FormatContext(
            path = "src/main/kotlin/Example.kt",
            startLine = 42,
            endLine = 53,
            code = "fun hello() = println(\"world\")",
            language = "kotlin",
            filename = "Example.kt"
        )

        val rendered = if (template.isBlank()) "" else TemplateFormatter(template).format(sampleContext)
        previewLabel?.text = toHtml(rendered)

        val unknownVariables = TemplateFormatter.findUnknownVariables(template)
        if (unknownVariables.isEmpty()) {
            validationLabel?.isVisible = false
            validationLabel?.text = ""
        } else {
            val unknowns = unknownVariables.joinToString(", ") { "{$it}" }
            validationLabel?.text = CopySelectionBundle.message("settings.template.validation.unknown", unknowns)
            validationLabel?.isVisible = true
        }

        updatePresetSelection()
    }

    private fun toHtml(text: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        return "<html>$escaped</html>"
    }
}
