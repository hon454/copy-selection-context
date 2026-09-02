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
    private val baseBundle = loadProperties("src/main/resources/messages/CopySelectionBundle.properties")
    private val koreanBundle = loadProperties("src/main/resources/messages/CopySelectionBundle_ko.properties")

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

        assertEquals(expectedKeys, descriptorPresentationKeys(baseBundle))
        assertEquals(expectedKeys, descriptorPresentationKeys(koreanBundle))
        expectedKeys.forEach { key ->
            assertTrue(baseBundle.getProperty(key).isNotBlank(), "Base bundle key '$key' must be non-blank")
            assertTrue(koreanBundle.getProperty(key).isNotBlank(), "Korean bundle key '$key' must be non-blank")
        }
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
