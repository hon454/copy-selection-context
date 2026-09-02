package com.github.hon454.copyselectioncontext

import com.intellij.openapi.components.RoamingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopySelectionReviewServiceTest {
    @Test
    fun `only the tenth successful copy is eligible for a prompt`() {
        val service = CopySelectionReviewService()

        val decisions = (1..CopySelectionReviewService.PROMPT_THRESHOLD).map {
            service.registerSuccessfulCopy(
                pluginVersion = "1.2.0",
                notificationsEnabled = true,
                unitTestMode = false,
                headlessEnvironment = false,
            )
        }

        assertTrue(decisions.take(9).none { it })
        assertTrue(decisions.last())
        assertEquals(10, service.sessionCopyCount())
        assertEquals("1.2.0", service.state.lastPromptedVersion)
        assertFalse(
            service.registerSuccessfulCopy(
                pluginVersion = "1.2.0",
                notificationsEnabled = true,
                unitTestMode = false,
                headlessEnvironment = false,
            ),
        )
        assertEquals(10, service.sessionCopyCount())
    }

    @Test
    fun `later suppresses the current version while a later version can prompt`() {
        val firstSession = CopySelectionReviewService()
        assertTrue(reachThreshold(firstSession, pluginVersion = "1.2.0"))

        val sameVersionSession = CopySelectionReviewService().apply {
            loadState(firstSession.state)
        }
        assertFalse(reachThreshold(sameVersionSession, pluginVersion = "1.2.0"))

        val upgradedSession = CopySelectionReviewService().apply {
            loadState(firstSession.state)
        }
        assertTrue(reachThreshold(upgradedSession, pluginVersion = "1.3.0"))
        assertEquals("1.3.0", upgradedSession.state.lastPromptedVersion)
    }

    @Test
    fun `opening Marketplace permanently suppresses prompts after persisted reload`() {
        val service = CopySelectionReviewService()
        var openedUrl: String? = null

        service.openMarketplaceReviewPage { openedUrl = it }

        val reloaded = CopySelectionReviewService().apply { loadState(service.state) }

        assertEquals(CopySelectionReviewService.MARKETPLACE_REVIEW_URL, openedUrl)
        assertTrue(reloaded.state.marketplacePageOpened)
        assertFalse(reachThreshold(reloaded, pluginVersion = "99.0.0"))
    }

    @Test
    fun `do not ask again permanently suppresses prompts after persisted reload`() {
        val service = CopySelectionReviewService()
        service.suppressPermanently()

        val reloaded = CopySelectionReviewService().apply { loadState(service.state) }

        assertTrue(reloaded.state.neverAskAgain)
        assertFalse(reachThreshold(reloaded, pluginVersion = "99.0.0"))
    }

    @Test
    fun `notification disabled leaves state unchanged and enabled copies get a fresh threshold`() {
        val originalState = CopySelectionReviewService.State(lastPromptedVersion = "1.1.0")
        val service = CopySelectionReviewService().apply { loadState(originalState) }

        val disabledDecisions = (1..CopySelectionReviewService.PROMPT_THRESHOLD).map {
            service.registerSuccessfulCopy(
                pluginVersion = "1.2.0",
                notificationsEnabled = false,
                unitTestMode = false,
                headlessEnvironment = false,
            )
        }

        assertTrue(disabledDecisions.none { it })
        assertEquals(0, service.sessionCopyCount())
        assertEquals(originalState, service.state)

        val enabledDecisions = (1..CopySelectionReviewService.PROMPT_THRESHOLD).map {
            service.registerSuccessfulCopy(
                pluginVersion = "1.2.0",
                notificationsEnabled = true,
                unitTestMode = false,
                headlessEnvironment = false,
            )
        }

        assertTrue(enabledDecisions.take(9).none { it })
        assertTrue(enabledDecisions.last())
        assertEquals(10, service.sessionCopyCount())
        assertEquals("1.2.0", service.state.lastPromptedVersion)
    }

    @Test
    fun `suppressed versions and permanent decisions do not advance the session counter`() {
        val currentVersion = CopySelectionReviewService().apply {
            loadState(CopySelectionReviewService.State(lastPromptedVersion = "1.2.0"))
        }
        val neverAskAgain = CopySelectionReviewService().apply {
            loadState(CopySelectionReviewService.State(neverAskAgain = true))
        }
        val marketplaceOpened = CopySelectionReviewService().apply {
            loadState(CopySelectionReviewService.State(marketplacePageOpened = true))
        }

        assertFalse(reachThreshold(currentVersion, pluginVersion = "1.2.0"))
        assertFalse(reachThreshold(neverAskAgain, pluginVersion = "99.0.0"))
        assertFalse(reachThreshold(marketplaceOpened, pluginVersion = "99.0.0"))
        assertEquals(0, currentVersion.sessionCopyCount())
        assertEquals(0, neverAskAgain.sessionCopyCount())
        assertEquals(0, marketplaceOpened.sessionCopyCount())
    }

    @Test
    fun `notification disabled alone never mutates a default service`() {
        val service = CopySelectionReviewService()
        val originalState = service.state

        val prompted = reachThreshold(
            service = service,
            pluginVersion = "1.2.0",
            notificationsEnabled = false,
        )

        assertFalse(prompted)
        assertEquals(0, service.sessionCopyCount())
        assertEquals(originalState, service.state)
    }

    @Test
    fun `unit test headless and missing-version contexts never prompt`() {
        val unitTestService = CopySelectionReviewService()
        val headlessService = CopySelectionReviewService()
        val missingVersionService = CopySelectionReviewService()

        assertFalse(reachThreshold(unitTestService, unitTestMode = true))
        assertFalse(reachThreshold(headlessService, headlessEnvironment = true))
        assertFalse(reachThreshold(missingVersionService, pluginVersion = null))
        assertEquals(0, unitTestService.sessionCopyCount())
        assertEquals(0, headlessService.sessionCopyCount())
        assertEquals(0, missingVersionService.sessionCopyCount())
        assertEquals("", unitTestService.state.lastPromptedVersion)
        assertEquals("", headlessService.state.lastPromptedVersion)
        assertEquals("", missingVersionService.state.lastPromptedVersion)
    }

    @Test
    fun `persisted state is defensive and excludes the session counter`() {
        val service = CopySelectionReviewService()
        service.registerSuccessfulCopy(
            pluginVersion = "1.2.0",
            notificationsEnabled = true,
            unitTestMode = false,
            headlessEnvironment = false,
        )

        val persisted = service.state
        persisted.lastPromptedVersion = "mutated"

        assertEquals("", service.state.lastPromptedVersion)
        assertEquals(
            setOf("lastPromptedVersion", "neverAskAgain", "marketplacePageOpened"),
            CopySelectionReviewService.State::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("\$") }
                .toSet(),
        )
        assertEquals(1, service.sessionCopyCount())
    }

    @Test
    fun `prompt control state uses local non-roaming storage`() {
        val stateAnnotation = requireNotNull(
            CopySelectionReviewService::class.java.getAnnotation(com.intellij.openapi.components.State::class.java),
        )

        assertEquals(1, stateAnnotation.storages.size)
        assertEquals("copySelectionReview.xml", stateAnnotation.storages.single().value)
        assertEquals(RoamingType.DISABLED, stateAnnotation.storages.single().roamingType)
    }

    @Test
    fun `Marketplace review URL is exact`() {
        assertEquals(
            "https://plugins.jetbrains.com/plugin/30262-copy-selection-context/reviews",
            CopySelectionReviewService.MARKETPLACE_REVIEW_URL,
        )
    }

    private fun reachThreshold(
        service: CopySelectionReviewService,
        pluginVersion: String? = "1.2.0",
        notificationsEnabled: Boolean = true,
        unitTestMode: Boolean = false,
        headlessEnvironment: Boolean = false,
    ): Boolean = (1..CopySelectionReviewService.PROMPT_THRESHOLD)
        .map {
            service.registerSuccessfulCopy(
                pluginVersion = pluginVersion,
                notificationsEnabled = notificationsEnabled,
                unitTestMode = unitTestMode,
                headlessEnvironment = headlessEnvironment,
            )
        }
        .last()
}
