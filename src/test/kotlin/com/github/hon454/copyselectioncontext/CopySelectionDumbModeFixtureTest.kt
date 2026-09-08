package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertIs

class CopySelectionDumbModeFixtureTest : BasePlatformTestCase() {
    private lateinit var originalSettings: CopySelectionSettings.State
    private var originalClipboard: Transferable? = null
    private var activeDumbTask: DumbModeTask? = null
    private var releaseDumbTask: CountDownLatch? = null

    override fun setUp() {
        super.setUp()
        originalSettings = CopySelectionSettings.getInstance().state.copy()
        originalClipboard = CopyPasteManager.getInstance().contents
        CopySelectionSettings.getInstance().loadState(
            CopySelectionSettings.State(
                defaultPathType = PathType.RELATIVE,
                includeCodeContent = false,
                enableNotification = false,
                outputFormat = "pathline",
                codeTrimming = false,
                copyHistorySize = 10,
                customFormatTemplate = "",
                analyticsEnabled = false,
            ),
        )
        CopyHistoryService.getInstance(project).clear()
        CopyPasteManager.getInstance().setContents(StringSelection("dumb-mode-fixture-initial"))
    }

    override fun tearDown() {
        try {
            stopDumbMode()
            CopySelectionSettings.getInstance().loadState(originalSettings)
            CopyHistoryService.getInstance(project).clear()
            CopyPasteManager.getInstance().setContents(originalClipboard ?: StringSelection(""))
        } finally {
            super.tearDown()
        }
    }

    fun testRegisteredActionsRemainEligibleAndUseBackgroundUpdatesDuringDumbMode() {
        myFixture.configureByText("eligibility.kt", "fun main() = Unit<caret>")

        withDumbMode {
            AFFECTED_ACTION_IDS.forEach { actionId ->
                val action = registeredAction(actionId)
                assertIs<DumbAwareAction>(action, "$actionId must use the platform dumb-aware action base")
                assertTrue("$actionId must remain eligible during indexing", DumbService.isDumbAware(action))
                assertEquals("$actionId update thread", ActionUpdateThread.BGT, action.actionUpdateThread)

                val event = TestActionEvent.createTestEvent(action, actionContext())
                assertTrue(
                    "$actionId must pass action-system dumb-mode checks",
                    ActionUtil.lastUpdateAndCheckDumb(action, event, false),
                )
                assertTrue("$actionId must remain enabled and visible", event.presentation.isEnabledAndVisible)
            }
        }
    }

    fun testRegisteredStandardActionsCopyRangesCodeAndEveryCaretDuringDumbMode() {
        myFixture.configureByText(
            "dumb-copy.kt",
            "alpha\n<selection>beta\ngamma</selection>\nomega",
        )
        val editor = myFixture.editor
        requireNotNull(editor.caretModel.addCaret(editor.logicalToVisualPosition(LogicalPosition(3, 0))))
        CopySelectionSettings.getInstance().state.includeCodeContent = true

        withDumbMode {
            performRegistered(MAIN_ACTION_ID)
            val relativePath = relativePath()
            val relativeWithCode =
                "$relativePath:2-3\n```kotlin\nbeta\ngamma\n```\n\n" +
                    "$relativePath:4\n```kotlin\nomega\n```"
            assertEquals(relativeWithCode, clipboardText())

            performRegistered(RELATIVE_ACTION_ID)
            assertEquals("$relativePath:2-3\n\n$relativePath:4", clipboardText())

            performRegistered(ABSOLUTE_ACTION_ID)
            val absolutePath = myFixture.file.virtualFile.path
            assertEquals("$absolutePath:2-3\n\n$absolutePath:4", clipboardText())

            performRegistered(WITH_CODE_ACTION_ID)
            assertEquals(relativeWithCode, clipboardText())
        }
    }

    fun testHistoryRecopyAndGitMetadataPublicationPreserveOrderingDuringDumbMode() {
        myFixture.configureByText("publisher.kt", "first line\nsecond<caret> line\nthird line")
        val repositoryRoot = Files.createTempDirectory("copy-selection-dumb-mode-git")
        try {
            val source = repositoryRoot.resolve("src/Main.kt")
            write(repositoryRoot.resolve(".git/HEAD"), "ref: refs/heads/main\n")
            write(repositoryRoot.resolve(".git/refs/heads/main"), "$COMMIT_SHA\n")
            write(
                repositoryRoot.resolve(".git/config"),
                "[remote \"origin\"]\n    url = https://github.com/owner/repo.git\n",
            )
            write(source, "first line\nsecond line\nthird line\n")
            val historyContent = "previous copied context"
            CopyHistoryService.getInstance(project).addEntry(historyContent)

            withDumbMode {
                val historyAction = registeredAction(HISTORY_ACTION_ID)
                val historyEvent = TestActionEvent.createTestEvent(historyAction, actionContext())
                assertTrue(ActionUtil.lastUpdateAndCheckDumb(historyAction, historyEvent, false))
                assertTrue(historyEvent.presentation.isEnabledAndVisible)

                val entry = assertIs<CopyHistoryPopup.PopupItem.Entry>(
                    CopyHistoryPopup.createItems(CopyHistoryService.getInstance(project).getEntries()).first(),
                )
                CopyHistoryPopup.handleSelection(
                    service = CopyHistoryService.getInstance(project),
                    selected = entry,
                    copyContent = { CopyHistoryPopup.recopy(project, it) },
                    confirmClear = { false },
                )
                assertEquals(historyContent, clipboardText())

                val permalinkAction = assertIs<CopyGitPermalinkAction>(registeredAction(PERMALINK_ACTION_ID))
                val permalink = assertIs<GitPermalinkResult.Success<String>>(
                    permalinkAction.tryBuildPermalink(
                        repositoryRoot.toString(),
                        source.toString(),
                        listOf(Pair(2, 3)),
                    ),
                ).value
                val expectedPermalink =
                    "https://github.com/owner/repo/blob/$COMMIT_SHA/src/Main.kt#L2-L3"
                assertEquals(expectedPermalink, permalink)

                val publisher = permalinkAction.copyResultPublisher(project)
                val request = publisher.beginRequest()
                assertTrue(
                    publisher.publishIfCurrent(
                        request = request,
                        result = CopyResult(
                            content = permalink,
                            editor = myFixture.editor,
                            lineRanges = listOf(Pair(2, 3)),
                        ),
                        policy = CopyResultPolicy.GIT_PERMALINK,
                    ),
                )
                assertEquals(expectedPermalink, clipboardText())

                val staleRequest = publisher.beginRequest()
                assertIs<CopyPublicationOutcome.Published>(CopyHistoryPopup.recopy(project, "newer history copy"))
                assertFalse(
                    publisher.publishIfCurrent(
                        request = staleRequest,
                        result = CopyResult(content = "stale permalink"),
                        policy = CopyResultPolicy.GIT_PERMALINK,
                    ),
                )
                assertEquals("newer history copy", clipboardText())
            }
        } finally {
            FileUtil.delete(repositoryRoot.toFile())
        }
    }

    private fun withDumbMode(block: () -> Unit) {
        val dumbService = DumbService.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching(
            "project to be smart before the dumb-mode fixture",
            { !dumbService.isDumb },
            DUMB_MODE_TIMEOUT_MILLIS,
        )
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = object : DumbModeTask() {
            override fun performInDumbMode(indicator: ProgressIndicator) {
                entered.countDown()
                while (!release.await(10, TimeUnit.MILLISECONDS)) {
                    indicator.checkCanceled()
                }
            }
        }
        activeDumbTask = task
        releaseDumbTask = release
        dumbService.queueTask(task)
        PlatformTestUtil.waitWithEventsDispatching(
            "project to enter dumb mode",
            { entered.count == 0L && dumbService.isDumb },
            DUMB_MODE_TIMEOUT_MILLIS,
        )

        try {
            ApplicationManager.getApplication().assertIsDispatchThread()
            block()
        } finally {
            stopDumbMode()
        }
    }

    private fun stopDumbMode() {
        val task = activeDumbTask ?: return
        val dumbService = DumbService.getInstance(project)
        releaseDumbTask?.countDown()
        dumbService.cancelTask(task)
        activeDumbTask = null
        releaseDumbTask = null
        PlatformTestUtil.waitWithEventsDispatching(
            "project to return to smart mode",
            { !dumbService.isDumb },
            DUMB_MODE_TIMEOUT_MILLIS,
        )
    }

    private fun performRegistered(actionId: String) {
        val action = registeredAction(actionId)
        val event = TestActionEvent.createTestEvent(action, actionContext())
        assertTrue(
            "$actionId must pass action-system checks",
            ActionUtil.lastUpdateAndCheckDumb(action, event, false),
        )
        ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }

    private fun registeredAction(actionId: String): AnAction =
        requireNotNull(ActionManager.getInstance().getAction(actionId)) { "$actionId is not registered" }

    private fun actionContext(): DataContext = DataContext { dataId ->
        when (dataId) {
            CommonDataKeys.PROJECT.name -> project
            CommonDataKeys.EDITOR.name -> myFixture.editor
            CommonDataKeys.VIRTUAL_FILE.name -> myFixture.file.virtualFile
            else -> null
        }
    }

    private fun relativePath(): String =
        CopySelectionUtils.resolvePath(project, myFixture.file.virtualFile, PathType.RELATIVE)

    private fun clipboardText(): String? =
        CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private companion object {
        const val MAIN_ACTION_ID = "CopySelectionContext.Copy"
        const val HISTORY_ACTION_ID = "CopySelectionContext.ShowHistory"
        const val RELATIVE_ACTION_ID = "CopySelectionContext.CopyRelativePath"
        const val ABSOLUTE_ACTION_ID = "CopySelectionContext.CopyAbsolutePath"
        const val WITH_CODE_ACTION_ID = "CopySelectionContext.CopyWithCodeContent"
        const val PERMALINK_ACTION_ID = "CopySelectionContext.CopyGitPermalink"
        const val COMMIT_SHA = "0123456789abcdef0123456789abcdef01234567"
        const val DUMB_MODE_TIMEOUT_MILLIS = 10_000

        val AFFECTED_ACTION_IDS = listOf(
            MAIN_ACTION_ID,
            HISTORY_ACTION_ID,
            RELATIVE_ACTION_ID,
            ABSOLUTE_ACTION_ID,
            WITH_CODE_ACTION_ID,
            PERMALINK_ACTION_ID,
        )
    }
}
