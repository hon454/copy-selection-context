package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.ActionLink
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CopySelectionConfigurableTest {
    @Test
    fun `multiline preset populates editor and preview without flattening line breaks`() = onEdt {
        val fixture = createFixture()

        fixture.outputFormat.selectedItem = OutputFormatOption.TEMPLATE
        fixture.preset.selectedItem = TemplatePreset.WITH_CODE_BLOCK

        assertEquals(TemplateFormatter.PRESET_WITH_CODE_BLOCK, fixture.editor.text)
        assertEquals(
            "src/main/kotlin/Example.kt:42-53\n```kotlin\nfun hello() = println(\"world\")\n```",
            fixture.preview.text
        )
    }

    @Test
    fun `unknown template variables prevent apply and preserve settings`() = onEdt {
        val fixture = createFixture()

        fixture.outputFormat.selectedItem = OutputFormatOption.TEMPLATE
        fixture.editor.text = "{path}\n{unknown}"

        val exception = assertFailsWith<ConfigurationException> {
            fixture.configurable.apply()
        }
        assertTrue(exception.localizedMessage.orEmpty().contains("{unknown}"))
        assertEquals("claude", fixture.settings.state.outputFormat)
        assertEquals("", fixture.settings.state.customFormatTemplate)
    }

    @Test
    fun `valid multiline template applies and reset restores persisted content`() = onEdt {
        val fixture = createFixture()
        val template = "{path}:{range}\n```{lang}\n{code}\n```"

        fixture.outputFormat.selectedItem = OutputFormatOption.TEMPLATE
        fixture.editor.text = template
        fixture.configurable.apply()

        assertEquals("template", fixture.settings.state.outputFormat)
        assertEquals(template, fixture.settings.state.customFormatTemplate)

        fixture.editor.text = "temporary edit"
        fixture.configurable.reset()

        assertEquals(template, fixture.editor.text)
        assertEquals(CopySelectionConfigurable.renderTemplatePreview(template), fixture.preview.text)
    }

    @Test
    fun `blank template preview matches runtime fallback`() = onEdt {
        val context = FormatContext(
            path = "src/main/kotlin/Example.kt",
            startLine = 42,
            endLine = 53,
            code = "fun hello() = println(\"world\")",
            language = "kotlin",
            filename = "Example.kt"
        )

        listOf("", " \n\t").forEach { template ->
            val settings = CopySelectionSettings.State(
                outputFormat = "template",
                customFormatTemplate = template
            )
            val runtimeOutput = OutputFormatterFactory.getFormatterForSettings(settings).format(context)

            assertEquals(runtimeOutput, CopySelectionConfigurable.renderTemplatePreview(template))
            assertEquals("src/main/kotlin/Example.kt:42-53", runtimeOutput)
        }
    }

    @Test
    fun `template editor and preview are bounded and accessible`() = onEdt {
        val fixture = createFixture()

        assertEquals(CopySelectionConfigurable.TEMPLATE_EDITOR_ROWS, fixture.editor.rows)
        assertEquals(CopySelectionConfigurable.PREVIEW_ROWS, fixture.preview.rows)
        assertTrue(fixture.editor.isEditable)
        assertFalse(fixture.preview.isEditable)
        assertTrue(fixture.preview.isFocusable)
        assertTrue(fixture.editor.accessibleContext.accessibleName.isNotBlank())
        assertTrue(fixture.editor.accessibleContext.accessibleDescription.isNotBlank())
        assertTrue(fixture.preview.accessibleContext.accessibleName.isNotBlank())
        assertTrue(fixture.preview.accessibleContext.accessibleDescription.isNotBlank())
    }

    @Test
    fun `analytics view shows total format and language counters`() = onEdt {
        val analytics = CopySelectionAnalytics().apply {
            recordCopy("template", "typescript")
            recordCopy("claude", "kotlin")
            recordCopy("template", "typescript")
        }

        val fixture = createFixture(analytics = analytics)

        assertTrue(fixture.analyticsSummary.text.contains("3"))
        assertTrue(fixture.analyticsSummary.text.contains("claude: 1"))
        assertTrue(fixture.analyticsSummary.text.contains("template: 2"))
        assertTrue(fixture.analyticsSummary.text.contains("kotlin: 1"))
        assertTrue(fixture.analyticsSummary.text.contains("typescript: 2"))
        assertFalse(fixture.analyticsSummary.isEditable)
        assertTrue(fixture.analyticsSummary.isFocusable)
        assertTrue(fixture.analyticsSummary.accessibleContext.accessibleDescription.isNotBlank())
    }

    @Test
    fun `analytics reset requires confirmation and refreshes persisted view`() = onEdt {
        val analytics = CopySelectionAnalytics().apply { recordCopy("claude", "kotlin") }
        var resetConfirmed = false
        val fixture = createFixture(
            analytics = analytics,
            confirmAnalyticsReset = { resetConfirmed },
        )

        fixture.analyticsReset.doClick()
        assertEquals(1, analytics.snapshot().totalCopyCount)

        resetConfirmed = true
        fixture.analyticsReset.doClick()

        assertEquals(CopySelectionAnalytics.Snapshot(0, emptyMap(), emptyMap()), analytics.snapshot())
        assertTrue(fixture.analyticsSummary.text.contains("0"))
        assertFalse(fixture.analyticsSummary.text.contains("claude: 1"))

        val reloaded = CopySelectionAnalytics()
        reloaded.loadState(analytics.state)
        assertEquals(CopySelectionAnalytics.Snapshot(0, emptyMap(), emptyMap()), reloaded.snapshot())
    }

    @Test
    fun `settings exposes a passive Marketplace review link`() = onEdt {
        var openCount = 0
        val fixture = createFixture(openMarketplaceReviewPage = { openCount++ })

        assertEquals(CopySelectionBundle.message("settings.review.marketplace"), fixture.reviewLink.text)
        assertEquals(0, openCount)

        fixture.reviewLink.doClick()

        assertEquals(1, openCount)
    }

    private fun createFixture(
        analytics: CopySelectionAnalytics = CopySelectionAnalytics(),
        openMarketplaceReviewPage: () -> Unit = {},
        confirmAnalyticsReset: () -> Boolean = { false },
    ): Fixture {
        val settings = CopySelectionSettings()
        val configurable = CopySelectionConfigurable(
            settings = settings,
            trimOpenProjectHistory = {},
            analytics = analytics,
            openMarketplaceReviewPage = openMarketplaceReviewPage,
            confirmAnalyticsReset = confirmAnalyticsReset,
        )
        val component = configurable.createComponent()
        val comboBoxes = descendantsOfType<JComboBox<*>>(component)
        val textAreas = descendantsOfType<JTextArea>(component)
        val buttons = descendantsOfType<JButton>(component)
        val reviewLinks = descendantsOfType<ActionLink>(component)

        val outputFormat = comboBoxes.firstOrNull { combo -> combo.items().contains(OutputFormatOption.TEMPLATE) }
        val preset = comboBoxes.firstOrNull { combo -> combo.items().contains(TemplatePreset.WITH_CODE_BLOCK) }
        val editor = textAreas.firstOrNull { it.isEditable }
        val preview = textAreas.firstOrNull {
            it.accessibleContext.accessibleName == CopySelectionBundle.message("settings.template.preview.label")
        }
        val analyticsSummary = textAreas.firstOrNull {
            it.accessibleContext.accessibleName == CopySelectionBundle.message("settings.analytics.statistics.label")
        }
        val analyticsReset = buttons.firstOrNull {
            it.text == CopySelectionBundle.message("settings.analytics.reset")
        }
        val reviewLink = reviewLinks.firstOrNull {
            it.text == CopySelectionBundle.message("settings.review.marketplace")
        }

        return Fixture(
            configurable = configurable,
            settings = settings,
            outputFormat = assertNotNull(outputFormat),
            preset = assertNotNull(preset),
            editor = assertNotNull(editor),
            preview = assertNotNull(preview),
            analyticsSummary = assertNotNull(analyticsSummary),
            analyticsReset = assertNotNull(analyticsReset),
            reviewLink = assertNotNull(reviewLink),
        )
    }

    private fun JComboBox<*>.items(): List<Any?> =
        (0 until itemCount).map { getItemAt(it) }

    private inline fun <reified T : Component> descendantsOfType(root: Component): List<T> =
        descendantsOfType(root, T::class.java)

    private fun <T : Component> descendantsOfType(root: Component, type: Class<T>): List<T> = buildList {
        if (type.isInstance(root)) {
            add(type.cast(root))
        }
        if (root is Container) {
            root.components.forEach { addAll(descendantsOfType(it, type)) }
        }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        javax.swing.SwingUtilities.invokeAndWait {
            result = runCatching(block)
        }
        return requireNotNull(result).getOrThrow()
    }

    private data class Fixture(
        val configurable: CopySelectionConfigurable,
        val settings: CopySelectionSettings,
        val outputFormat: JComboBox<*>,
        val preset: JComboBox<*>,
        val editor: JTextArea,
        val preview: JTextArea,
        val analyticsSummary: JTextArea,
        val analyticsReset: JButton,
        val reviewLink: ActionLink,
    )
}
