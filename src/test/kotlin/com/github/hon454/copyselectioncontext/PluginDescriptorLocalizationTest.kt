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
    fun `only the main copy action declares a single stroke default shortcut`() {
        val shortcutsByAction = registeredActions().mapValues { (_, action) ->
            val shortcuts = action.getElementsByTagName("keyboard-shortcut")
            (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }
        }
        assertEquals(setOf(COPY_ACTION_ID), shortcutsByAction.filterValues { it.isNotEmpty() }.keys)

        val declared = shortcutsByAction.getValue(COPY_ACTION_ID).associateBy { it.getAttribute("keymap") }
        assertEquals(MAC_KEYMAP_IDS + "\$default", declared.keys)
        val default = declared.getValue("\$default")
        assertEquals("control alt shift X", default.getAttribute("first-keystroke"))
        assertEquals("", default.getAttribute("replace-all"))
        MAC_KEYMAP_IDS.forEach { keymapId ->
            val mac = declared.getValue(keymapId)
            assertEquals("meta alt shift X", mac.getAttribute("first-keystroke"), keymapId)
            assertEquals("true", mac.getAttribute("replace-all"), keymapId)
        }

        val allShortcuts = shortcutsByAction.values.flatten()
        assertTrue(allShortcuts.all { it.getAttribute("second-keystroke").isEmpty() }, "two-stroke defaults are not allowed")
        val conflicting = setOf("control alt C", "meta alt C", "control alt shift C", "meta alt shift C", "control alt H")
        assertFalse(allShortcuts.any { it.getAttribute("first-keystroke") in conflicting })
    }

    @Test
    fun `main copy action sits directly before the tools submenu in the editor popup`() {
        val copy = registeredActions().getValue(COPY_ACTION_ID)
        assertEquals("actions", (copy.parentNode as org.w3c.dom.Element).tagName)
        val placement = copy.getElementsByTagName("add-to-group").item(0) as org.w3c.dom.Element
        assertEquals("EditorPopupMenu", placement.getAttribute("group-id"))
        assertEquals("before", placement.getAttribute("anchor"))
        assertEquals("CopySelectionContextGroup", placement.getAttribute("relative-to-action"))
    }

    private fun registeredActions(): Map<String, org.w3c.dom.Element> {
        val actions = descriptor.getElementsByTagName("action")
        return (0 until actions.length)
            .map { actions.item(it) as org.w3c.dom.Element }
            .associateBy { it.getAttribute("id") }
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

    private companion object {
        const val COPY_ACTION_ID = "CopySelectionContext.Copy"
        val MAC_KEYMAP_IDS = setOf(
            "Mac OS X",
            "Mac OS X 10.5+",
            "macOS System Shortcuts",
            "Visual Studio OSX",
            "ReSharper OSX",
            "VSCode OSX",
            "Visual Assist OSX",
            "Sublime Text (Mac OS X)",
        )
    }
}
