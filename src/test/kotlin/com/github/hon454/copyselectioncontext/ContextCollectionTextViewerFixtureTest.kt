package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Font
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.text.DefaultEditorKit
import javax.swing.text.PlainDocument
import javax.swing.text.Position

class ContextCollectionTextViewerFixtureTest : BasePlatformTestCase() {
    private val application get() = ApplicationManager.getApplication()

    fun testLatePreparedAAndQueuedAInstallCannotReplaceB() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val harness = Harness(ignoreCancellation = true) { text, font, context, leftToRight, checkCancelled ->
            if (text == "A") {
                entered.countDown()
                check(release.await(30, TimeUnit.SECONDS))
                // Simulate an uncooperative operation finishing after its request was cancelled.
                ContextCollectionTextViewer.prepareDocument(text, font, context, leftToRight) {}
            } else ContextCollectionTextViewer.prepareDocument(text, font, context, leftToRight, checkCancelled)
        }
        harness.viewer.show("A")
        val old = requireNotNull(harness.viewer.pendingRequest)
        val first = harness.start()
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS))
            harness.viewer.show("B")
            assertEquals(0, old.retainedCharacters())
            harness.compute()
            harness.flush()
            assertEquals("B", harness.area.text)
        } finally {
            release.countDown()
            first.get(30, TimeUnit.SECONDS)
        }
        harness.flush()
        assertEquals("B", harness.area.text)

        harness.viewer.show("queued A")
        harness.compute()
        val queued = requireNotNull(harness.viewer.pendingRequest)
        assertEquals(8, queued.retainedCharacters())
        harness.viewer.show("new B")
        harness.compute()
        harness.ui.removeLast().invoke() // deliberately install B before dispatching old A
        assertEquals("new B", harness.area.text)
        assertEquals(0, queued.retainedCharacters())
        harness.flush()
        assertEquals("new B", harness.area.text)
    }

    fun testDisposalBeforeWorkDuringPreparationAndWhileEdtInstallWaitsReleasesPayloads() {
        val before = Harness()
        before.viewer.show("never prepared")
        val pending = requireNotNull(before.viewer.pendingRequest)
        Disposer.dispose(before.viewer)
        assertEquals(0, pending.retainedCharacters())
        before.compute()
        before.flush()
        assertEquals(0, before.preparations)
        assertEquals("", before.area.text)

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val during = Harness(ignoreCancellation = true) { text, font, context, leftToRight, checkCancelled ->
            entered.countDown()
            check(release.await(30, TimeUnit.SECONDS))
            ContextCollectionTextViewer.prepareDocument(text, font, context, leftToRight, checkCancelled)
        }
        during.viewer.show("ا".repeat(131072))
        val preparing = requireNotNull(during.viewer.pendingRequest)
        val running = during.start()
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS))
            Disposer.dispose(during.viewer)
            assertEquals(0, preparing.retainedCharacters())
        } finally {
            release.countDown()
            running.get(30, TimeUnit.SECONDS)
        }
        during.flush()
        assertEquals("", during.area.text)

        val after = Harness()
        after.viewer.show("prepared document")
        after.compute()
        val prepared = requireNotNull(after.viewer.pendingRequest)
        assertTrue(prepared.retainedCharacters() > 0)
        Disposer.dispose(after.viewer)
        var documentChanges = 0
        after.area.addPropertyChangeListener("document") { documentChanges++ }
        after.flush()
        after.viewer.show("after disposal")
        assertEquals(0, prepared.retainedCharacters())
        assertEquals(0, documentChanges)
        assertEquals("", after.area.text)
    }

    fun testProjectDisposalReleasesInstalledAndQueuedViewerResources() {
        val directory = Files.createTempDirectory("collection-viewer-disposal-")
        val other = requireNotNull(ProjectManager.getInstance().createProject("viewer-disposal", directory.toString()))
        try {
            val installed = Harness(other)
            installed.viewer.show("installed")
            installed.compute()
            installed.flush()
            val waiting = Harness(other)
            waiting.viewer.show("queued")
            waiting.compute()
            val request = requireNotNull(waiting.viewer.pendingRequest)
            application.runWriteAction { Disposer.dispose(other) }
            var changes = 0
            waiting.area.addPropertyChangeListener("document") { changes++ }
            waiting.flush()
            assertEquals(0, changes)
            assertEquals(0, request.retainedCharacters())
            assertEquals("", waiting.area.text)
            assertEquals("", installed.area.text)
        } finally {
            if (!other.isDisposed) application.runWriteAction { Disposer.dispose(other) }
            directory.toFile().deleteRecursively()
        }
    }

    fun testInstallDoesNotReanalyseParagraphDirectionOnEdt() {
        for (orientation in listOf(java.awt.ComponentOrientation.LEFT_TO_RIGHT, java.awt.ComponentOrientation.RIGHT_TO_LEFT)) {
            lateinit var prepared: PlainDocument
            var directionWritesOnEdt = 0
            val harness = Harness { text, font, context, leftToRight, checkCancelled ->
                ContextCollectionTextViewer.prepareDocument(text, font, context, leftToRight, checkCancelled).also { document ->
                    val properties = object : java.util.Hashtable<Any, Any>() {
                        override fun put(key: Any, value: Any): Any? {
                            if (key == java.awt.font.TextAttribute.RUN_DIRECTION && application.isDispatchThread) directionWritesOnEdt++
                            return super.put(key, value)
                        }
                    }
                    for (key in document.documentProperties.keys()) properties[key] = document.getProperty(key)
                    document.documentProperties = properties
                    prepared = document
                }
            }
            harness.area.componentOrientation = orientation
            harness.viewer.show("ا".repeat(131072))
            harness.compute()
            assertEquals(!orientation.isLeftToRight, prepared.getProperty(java.awt.font.TextAttribute.RUN_DIRECTION))
            harness.flush()
            assertSame(prepared, harness.area.document)
            assertEquals(0, directionWritesOnEdt)
            val flipped = if (orientation.isLeftToRight) java.awt.ComponentOrientation.RIGHT_TO_LEFT else java.awt.ComponentOrientation.LEFT_TO_RIGHT
            harness.area.componentOrientation = flipped
            assertEquals(0, directionWritesOnEdt)
            assertEquals(0, harness.area.document.length)
            harness.compute()
            harness.flush()
            assertEquals(!flipped.isLeftToRight, prepared.getProperty(java.awt.font.TextAttribute.RUN_DIRECTION))
            assertSame(prepared, harness.area.document)
            assertEquals(131072, harness.area.document.length)
            assertEquals(0, directionWritesOnEdt)
        }
    }

    fun testNativeSelectAllAndRangeTransferKeepCompleteUnicodeDocumentsThrough4MiB() {
        val harness = Harness()
        val examples = listOf("small preview\n", "x".repeat(262144), "가".repeat(87381) + "x",
            "ا".repeat(131072), "abc\tمرحبا 123 שלום\n한글 😀𝄞 e\u0301\r\n", "ا".repeat(2097152))
        for (text in examples) {
            harness.viewer.show(text)
            harness.compute()
            harness.flush()
            val area = harness.area
            assertFalse(area.isEditable)
            assertTrue(area.isFocusable)
            assertTrue(area.focusTraversalKeysEnabled)
            assertEquals("fixture preview", area.accessibleContext.accessibleName)
            assertEquals(text, area.text)
            assertEquals(text.length, area.accessibleContext.accessibleText.charCount)
            area.actionMap.get(DefaultEditorKit.selectAllAction).actionPerformed(ActionEvent(area, 0, "select-all"))
            assertEquals(text, copied(harness))
            for ((from, to) in listOf(0 to minOf(7, text.length), text.length / 2 to minOf(text.length / 2 + 5, text.length),
                maxOf(0, text.length - 7) to text.length)) {
                area.select(from, to)
                assertEquals(text.substring(from, to), copied(harness))
            }
            val size = area.preferredSize
            area.setSize(maxOf(size.width, 500), maxOf(size.height, 300))
            area.caretPosition = text.length
            val end = area.modelToView2D(text.length)
            assertNotNull(end)
            area.scrollRectToVisible(end.bounds)
            assertEquals(text.length, area.caretPosition)
            paintViewport(harness, maxOf(0, end.x.toInt() - 499), maxOf(0, end.y.toInt() - 299))
        }
    }

    fun testVisualCaretMouseMappingSelectionAndFontChangeUsePreparedGeometry() {
        val harness = Harness()
        val text = "a مرحبا 123 שלום 😀 x\n\t한글 e\u0301\n"
        harness.viewer.show(text)
        harness.compute()
        harness.flush()
        harness.area.setSize(1000, 300)
        val area = harness.area
        val root = area.ui.getRootView(area).getView(0)
        assertTrue(root is ContextCollectionTextView)
        val allocation = java.awt.Rectangle(0, 0, 1000, 300)
        var position = 0
        var bias = Position.Bias.Forward
        val visited = mutableSetOf<Int>()
        repeat(text.length * 3) {
            visited.add(position)
            val bounds = root.modelToView(position, allocation, bias).bounds2D
            val returnedBias = arrayOf(Position.Bias.Forward)
            val hit = root.viewToModel(bounds.x.toFloat(), (bounds.centerY).toFloat(), allocation, returnedBias)
            val roundTrip = root.modelToView(hit, allocation, returnedBias[0]).bounds2D
            assertEquals(bounds.x, roundTrip.x, 1.0)
            val nextBias = arrayOf(Position.Bias.Forward)
            val next = root.getNextVisualPositionFrom(position, bias, allocation, SwingConstants.EAST, nextBias)
            assertTrue(next in 0..text.length)
            position = next
            bias = nextBias[0]
        }
        assertTrue("Visual navigation must reach the last line", visited.contains(text.length))
        assertTrue(visited.size > text.length / 2)
        area.select(2, 18)
        area.caret.isSelectionVisible = true
        paintViewport(harness, 0, 0)
        assertEquals(text.substring(2, 18), copied(harness))
        val document = area.document
        harness.viewer.show(text)
        assertSame(document, area.document)
        assertEquals(2, area.selectionStart)
        area.font = area.font.deriveFont(17f)
        assertEquals("", area.text)
        harness.compute()
        harness.flush()
        assertEquals(text, area.text)
    }

    fun testBothPanelViewersKeepExactPayloadAndCopyAllWorksBeforePreviewPreparationAtFourMiB() {
        val settings = CopySelectionSettings.getInstance()
        val original = settings.state.copy()
        val collection = ContextCollectionService.getInstance(project)
        val outputJobs = ArrayDeque<FutureTask<Unit>>()
        val ui = ArrayDeque<() -> Unit>()
        val viewers = mutableListOf<Harness>()
        val errors = mutableListOf<String>()
        var confirmations = 0
        fun flush() { while (ui.isNotEmpty()) ui.removeFirst().invoke() }
        fun computeOutput() {
            while (outputJobs.isNotEmpty()) {
                val job = outputJobs.removeFirst()
                application.executeOnPooledThread { job.run() }.get(30, TimeUnit.SECONDS)
                if (!job.isCancelled) job.get(30, TimeUnit.SECONDS)
            }
            flush()
        }
        try {
            settings.loadState(original.copy(enableNotification = false, analyticsEnabled = false,
                outputFormat = "template", customFormatTemplate = "{code}".repeat(16), codeTrimming = false))
            collection.clear()
            collection.setIncludeCode(true)
            val raw = "ا".repeat(131072)
            myFixture.configureByText("collection-bidi.txt", raw)
            myFixture.editor.selectionModel.setSelection(0, raw.length)
            assertTrue(collection.capture(myFixture.editor, myFixture.file.virtualFile, PathType.RELATIVE) is ContextCollectionAddResult.Added)
            val output = ContextCollectionOutputService.createForTest(project, collection, settings,
                { work -> FutureTask<Unit> { work() }.also(outputJobs::addLast) }, ui::addLast)
            val command = ContextCollectionCopyCommand.createForTest(project, output, CopyResultPublisher.getInstance(project),
                { confirmations++; true }, errors::add, ui::addLast)
            Disposer.register(testRootDisposable, output)
            Disposer.register(testRootDisposable, command)
            val panel = ContextCollectionPanel(project, collection, output, command::execute, viewerFactory = { owner, _ ->
                Harness(owner, register = false).also(viewers::add).viewer
            })
            Disposer.register(testRootDisposable, panel)
            panel.itemList.selectedIndex = 0
            computeOutput()
            val ready = (output.snapshot() as ContextCollectionOutputState.Computed).result as ContextCollectionOutputResult.Ready
            val expected = raw.repeat(16)
            assertEquals(4194304, ready.bytes)
            assertEquals(expected, ready.payload)
            assertTrue(panel.copyButton.isEnabled)
            assertEquals("", panel.outputViewer.component.text)
            panel.copyButton.doClick()
            flush()
            assertEquals(1, confirmations)
            assertEquals(expected, CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
            for (viewer in viewers) {
                while (viewer.jobs.isNotEmpty()) viewer.compute()
                viewer.flush()
            }
            assertEquals(raw, panel.capturedViewer.component.text)
            assertEquals(expected, panel.outputViewer.component.text)
            panel.outputViewer.component.selectAll()
            assertEquals(expected, copied(viewers[1]))
            assertEquals(4194304, copied(viewers[1]).toByteArray(Charsets.UTF_8).size)
            settings.state.customFormatTemplate = "{code}".repeat(17)
            settings.outputSettingsCommitted()
            assertEquals("", panel.outputViewer.component.text)
            assertFalse(panel.copyButton.isEnabled)
            computeOutput()
            assertEquals(ContextCollectionOutputResult.AboveHardLimit, (output.snapshot() as ContextCollectionOutputState.Computed).result)
            assertEquals("", panel.outputViewer.component.text)
            command.execute()
            flush()
            assertEquals(1, errors.size)
            assertEquals(1, confirmations)
            assertEquals(expected, CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor))
        } finally {
            collection.clear()
            settings.loadState(original)
        }
    }

    private fun copied(harness: Harness): String {
        val clipboard = Clipboard("collection viewer fixture")
        harness.area.transferHandler.exportToClipboard(harness.area, clipboard, TransferHandler.COPY)
        return clipboard.getData(DataFlavor.stringFlavor) as String
    }

    private fun paintViewport(harness: Harness, x: Int, y: Int) {
        val image = BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.translate(-x, -y)
            graphics.setClip(x, y, 500, 300)
            harness.area.paint(graphics)
        } finally { graphics.dispose() }
    }

    private inner class Harness(owner: Project = project, ignoreCancellation: Boolean = false, register: Boolean = true,
        prepare: (String, Font, FontRenderContext, Boolean, () -> Unit) -> PlainDocument = ContextCollectionTextViewer::prepareDocument) {
        val jobs = ArrayDeque<FutureTask<Unit>>()
        val ui = ConcurrentLinkedDeque<() -> Unit>()
        var preparations = 0
        val viewer = ContextCollectionTextViewer.createForTest(owner, "fixture preview", { work ->
            object : FutureTask<Unit>({ work() }) {
                override fun cancel(interrupt: Boolean): Boolean = if (ignoreCancellation) false else super.cancel(interrupt)
            }.also(jobs::addLast)
        }, ui::addLast) { text, font, context, leftToRight, checkCancelled ->
            check(!SwingUtilities.isEventDispatchThread())
            preparations++
            prepare(text, font, context, leftToRight, checkCancelled)
        }.also { if (register) Disposer.register(testRootDisposable, it) }
        val area get() = viewer.component
        fun start(): java.util.concurrent.Future<*> {
            val job = jobs.removeFirst()
            return application.executeOnPooledThread { job.run(); if (!job.isCancelled) job.get(30, TimeUnit.SECONDS) }
        }
        fun compute() { start().get(30, TimeUnit.SECONDS) }
        fun flush() { while (ui.isNotEmpty()) ui.removeFirst().invoke() }
    }
}
