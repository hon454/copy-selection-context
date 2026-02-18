package com.github.hon454.copyselectioncontext

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull

class CopySelectionWebHelpProviderTest {
    private val provider = CopySelectionWebHelpProvider()

    @Test
    fun `main help topic returns github url`() {
        val url = provider.getHelpPageUrl(CopySelectionWebHelpProvider.HELP_TOPIC_MAIN)
        assertNotNull(url)
        assertTrue(url.startsWith("https://github.com/hon454/copy-selection-context"))
        assertTrue(url.contains("#readme"))
    }

    @Test
    fun `settings help topic returns github url`() {
        val url = provider.getHelpPageUrl(CopySelectionWebHelpProvider.HELP_TOPIC_SETTINGS)
        assertNotNull(url)
        assertTrue(url.startsWith("https://github.com/hon454/copy-selection-context"))
        assertTrue(url.contains("#settings"))
    }

    @Test
    fun `formats help topic returns github url`() {
        val url = provider.getHelpPageUrl(CopySelectionWebHelpProvider.HELP_TOPIC_FORMATS)
        assertNotNull(url)
        assertTrue(url.startsWith("https://github.com/hon454/copy-selection-context"))
        assertTrue(url.contains("#output-format"))
    }

    @Test
    fun `unknown topic returns null`() {
        val url = provider.getHelpPageUrl("unknown.topic")
        assertNull(url)
    }

    @Test
    fun `empty topic returns null`() {
        val url = provider.getHelpPageUrl("")
        assertNull(url)
    }
}
