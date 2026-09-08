package com.github.hon454.copyselectioncontext

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

@Service(Service.Level.APP)
@State(
    name = "CopySelectionShortcutIntroduction",
    storages = [Storage(value = "copySelectionShortcuts.xml", roamingType = RoamingType.DISABLED)],
)
class CopySelectionShortcutIntroduction : PersistentStateComponent<CopySelectionShortcutIntroduction.State> {
    data class State(var introduced: Boolean = false)

    private var myState = State()

    @Synchronized
    override fun getState(): State = myState.copy()

    @Synchronized
    override fun loadState(state: State) {
        myState = state.copy()
    }

    @Synchronized
    internal fun claimIfFirst(): Boolean {
        if (myState.introduced) return false
        myState.introduced = true
        return true
    }

    @Synchronized
    private fun hasBeenIntroduced(): Boolean = myState.introduced

    internal fun show(project: Project): Boolean = show(project, PlatformShortcutIntroductionRuntime)

    internal fun show(project: Project, runtime: ShortcutIntroductionRuntime): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (project.isDisposed || runtime.unitTestMode || runtime.headlessEnvironment) return false

        val keymap = runtime.activeKeymap ?: return false
        if (hasBeenIntroduced()) return false
        val presentation = presentation(keymap)
        val notification = runtime.createNotification(
            CopySelectionBundle.message("shortcuts.intro.title"),
            CopySelectionBundle.message(
                "shortcuts.intro.content",
                presentation.defaultPrefix,
                presentation.currentCopy,
            ),
        )
        addProjectAction(
            notification,
            project,
            CopySelectionBundle.message("shortcuts.intro.action.settings"),
            runtime::openPluginSettings,
        )
        addProjectAction(
            notification,
            project,
            CopySelectionBundle.message("shortcuts.intro.action.keymap"),
            runtime::openKeymapSettings,
        )
        addProjectAction(
            notification,
            project,
            CopySelectionBundle.message("shortcuts.intro.action.dismiss"),
        ) {}

        // Notification construction can initialize services. Recheck at the visible boundary
        // so a closing project never consumes the application-wide introduction claim.
        if (project.isDisposed || !claimIfFirst()) return false
        runtime.publish(notification, project)
        return true
    }

    internal fun presentation(
        keymap: Keymap,
        renderShortcut: (Shortcut) -> String = { KeymapUtil.getShortcutText(it) },
    ): ShortcutIntroductionPresentation {
        val defaultCopy = CopySelectionShortcuts.defaultShortcuts(keymap).getValue(COPY_ACTION_ID)
        val currentCopy = keymap.getShortcuts(COPY_ACTION_ID)
            .joinToString(SHORTCUT_SEPARATOR, transform = renderShortcut)
            .ifEmpty { CopySelectionBundle.message("shortcuts.intro.unassigned") }
        return ShortcutIntroductionPresentation(
            defaultPrefix = CopyPreview.notification(KeymapUtil.getKeystrokeText(defaultCopy.firstKeyStroke)),
            currentCopy = CopyPreview.notification(currentCopy),
        )
    }

    private fun addProjectAction(
        notification: Notification,
        project: Project,
        text: String,
        action: (Project) -> Unit,
    ) {
        notification.addAction(NotificationAction.create(text) { _, currentNotification ->
            if (!project.isDisposed) {
                action(project)
                currentNotification.expire()
            }
        })
    }

    companion object {
        internal const val COPY_ACTION_ID = "CopySelectionContext.Copy"
        internal const val NOTIFICATION_GROUP_ID = "CopySelectionContext"
        internal const val PLUGIN_SETTINGS_ID = "CopySelectionContext.Settings"
        internal const val KEYMAP_SETTINGS_ID = "preferences.keymap"
        private const val SHORTCUT_SEPARATOR = " / "

        internal fun getInstance(): CopySelectionShortcutIntroduction =
            ApplicationManager.getApplication().getService(CopySelectionShortcutIntroduction::class.java)
    }
}

internal data class ShortcutIntroductionPresentation(
    val defaultPrefix: String,
    val currentCopy: String,
)

internal interface ShortcutIntroductionRuntime {
    val unitTestMode: Boolean
    val headlessEnvironment: Boolean
    val activeKeymap: Keymap?

    fun createNotification(title: String, content: String): Notification

    fun publish(notification: Notification, project: Project)

    fun openPluginSettings(project: Project)

    fun openKeymapSettings(project: Project)
}

private object PlatformShortcutIntroductionRuntime : ShortcutIntroductionRuntime {
    override val unitTestMode: Boolean
        get() = ApplicationManager.getApplication().isUnitTestMode

    override val headlessEnvironment: Boolean
        get() = ApplicationManager.getApplication().isHeadlessEnvironment

    override val activeKeymap: Keymap?
        get() = KeymapManager.getInstance().activeKeymap

    override fun createNotification(title: String, content: String): Notification =
        NotificationGroupManager.getInstance()
            .getNotificationGroup(CopySelectionShortcutIntroduction.NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)

    override fun publish(notification: Notification, project: Project) {
        notification.notify(project)
    }

    override fun openPluginSettings(project: Project) {
        openSettings(project, CopySelectionShortcutIntroduction.PLUGIN_SETTINGS_ID)
    }

    override fun openKeymapSettings(project: Project) {
        openSettings(project, CopySelectionShortcutIntroduction.KEYMAP_SETTINGS_ID)
    }

    private fun openSettings(project: Project, configurableId: String) {
        if (project.isDisposed) return
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            { configurable -> (configurable as? SearchableConfigurable)?.id == configurableId },
            {},
        )
    }
}

class CopySelectionShortcutStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val application = ApplicationManager.getApplication()
        if (project.isDisposed || application.isUnitTestMode || application.isHeadlessEnvironment) return

        application.invokeLater {
            if (!project.isDisposed && !application.isUnitTestMode && !application.isHeadlessEnvironment) {
                CopySelectionShortcutIntroduction.getInstance().show(project)
            }
        }
    }
}
