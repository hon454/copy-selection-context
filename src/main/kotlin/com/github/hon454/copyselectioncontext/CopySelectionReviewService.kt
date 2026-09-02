package com.github.hon454.copyselectioncontext

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
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
        val copyCount = successfulCopiesThisSession.incrementAndGet()
        if (copyCount != PROMPT_THRESHOLD) return false
        if (!notificationsEnabled || unitTestMode || headlessEnvironment) return false
        if (pluginVersion.isNullOrBlank()) return false

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
    private fun markPromptedIfEligible(pluginVersion: String): Boolean {
        if (myState.neverAskAgain || myState.marketplacePageOpened) return false
        if (myState.lastPromptedVersion == pluginVersion) return false

        myState.lastPromptedVersion = pluginVersion
        return true
    }

    internal fun sessionCopyCount(): Int = successfulCopiesThisSession.get()

    private fun currentPluginVersion(): String? = PluginManagerCore
        .getPlugin(PluginId.getId(PLUGIN_ID))
        ?.version
        ?.takeIf(String::isNotBlank)

    companion object {
        internal const val PROMPT_THRESHOLD = 10
        internal const val MARKETPLACE_REVIEW_URL =
            "https://plugins.jetbrains.com/plugin/30262-copy-selection-context/reviews"
        private const val PLUGIN_ID = "com.github.hon454.copy-selection-context"

        fun getInstance(): CopySelectionReviewService =
            ApplicationManager.getApplication().getService(CopySelectionReviewService::class.java)
    }
}
