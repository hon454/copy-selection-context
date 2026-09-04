package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CopySelectionConfigurable internal constructor(
    private val settings: CopySelectionSettings,
    private val trimOpenProjectHistory: (Int) -> Unit = CopyHistoryService::trimOpenProjects,
    private val analytics: CopySelectionAnalytics = CopySelectionAnalytics.getInstance(),
    private val openMarketplaceReviewPage: () -> Unit =
        CopySelectionReviewService.getInstance()::openMarketplaceReviewPage,
    private val confirmAnalyticsReset: () -> Boolean = {
        Messages.showYesNoDialog(
            CopySelectionBundle.message("settings.analytics.reset.confirm.message"),
            CopySelectionBundle.message("settings.analytics.reset.confirm.title"),
            Messages.getQuestionIcon(),
        ) == Messages.YES
    },
) : Configurable {
    constructor() : this(CopySelectionSettings.getInstance())

    private var dialogPanel: DialogPanel? = null
    private var outputFormatCombo: JComboBox<OutputFormatOption>? = null
    private var presetCombo: JComboBox<TemplatePreset>? = null
    private var templateTextArea: JTextArea? = null
    private var previewTextArea: JTextArea? = null
    private var analyticsTextArea: JTextArea? = null
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
                    comboBox(TemplatePreset.entries)
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
                row {
                    link(CopySelectionBundle.message("settings.review.marketplace")) {
                        openMarketplaceReviewPage()
                    }
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
                row {
                    label(CopySelectionBundle.message("settings.analytics.local.only"))
                }
                row(CopySelectionBundle.message("settings.analytics.statistics.label")) {
                    textArea()
                        .rows(ANALYTICS_ROWS)
                        .columns(TEMPLATE_COLUMNS)
                        .align(AlignX.FILL)
                        .resizableColumn()
                        .accessibleName(CopySelectionBundle.message("settings.analytics.statistics.label"))
                        .accessibleDescription(CopySelectionBundle.message("settings.analytics.local.only"))
                        .applyToComponent {
                            isEditable = false
                            isFocusable = true
                        }
                        .also { cell -> analyticsTextArea = cell.component }
                }.resizableRow()
                row {
                    button(CopySelectionBundle.message("settings.analytics.reset")) {
                        if (confirmAnalyticsReset()) {
                            analytics.reset()
                            updateAnalyticsSummary()
                        }
                    }
                }
            }
        }
        dialogPanel = panel
        updatePresetSelection()
        updatePreview()
        updateTemplateControls()
        updateAnalyticsSummary()
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
        settings.withOutputLock { dialogPanel?.apply() }
        settings.outputSettingsCommitted()
        trimOpenProjectHistory(settings.state.copyHistorySize)
    }

    override fun reset() {
        dialogPanel?.reset()
        updatePresetSelection()
        updatePreview()
        updateTemplateControls()
        updateAnalyticsSummary()
    }

    override fun disposeUIResources() {
        dialogPanel = null
        outputFormatCombo = null
        presetCombo = null
        templateTextArea = null
        previewTextArea = null
        analyticsTextArea = null
        updatingPresetSelection = false
    }

    private fun applySelectedPreset() {
        if (updatingPresetSelection) {
            return
        }

        val selected = presetCombo?.selectedItem as? TemplatePreset ?: return
        val presetTemplate = selected.template ?: return
        templateTextArea?.text = presetTemplate
        updatePreview()
    }

    private fun updatePresetSelection() {
        val currentTemplate = templateTextArea?.text ?: settings.state.customFormatTemplate
        val preset = TemplateFormatter.PRESETS.find { it.template == currentTemplate } ?: TemplatePreset.CUSTOM
        if (presetCombo?.selectedItem != preset) {
            updatingPresetSelection = true
            try {
                presetCombo?.selectedItem = preset
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

    private fun updateAnalyticsSummary() {
        analyticsTextArea?.text = renderAnalyticsSummary(analytics.snapshot())
        analyticsTextArea?.caretPosition = 0
    }

    private fun isTemplateFormatSelected() =
        outputFormatCombo?.selectedItem == OutputFormatOption.TEMPLATE

    companion object {
        internal const val TEMPLATE_EDITOR_ROWS = 6
        internal const val PREVIEW_ROWS = 6
        internal const val ANALYTICS_ROWS = 8
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

        internal fun renderAnalyticsSummary(snapshot: CopySelectionAnalytics.Snapshot): String = buildString {
            appendLine(
                CopySelectionBundle.message(
                    "settings.analytics.statistics.total",
                    snapshot.totalCopyCount,
                ),
            )
            appendLine()
            appendUsage(
                CopySelectionBundle.message("settings.analytics.statistics.formats"),
                snapshot.formatUsage,
            )
            appendLine()
            appendUsage(
                CopySelectionBundle.message("settings.analytics.statistics.languages"),
                snapshot.languageUsage,
            )
        }.trimEnd()

        private fun StringBuilder.appendUsage(title: String, usage: Map<String, Int>) {
            appendLine(title)
            if (usage.isEmpty()) {
                appendLine(CopySelectionBundle.message("settings.analytics.statistics.empty"))
                return
            }
            usage.toSortedMap().forEach { (key, count) ->
                val label = if (key == "mixed") CopySelectionBundle.message("settings.analytics.language.mixed") else key
                appendLine("$label: $count")
            }
        }

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
