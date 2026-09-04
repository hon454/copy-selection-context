package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.FutureTask

class ContextCollectionPanelFixtureTest : BasePlatformTestCase() {
    private lateinit var collection: ContextCollectionService
    private lateinit var settings: CopySelectionSettings
    private lateinit var original: CopySelectionSettings.State
    private val jobs = ArrayDeque<FutureTask<Unit>>()
    private val dispatches = ArrayDeque<() -> Unit>()
    private lateinit var output: ContextCollectionOutputService

    override fun setUp() {
        super.setUp()
        settings = CopySelectionSettings.getInstance()
        original = settings.state.copy()
        settings.loadState(CopySelectionSettings.State(enableNotification = false, outputFormat = "pathline"))
        collection = ContextCollectionService.getInstance(project)
        collection.clear()
        collection.setIncludeCode(true)
        output = ContextCollectionOutputService.createForTest(project, collection, settings,
            { task -> FutureTask<Unit> { task(); Unit }.also(jobs::addLast) }, dispatches::addLast)
        Disposer.register(testRootDisposable, output)
    }
    override fun tearDown() {
        try { settings.loadState(original) } finally { super.tearDown() }
    }
    private fun compute() {
        while (jobs.isNotEmpty()) jobs.removeFirst().run()
        while (dispatches.isNotEmpty()) dispatches.removeFirst()()
    }
    private fun capture(name: String, code: String) {
        myFixture.configureByText(name, code)
        myFixture.editor.selectionModel.setSelection(0, code.length)
        collection.capture(myFixture.editor, myFixture.file.virtualFile, PathType.RELATIVE)
    }
    private fun panel(confirm: (Int) -> Boolean = { false }, report: (String) -> Unit = {}, copy: () -> Unit = {}) =
        ContextCollectionPanel(project, collection, output, copy, confirm, report).also { Disposer.register(testRootDisposable, it) }

    fun testEmptyNoEditorActionAndPanelThenCapturePreservesSelectionAndMutationControls() {
        val action = ShowContextCollectionAction()
        val event = TestActionEvent.createTestEvent(action, DataContext { if (it == CommonDataKeys.PROJECT.name) project else null })
        action.update(event)
        assertTrue(event.presentation.isEnabled)
        val panel = panel()
        compute()
        assertFalse(panel.copyButton.isEnabled)
        assertFalse(panel.clearButton.isEnabled)
        assertTrue(panel.includeCode.isSelected)
        capture("A.kt", "old")
        capture("B.kt", "two")
        assertEquals(-1, panel.itemList.selectedIndex)
        panel.itemList.selectedIndex = 0
        val id = panel.itemList.selectedValue.id
        capture("C.kt", "three")
        assertEquals(id, panel.itemList.selectedValue.id)
        assertFalse(panel.upButton.isEnabled)
        panel.downButton.doClick()
        assertEquals(1, panel.itemList.selectedIndex)
        assertEquals(id, panel.itemList.selectedValue.id)
        panel.removeSelected()
        assertEquals(1, panel.itemList.selectedIndex)
        assertEquals("three", panel.itemList.selectedValue.code)
        panel.removeSelected()
        assertEquals(0, panel.itemList.selectedIndex)
    }

    fun testClearCancelAndRevisionConfirmationNeverClearNewCaptures() {
        capture("A.kt", "old")
        val canceled = panel()
        canceled.clearAll()
        assertEquals(1, collection.snapshot().items.size)
        var reports = 0
        val changed = panel(confirm = { capture("B.kt", "new"); true }, report = { reports++ })
        changed.clearAll()
        assertEquals(2, collection.snapshot().items.size)
        assertEquals(1, reports)
        panel(confirm = { true }).clearAll()
        assertTrue(collection.snapshot().items.isEmpty())
    }

    fun testSharedOutputStatesToggleAndSourceOnlyUpdatesAreIndependent() {
        capture("A.kt", "old")
        var copies = 0
        val panel = panel(copy = { copies++ })
        compute()
        val ready = output.snapshot()
        assertTrue(panel.copyButton.isEnabled)
        panel.copyButton.doClick()
        assertEquals(1, copies)
        panel.itemList.selectedIndex = 0
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("new") }
        assertSame(ready, output.snapshot())
        panel.includeCode.doClick()
        assertFalse(collection.snapshot().includeCode)
        assertFalse(settings.state.includeCodeContent)
        assertFalse(panel.copyButton.isEnabled)
        assertTrue(panel.outputStatus.text.contains("Calculating"))
        panel.refreshOutput(ready)
        assertFalse(panel.copyButton.isEnabled)
        compute()
        assertTrue(panel.copyButton.isEnabled)
        settings.state.outputFormat = "template"
        settings.state.customFormatTemplate = "{code}"
        settings.outputSettingsCommitted()
        compute()
        assertFalse(panel.copyButton.isEnabled)
        assertTrue(panel.outputStatus.text.contains("blank"))
        collection.setIncludeCode(true)
        settings.state.customFormatTemplate = "x".repeat(4194305)
        settings.outputSettingsCommitted()
        compute()
        assertFalse(panel.copyButton.isEnabled)
        assertTrue(panel.outputStatus.text.contains("exceeds 4 MiB"))
    }

    fun testDisposedPanelReleasesModelsAndDocumentsWhileServiceRetainsItems() {
        capture("A.kt", "old")
        val panel = panel()
        panel.itemList.selectedIndex = 0
        compute()
        assertFalse(panel.capturedViewer.component.isEditable)
        assertFalse(panel.outputViewer.component.isEditable)
        assertNotNull(panel.capturedViewer.component.accessibleContext.accessibleName)
        Disposer.dispose(panel)
        assertEquals(0, panel.itemList.model.size)
        assertEquals(0, panel.capturedViewer.component.document.length)
        assertEquals(0, panel.outputViewer.component.document.length)
        assertEquals(1, collection.snapshot().items.size)
        capture("B.kt", "new")
        compute()
        assertEquals(0, panel.itemList.model.size)
    }
}
