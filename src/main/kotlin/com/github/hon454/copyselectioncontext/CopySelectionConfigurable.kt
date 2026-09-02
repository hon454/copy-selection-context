package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CopySelectionConfigurable internal constructor(
    private val settings: CopySelectionSettings,
    private val trimOpenProjectHistory: (Int) -> Unit = CopyHistoryService::trimOpenProjects
) : Configurable {
    constructor() : this(CopySelectionSettings.getInstance())

    private var dialogPanel: DialogPanel? = null
    private var outputFormatCombo: JComboBox<OutputFormatOption>? = null
    private var presetCombo: JComboBox<String>? = null
    private var templateTextArea: JTextArea? = null
    private var previewTextArea: JTextArea? = null
    private var updatingPresetSelection = false

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
                    comboBox(OutputFormatOption.entries)
                        .bindItem(
                            { OutputFormatOption.fromKey(state.outputFormat) },
                            { state.outputFormat = (it ?: OutputFormatOption.default).key }
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
                    textArea()
                        .rows(TEMPLATE_EDITOR_ROWS)
                        .columns(TEMPLATE_COLUMNS)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .bindText(state::customFormatTemplate)
                        .comment(CopySelectionBundle.message("settings.template.variables.comment"))
                        .accessibleName(CopySelectionBundle.message("settings.template.label"))
                        .accessibleDescription(CopySelectionBundle.message("settings.template.variables.comment"))
                        .validationOnApply { component ->
                            if (isTemplateFormatSelected()) {
                                templateValidationMessage(component.text)?.let { error(it) }
                            } else {
                                null
                            }
                        }
                        .validationOnInput { component ->
                            if (isTemplateFormatSelected()) {
                                templateValidationMessage(component.text)?.let { error(it) }
                            } else {
                                null
                            }
                        }
                        .also { cell ->
                            templateTextArea = cell.component
                            cell.component.document.addDocumentListener(object : DocumentListener {
                                override fun insertUpdate(e: DocumentEvent) = updatePreview()
                                override fun removeUpdate(e: DocumentEvent) = updatePreview()
                                override fun changedUpdate(e: DocumentEvent) = updatePreview()
                            })
                        }
                }.resizableRow()
                row(CopySelectionBundle.message("settings.template.preview.label")) {
                    textArea()
                        .rows(PREVIEW_ROWS)
                        .columns(TEMPLATE_COLUMNS)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .accessibleName(CopySelectionBundle.message("settings.template.preview.label"))
                        .accessibleDescription(CopySelectionBundle.message("settings.template.preview.description"))
                        .applyToComponent {
                            isEditable = false
                            isFocusable = true
                        }
                        .also { cell ->
                            previewTextArea = cell.component
                        }
                }.resizableRow()
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
                    spinner(0..100)
                        .bindIntValue(state::copyHistorySize)
                        .comment(CopySelectionBundle.message("settings.history.size.comment"))
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
        updatePreview()
        updateTemplateControls()
        return panel
    }

    override fun isModified() = dialogPanel?.isModified() ?: false

    override fun apply() {
        val validationMessage = if (isTemplateFormatSelected()) {
            templateValidationMessage(templateTextArea?.text.orEmpty())
        } else {
            null
        }
        if (validationMessage != null) {
            throw ConfigurationException(validationMessage)
        }
        dialogPanel?.apply()
        trimOpenProjectHistory(settings.state.copyHistorySize)
    }

    override fun reset() {
        dialogPanel?.reset()
        updatePresetSelection()
        updatePreview()
        updateTemplateControls()
    }

    override fun disposeUIResources() {
        dialogPanel = null
        outputFormatCombo = null
        presetCombo = null
        templateTextArea = null
        previewTextArea = null
        updatingPresetSelection = false
    }

    private fun applySelectedPreset() {
        if (updatingPresetSelection) {
            return
        }

        val customLabel = CopySelectionBundle.message("settings.template.preset.custom")
        val selected = presetCombo?.selectedItem as? String ?: return
        if (selected == customLabel) {
            return
        }

        val presetTemplate = TemplateFormatter.PRESETS.find { it.first == selected }?.second ?: return
        templateTextArea?.text = presetTemplate
        updatePreview()
    }

    private fun updatePresetSelection() {
        val currentTemplate = templateTextArea?.text ?: settings.state.customFormatTemplate
        val customLabel = CopySelectionBundle.message("settings.template.preset.custom")
        val presetName = TemplateFormatter.PRESETS.find { it.second == currentTemplate }?.first ?: customLabel
        if (presetCombo?.selectedItem != presetName) {
            updatingPresetSelection = true
            try {
                presetCombo?.selectedItem = presetName
            } finally {
                updatingPresetSelection = false
            }
        }
    }

    private fun updateTemplateControls() {
        val isTemplate = isTemplateFormatSelected()
        presetCombo?.isEnabled = isTemplate
        templateTextArea?.isEnabled = isTemplate
        previewTextArea?.isEnabled = isTemplate
    }

    private fun updatePreview() {
        val template = templateTextArea?.text ?: settings.state.customFormatTemplate
        previewTextArea?.text = renderTemplatePreview(template)
        previewTextArea?.caretPosition = 0
        updatePresetSelection()
    }

    private fun isTemplateFormatSelected() =
        outputFormatCombo?.selectedItem == OutputFormatOption.TEMPLATE

    companion object {
        internal const val TEMPLATE_EDITOR_ROWS = 6
        internal const val PREVIEW_ROWS = 6
        private const val TEMPLATE_COLUMNS = 60

        private val SAMPLE_CONTEXT = FormatContext(
            path = "src/main/kotlin/Example.kt",
            startLine = 42,
            endLine = 53,
            code = "fun hello() = println(\"world\")",
            language = "kotlin",
            filename = "Example.kt"
        )

        internal fun renderTemplatePreview(template: String): String =
            OutputFormatterFactory.getTemplateFormatter(template).format(SAMPLE_CONTEXT)

        internal fun templateValidationMessage(template: String): String? {
            val unknownVariables = TemplateFormatter.findUnknownVariables(template)
            if (unknownVariables.isEmpty()) {
                return null
            }

            val unknowns = unknownVariables.joinToString(", ") { "{$it}" }
            return CopySelectionBundle.message("settings.template.validation.unknown", unknowns)
        }
    }
}
