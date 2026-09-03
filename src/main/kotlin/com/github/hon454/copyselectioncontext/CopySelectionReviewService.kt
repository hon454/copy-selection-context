package com.github.hon454.copyselectioncontext

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.APP)
@State(
    name = "CopySelectionReviewState",
    storages = [Storage(value = "copySelectionReview.xml", roamingType = RoamingType.DISABLED)],
)
class CopySelectionReviewService : PersistentStateComponent<CopySelectionReviewService.State> {
    data class State(
        var lastPromptedVersion: String = "",
        var neverAskAgain: Boolean = false,
        var marketplacePageOpened: Boolean = false,
    )

    private var myState = State()
    private val successfulCopiesThisSession = AtomicInteger()

    @Synchronized
    override fun getState(): State = myState.copy()

    @Synchronized
    override fun loadState(state: State) {
        myState = state.copy()
    }

    fun recordSuccessfulCopy(project: Project) {
        val application = ApplicationManager.getApplication()
        val shouldPrompt = registerSuccessfulCopy(
            pluginVersion = currentPluginVersion(),
            notificationsEnabled = CopySelectionSettings.getInstance().state.enableNotification,
            unitTestMode = application.isUnitTestMode,
            headlessEnvironment = application.isHeadlessEnvironment,
        )
        if (!shouldPrompt) return

        CopySelectionReviewNotifier.notify(
            project = project,
            onReview = ::openMarketplaceReviewPage,
            onNeverAskAgain = ::suppressPermanently,
        )
    }

    internal fun registerSuccessfulCopy(
        pluginVersion: String?,
        notificationsEnabled: Boolean,
        unitTestMode: Boolean,
        headlessEnvironment: Boolean,
    ): Boolean {
        if (!notificationsEnabled || unitTestMode || headlessEnvironment) return false
        if (pluginVersion.isNullOrBlank()) return false
        if (isPromptSuppressed(pluginVersion)) return false

        val copyCount = successfulCopiesThisSession.incrementAndGet()
        if (copyCount != PROMPT_THRESHOLD) return false

        return markPromptedIfEligible(pluginVersion)
    }

    fun openMarketplaceReviewPage() {
        openMarketplaceReviewPage(BrowserUtil::browse)
    }

    internal fun openMarketplaceReviewPage(openBrowser: (String) -> Unit) {
        markMarketplacePageOpened()
        openBrowser(MARKETPLACE_REVIEW_URL)
    }

    @Synchronized
    internal fun markMarketplacePageOpened() {
        myState.marketplacePageOpened = true
    }

    @Synchronized
    internal fun suppressPermanently() {
        myState.neverAskAgain = true
    }

    @Synchronized
    private fun isPromptSuppressed(pluginVersion: String): Boolean =
        myState.neverAskAgain ||
            myState.marketplacePageOpened ||
            myState.lastPromptedVersion == pluginVersion

    @Synchronized
    private fun markPromptedIfEligible(pluginVersion: String): Boolean {
        if (myState.neverAskAgain || myState.marketplacePageOpened) return false
        if (myState.lastPromptedVersion == pluginVersion) return false

        myState.lastPromptedVersion = pluginVersion
        return true
    }

    internal fun sessionCopyCount(): Int = successfulCopiesThisSession.get()

    internal fun currentPluginVersion(): String? = pluginVersion

    companion object {
        internal const val PROMPT_THRESHOLD = 10
        internal const val MARKETPLACE_REVIEW_URL =
            "https://plugins.jetbrains.com/plugin/30262-copy-selection-context/reviews"
        private const val VERSION_RESOURCE = "/META-INF/copy-selection-context-version.properties"
        private val pluginVersion: String? by lazy {
            CopySelectionReviewService::class.java.getResourceAsStream(VERSION_RESOURCE)
                ?.use { stream -> Properties().apply { load(stream) }.getProperty("version") }
                ?.takeIf(String::isNotBlank)
        }

        fun getInstance(): CopySelectionReviewService =
            ApplicationManager.getApplication().getService(CopySelectionReviewService::class.java)
    }
}
