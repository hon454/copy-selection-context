package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.MessageFormat
import java.util.Properties

class CopySelectionBundleTest {

    @Test
    fun `every shipped locale bundle has exactly the active base keys`() {
        val baseKeys = loadBundleKeys("messages/CopySelectionBundle.properties")

        LOCALIZED_BUNDLES.forEach { (localeName, resourcePath) ->
            val localizedKeys = loadBundleKeys(resourcePath)
            assertEquals(
                baseKeys,
                localizedKeys,
                "$localeName bundle keys must match the base bundle. " +
                    "Missing: ${baseKeys - localizedKeys}; Extra: ${localizedKeys - baseKeys}",
            )
        }
    }

    @Test
    fun `every active message is non-blank in every shipped locale`() {
        val baseKeys = loadBundleKeys("messages/CopySelectionBundle.properties")

        ALL_BUNDLES.forEach { (localeName, resourcePath) ->
            val bundle = loadBundle(resourcePath)
            baseKeys.forEach { key ->
                assertTrue(bundle.getProperty(key).isNotBlank(), "$localeName message '$key' should be non-blank")
            }
        }
    }

    @Test
    fun `localized bundles contain no unexpected English fallback`() {
        val base = loadBundle("messages/CopySelectionBundle.properties")
        val keysThatMustBeLocalized = base.stringPropertyNames() - LANGUAGE_NEUTRAL_KEYS

        LOCALIZED_BUNDLES.forEach { (localeName, resourcePath) ->
            val localized = loadBundle(resourcePath)
            keysThatMustBeLocalized.forEach { key ->
                assertNotEquals(
                    base.getProperty(key),
                    localized.getProperty(key),
                    "$localeName message '$key' must not fall back to English",
                )
            }
        }
    }

    @Test
    fun `localized MessageFormat patterns preserve arguments and apostrophe escapes`() {
        val base = loadBundle("messages/CopySelectionBundle.properties")

        ALL_BUNDLES.forEach { (localeName, resourcePath) ->
            val localized = loadBundle(resourcePath)
            base.stringPropertyNames().forEach { key ->
                val baseArgumentIndexes = messageArgumentIndexes(base.getProperty(key))
                val localizedPattern = localized.getProperty(key)
                val localizedArgumentIndexes = messageArgumentIndexes(localizedPattern)

                assertEquals(
                    baseArgumentIndexes,
                    localizedArgumentIndexes,
                    "$localeName message '$key' must preserve MessageFormat arguments",
                )
                if (localizedArgumentIndexes.isNotEmpty() || localizedPattern.contains('\'')) {
                    val argumentCount = (localizedArgumentIndexes.maxOrNull() ?: -1) + 1
                    val arguments = Array(argumentCount) { index -> "ARGUMENT_$index" }
                    val formatted = MessageFormat.format(localizedPattern, *arguments)
                    val expected = MESSAGE_ARGUMENT.replace(localizedPattern.replace("''", "'")) { match ->
                        arguments[match.groupValues[1].toInt()]
                    }
                    assertEquals(
                        expected,
                        formatted,
                        "$localeName message '$key' must preserve visible apostrophes and format its arguments",
                    )
                }
            }
        }
    }

    @Test
    fun `notification copied key resolves`() {
        val msg = CopySelectionBundle.message("notification.copied", "test")
        assertTrue(msg.contains("test"))
    }

    @Test
    fun `every permalink failure reason has guidance in every shipped locale`() {
        ALL_BUNDLES.forEach { (localeName, resourcePath) ->
            val bundle = loadBundle(resourcePath)
            PERMALINK_FAILURE_KEYS.forEach { key ->
                assertTrue(bundle.getProperty(key).isNotBlank(), "$localeName message '$key' should be non-blank")
            }
        }
    }

    @Test
    fun `history popup title key resolves`() {
        val msg = CopySelectionBundle.message("history.popup.title")
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `settings title key resolves`() {
        val msg = CopySelectionBundle.message("settings.title")
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun `all format keys resolve`() {
        listOf(
            "settings.format.claude",
            "settings.format.pathline",
            "settings.format.template"
        ).forEach { key ->
            assertTrue(CopySelectionBundle.message(key).isNotBlank())
        }
    }

    @Test
    fun `every shipped bundle localizes action tooltip and preset strings`() {
        val keys = listOf(
            "action.CopySelectionContext.Copy.text",
            "action.CopySelectionContext.CopyGitPermalink.text",
            "group.CopySelectionContextGroup.text",
            "gutter.tooltip.copied",
        ) + TemplatePreset.entries.map { it.messageKey }

        ALL_BUNDLES.forEach { (localeName, resourcePath) ->
            val bundle = loadBundle(resourcePath)
            keys.forEach { key ->
                assertTrue(bundle.getProperty(key).isNotBlank(), "$localeName key '$key' should be non-blank")
            }
        }
        TemplatePreset.entries.forEach { preset ->
            assertEquals(CopySelectionBundle.message(preset.messageKey), preset.toString())
        }

        val base = loadBundle("messages/CopySelectionBundle.properties")
        val korean = loadBundle("messages/CopySelectionBundle_ko.properties")
        val japanese = loadBundle("messages/CopySelectionBundle_ja.properties")
        val simplifiedChinese = loadBundle("messages/CopySelectionBundle_zh_CN.properties")
        val traditionalChinese = loadBundle("messages/CopySelectionBundle_zh_TW.properties")
        val permalinkActionKey = "action.CopySelectionContext.CopyGitPermalink.text"
        assertEquals("Copy GitHub/GitLab Permalink", base.getProperty(permalinkActionKey))
        assertEquals("GitHub/GitLab 퍼머링크 복사", korean.getProperty(permalinkActionKey))
        assertEquals("GitHub/GitLab パーマリンクをコピー", japanese.getProperty(permalinkActionKey))
        assertEquals("复制 GitHub/GitLab 永久链接", simplifiedChinese.getProperty(permalinkActionKey))
        assertEquals("複製 GitHub/GitLab 永久連結", traditionalChinese.getProperty(permalinkActionKey))
        assertEquals("Copied to clipboard", base.getProperty("gutter.tooltip.copied"))
        assertEquals("클립보드에 복사됨", korean.getProperty("gutter.tooltip.copied"))
        assertEquals("クリップボードにコピーしました", japanese.getProperty("gutter.tooltip.copied"))
        assertEquals("已复制到剪贴板", simplifiedChinese.getProperty("gutter.tooltip.copied"))
        assertEquals("已複製到剪貼簿", traditionalChinese.getProperty("gutter.tooltip.copied"))
        assertEquals("With Code Block", base.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
        assertEquals("코드 블록 포함", korean.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
        assertEquals("コードブロック付き", japanese.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
        assertEquals("包含代码块", simplifiedChinese.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
        assertEquals("包含程式碼區塊", traditionalChinese.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
    }

    @Test
    fun `notification copied key contains parameter`() {
        val msg = CopySelectionBundle.message("notification.copied", "myFile.kt")
        assertTrue(msg.contains("myFile.kt"), "Message should contain the parameter value")
    }

    @Test
    fun `widget and history keys resolve`() {
        listOf(
            "widget.tooltip",
            "widget.empty",
            "history.popup.title",
            "history.popup.empty",
            "history.popup.clear.all"
        ).forEach { key ->
            assertTrue(CopySelectionBundle.message(key).isNotBlank(), "Key '$key' should resolve to non-blank string")
        }
    }

    @Test
    fun `settings behavior keys resolve`() {
        listOf(
            "settings.include.code",
            "settings.path.type",
            "settings.path.relative",
            "settings.path.absolute",
            "settings.notification.enable",
            "settings.review.marketplace",
            "settings.trimming.enable",
            "settings.history.size",
            "settings.history.size.comment",
            "settings.template.label"
        ).forEach { key ->
            assertTrue(CopySelectionBundle.message(key).isNotBlank(), "Key '$key' should resolve to non-blank string")
        }
    }

    @Test
    fun `review prompt bundles keep exact English controls and localized action parity`() {
        val base = loadBundle("messages/CopySelectionBundle.properties")
        val keys = listOf(
            "review.prompt.title",
            "review.prompt.content",
            "review.prompt.action.review",
            "review.prompt.action.later",
            "review.prompt.action.never",
            "settings.review.marketplace",
        )

        ALL_BUNDLES.forEach { (localeName, resourcePath) ->
            val bundle = loadBundle(resourcePath)
            keys.forEach { key ->
                assertTrue(bundle.getProperty(key).isNotBlank(), "$localeName message '$key' should be non-blank")
            }
            assertEquals(
                bundle.getProperty("settings.review.marketplace"),
                bundle.getProperty("review.prompt.action.review"),
                "$localeName should use the same Marketplace action label in settings and the prompt",
            )
        }
        assertEquals("Review on Marketplace", base.getProperty("review.prompt.action.review"))
        assertEquals("Later", base.getProperty("review.prompt.action.later"))
        assertEquals("Don''t ask again", base.getProperty("review.prompt.action.never"))
        assertEquals("Review on Marketplace", base.getProperty("settings.review.marketplace"))
        assertEquals(
            base.getProperty("settings.review.marketplace"),
            base.getProperty("review.prompt.action.review"),
        )
        assertTrue(base.getProperty("review.prompt.content").contains("honest", ignoreCase = true))
    }

    private fun loadBundleKeys(resourcePath: String): Set<String> {
        return loadBundle(resourcePath).stringPropertyNames()
    }

    private fun loadBundle(resourcePath: String): Properties {
        val properties = Properties()
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resourcePath)) {
            "Missing test resource: $resourcePath"
        }
        stream.use(properties::load)
        return properties
    }

    private companion object {
        val LOCALIZED_BUNDLES = linkedMapOf(
            "Korean" to "messages/CopySelectionBundle_ko.properties",
            "Japanese" to "messages/CopySelectionBundle_ja.properties",
            "Simplified Chinese" to "messages/CopySelectionBundle_zh_CN.properties",
            "Traditional Chinese" to "messages/CopySelectionBundle_zh_TW.properties",
        )

        val ALL_BUNDLES = linkedMapOf("Base" to "messages/CopySelectionBundle.properties") + LOCALIZED_BUNDLES

        val LANGUAGE_NEUTRAL_KEYS = setOf(
            "notification.group",
            "settings.title",
            "settings.format.claude",
            "settings.template.variables.comment",
        )

        val MESSAGE_ARGUMENT = Regex("""\{(\d+)(?:,[^{}]+)?}""")

        val PERMALINK_FAILURE_KEYS = listOf(
            "notification.permalink.failed.missing.vcs.root",
            "notification.permalink.failed.git.metadata",
            "notification.permalink.failed.remote.host",
            "notification.permalink.failed.out.of.root",
            "notification.permalink.failed.io",
            "notification.permalink.failed.unexpected",
        )
    }

    private fun messageArgumentIndexes(pattern: String): Set<Int> =
        MESSAGE_ARGUMENT.findAll(pattern).mapTo(mutableSetOf()) { it.groupValues[1].toInt() }
}
