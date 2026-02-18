package com.github.hon454.copyselectioncontext

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopySelectionBundleTest {

    @Test
    fun `notification copied key resolves`() {
        val msg = CopySelectionBundle.message("notification.copied", "test")
        assertTrue(msg.contains("test"))
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
    fun `all action keys resolve`() {
        listOf(
            "action.copy.context.text",
            "action.copy.context.description",
            "action.copy.relative.text",
            "action.copy.absolute.text",
            "action.copy.with.code.text",
            "action.show.history.text",
            "action.show.history.description",
            "action.group.text",
            "action.group.description"
        ).forEach { key ->
            assertTrue(CopySelectionBundle.message(key).isNotBlank(), "Key '$key' should resolve to non-blank string")
        }
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
            "history.popup.empty"
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
            "settings.trimming.enable",
            "settings.history.size",
            "settings.template.label"
        ).forEach { key ->
            assertTrue(CopySelectionBundle.message(key).isNotBlank(), "Key '$key' should resolve to non-blank string")
        }
    }
}
