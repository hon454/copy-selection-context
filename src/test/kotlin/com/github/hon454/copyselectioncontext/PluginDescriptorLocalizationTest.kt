package com.github.hon454.copyselectioncontext

import java.nio.file.Path
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Document

class PluginDescriptorLocalizationTest {
    private val repositoryRoot = Path.of(System.getProperty("user.dir"))
    private val descriptor = loadDescriptor()
    private val bundles = linkedMapOf(
        "Base" to loadProperties("src/main/resources/messages/CopySelectionBundle.properties"),
        "Korean" to loadProperties("src/main/resources/messages/CopySelectionBundle_ko.properties"),
        "Japanese" to loadProperties("src/main/resources/messages/CopySelectionBundle_ja.properties"),
        "Simplified Chinese" to loadProperties("src/main/resources/messages/CopySelectionBundle_zh_CN.properties"),
        "Traditional Chinese" to loadProperties("src/main/resources/messages/CopySelectionBundle_zh_TW.properties"),
    )

    @Test
    fun `descriptor declares the action resource bundle`() {
        val bundles = descriptor.getElementsByTagName("resource-bundle")

        assertEquals(1, bundles.length)
        assertEquals("messages.CopySelectionBundle", bundles.item(0).textContent.trim())
    }

    @Test
    fun `registered actions and groups omit hardcoded presentations`() {
        listOf("action", "group").forEach { tagName ->
            val elements = descriptor.getElementsByTagName(tagName)
            for (index in 0 until elements.length) {
                val attributes = elements.item(index).attributes
                val id = assertNotNull(attributes.getNamedItem("id")).nodeValue
                assertFalse(attributes.getNamedItem("text") != null, "$tagName '$id' must not hardcode text")
                assertFalse(
                    attributes.getNamedItem("description") != null,
                    "$tagName '$id' must not hardcode description",
                )
            }
        }
    }

    @Test
    fun `every registered action and group has exactly named presentation keys`() {
        val expectedKeys = buildSet {
            listOf("action", "group").forEach { tagName ->
                val elements = descriptor.getElementsByTagName(tagName)
                for (index in 0 until elements.length) {
                    val id = assertNotNull(elements.item(index).attributes.getNamedItem("id")).nodeValue
                    add("$tagName.$id.text")
                    add("$tagName.$id.description")
                }
            }
        }

        bundles.forEach { (localeName, bundle) ->
            assertEquals(
                expectedKeys,
                descriptorPresentationKeys(bundle),
                "$localeName action and group presentation keys must match the descriptor",
            )
            expectedKeys.forEach { key ->
                assertTrue(bundle.getProperty(key).isNotBlank(), "$localeName bundle key '$key' must be non-blank")
            }
        }
    }

    @Test
    fun `collection add remains registered in the copy selection group`() {
        val actions = descriptor.getElementsByTagName("action")
        val action = (0 until actions.length).map { actions.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("id") == "CopySelectionContext.AddToCollection" }
        assertEquals("com.github.hon454.copyselectioncontext.AddToContextCollectionAction", action.getAttribute("class"))
        assertEquals("CopySelectionContextGroup", (action.parentNode as org.w3c.dom.Element).getAttribute("id"))
    }

    @Test
    fun `collection window is lazy right anchored and localized with an assignable open action`() {
        val windows = descriptor.getElementsByTagName("toolWindow")
        val window = windows.item(0) as org.w3c.dom.Element
        assertEquals("Context Collection", window.getAttribute("id"))
        assertEquals("right", window.getAttribute("anchor"))
        assertEquals("false", window.getAttribute("canCloseContents"))
        bundles.forEach { (_, bundle) -> assertTrue(bundle.getProperty("toolwindow.stripe.Context_Collection").isNotBlank()) }
        val actions = descriptor.getElementsByTagName("action")
        val open = (0 until actions.length).map { actions.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("id") == "CopySelectionContext.ShowCollection" }
        assertEquals("CopySelectionContextGroup", (open.parentNode as org.w3c.dom.Element).getAttribute("id"))
    }

    @Test
    fun `shortcut introduction is registered as one project startup activity`() {
        val activities = descriptor.getElementsByTagName("postStartupActivity")
        val matching = (0 until activities.length)
            .map { activities.item(it) as org.w3c.dom.Element }
            .filter {
                it.getAttribute("implementation") ==
                    "com.github.hon454.copyselectioncontext.CopySelectionShortcutStartupActivity"
            }

        assertEquals(1, matching.size)

        val groups = descriptor.getElementsByTagName("notificationGroup")
        val introductionGroup = (0 until groups.length)
            .map { groups.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("id") == CopySelectionShortcutIntroduction.NOTIFICATION_GROUP_ID }
        assertEquals("BALLOON", introductionGroup.getAttribute("displayType"))
    }

    @Test
    fun `descriptor and shared command table declare the same g prefix defaults`() {
        val actions = descriptor.getElementsByTagName("action")
        val registered = (0 until actions.length)
            .map { actions.item(it) as org.w3c.dom.Element }
            .associateBy { it.getAttribute("id") }
        assertEquals(CopySelectionShortcuts.commands.keys, registered.keys)
        assertEquals(9, CopySelectionShortcuts.commands.values.toSet().size)

        CopySelectionShortcuts.commands.forEach { (actionId, secondKey) ->
            val shortcuts = registered.getValue(actionId).getElementsByTagName("keyboard-shortcut")
            val declared = (0 until shortcuts.length)
                .map { shortcuts.item(it) as org.w3c.dom.Element }
                .associateBy { it.getAttribute("keymap") }
            assertEquals(CopySelectionShortcuts.macKeymapIds + "\$default", declared.keys, actionId)

            val default = declared.getValue("\$default")
            assertEquals("control alt shift G", default.getAttribute("first-keystroke"), actionId)
            assertEquals(secondKey, default.getAttribute("second-keystroke"), actionId)
            assertEquals("", default.getAttribute("replace-all"), actionId)

            CopySelectionShortcuts.macKeymapIds.forEach { keymapId ->
                val mac = declared.getValue(keymapId)
                assertEquals("meta alt shift G", mac.getAttribute("first-keystroke"), "$keymapId / $actionId")
                assertEquals(secondKey, mac.getAttribute("second-keystroke"), "$keymapId / $actionId")
                assertEquals("true", mac.getAttribute("replace-all"), "$keymapId / $actionId")
            }
        }

        val shortcutXml = registered.values
            .flatMap { action ->
                val shortcuts = action.getElementsByTagName("keyboard-shortcut")
                (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }
            }
        assertFalse(shortcutXml.any { it.getAttribute("second-keystroke").isEmpty() })
        assertFalse(shortcutXml.any { it.getAttribute("first-keystroke") in setOf("control alt C", "meta alt C") })
        assertFalse(shortcutXml.any { it.getAttribute("first-keystroke") == "control alt H" })
    }

    private fun descriptorPresentationKeys(properties: Properties): Set<String> =
        properties.stringPropertyNames().filterTo(mutableSetOf()) { key ->
            (key.startsWith("action.") || key.startsWith("group.")) &&
                (key.endsWith(".text") || key.endsWith(".description"))
        }

    private fun loadDescriptor(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder()
            .parse(repositoryRoot.resolve("src/main/resources/META-INF/plugin.xml").toFile())
    }

    private fun loadProperties(relativePath: String): Properties = Properties().apply {
        repositoryRoot.resolve(relativePath).inputStream().use(::load)
    }
}
