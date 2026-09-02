package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

class CopySelectionBundleTest {

    @Test
    fun `Korean bundle has the same keys as the base bundle`() {
        val baseKeys = loadBundleKeys("messages/CopySelectionBundle.properties")
        val koreanKeys = loadBundleKeys("messages/CopySelectionBundle_ko.properties")

        assertEquals(
            baseKeys,
            koreanKeys,
            "Korean bundle keys must match the base bundle. " +
                "Missing: ${baseKeys - koreanKeys}; Extra: ${koreanKeys - baseKeys}"
        )
    }

    @Test
    fun `notification copied key resolves`() {
        val msg = CopySelectionBundle.message("notification.copied", "test")
        assertTrue(msg.contains("test"))
    }

    @Test
    fun `every permalink failure reason has English and Korean guidance`() {
        val base = loadBundle("messages/CopySelectionBundle.properties")
        val korean = loadBundle("messages/CopySelectionBundle_ko.properties")

        PERMALINK_FAILURE_KEYS.forEach { key ->
            assertTrue(base.getProperty(key).isNotBlank(), "Base message '$key' should be non-blank")
            assertTrue(korean.getProperty(key).isNotBlank(), "Korean message '$key' should be non-blank")
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
    fun `English and Korean bundles localize action tooltip and preset strings`() {
        val base = loadBundle("messages/CopySelectionBundle.properties")
        val korean = loadBundle("messages/CopySelectionBundle_ko.properties")
        val keys = listOf(
            "action.CopySelectionContext.Copy.text",
            "action.CopySelectionContext.CopyGitPermalink.text",
            "group.CopySelectionContextGroup.text",
            "gutter.tooltip.copied",
        ) + TemplatePreset.entries.map { it.messageKey }

        keys.forEach { key ->
            assertTrue(base.getProperty(key).isNotBlank(), "Base key '$key' should be non-blank")
            assertTrue(korean.getProperty(key).isNotBlank(), "Korean key '$key' should be non-blank")
        }
        TemplatePreset.entries.forEach { preset ->
            assertEquals(CopySelectionBundle.message(preset.messageKey), preset.toString())
        }

        assertEquals("Copy GitHub/GitLab Permalink", base.getProperty("action.CopySelectionContext.CopyGitPermalink.text"))
        assertEquals("GitHub/GitLab 퍼머링크 복사", korean.getProperty("action.CopySelectionContext.CopyGitPermalink.text"))
        assertEquals("Copied to clipboard", base.getProperty("gutter.tooltip.copied"))
        assertEquals("클립보드에 복사됨", korean.getProperty("gutter.tooltip.copied"))
        assertEquals("With Code Block", base.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
        assertEquals("코드 블록 포함", korean.getProperty(TemplatePreset.WITH_CODE_BLOCK.messageKey))
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
    fun `review prompt bundles keep exact English controls and Korean parity`() {
        val base = loadBundle("messages/CopySelectionBundle.properties")
        val korean = loadBundle("messages/CopySelectionBundle_ko.properties")
        val keys = listOf(
            "review.prompt.title",
            "review.prompt.content",
            "review.prompt.action.review",
            "review.prompt.action.later",
            "review.prompt.action.never",
            "settings.review.marketplace",
        )

        keys.forEach { key ->
            assertTrue(base.getProperty(key).isNotBlank(), "Base message '$key' should be non-blank")
            assertTrue(korean.getProperty(key).isNotBlank(), "Korean message '$key' should be non-blank")
        }
        assertEquals("Review", base.getProperty("review.prompt.action.review"))
        assertEquals("Later", base.getProperty("review.prompt.action.later"))
        assertEquals("Don''t ask again", base.getProperty("review.prompt.action.never"))
        assertEquals("Review on Marketplace", base.getProperty("settings.review.marketplace"))
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
        val PERMALINK_FAILURE_KEYS = listOf(
            "notification.permalink.failed.missing.vcs.root",
            "notification.permalink.failed.git.metadata",
            "notification.permalink.failed.remote.host",
            "notification.permalink.failed.out.of.root",
            "notification.permalink.failed.io",
            "notification.permalink.failed.unexpected",
        )
    }
}
