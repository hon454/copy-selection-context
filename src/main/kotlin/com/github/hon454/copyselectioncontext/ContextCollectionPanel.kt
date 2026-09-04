package com.github.hon454.copyselectioncontext

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import com.intellij.icons.AllIcons
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

/** Content-owned UI. All mutations, output policy and copying remain in the shared services. */
internal class ContextCollectionPanel(
    private val project: Project,
    private val collection: ContextCollectionService = ContextCollectionService.getInstance(project),
    private val output: ContextCollectionOutputService = ContextCollectionOutputService.getInstance(project),
    private val copy: () -> Unit = { ContextCollectionCopyCommand.getInstance(project).execute() },
    private val confirmClear: (Int) -> Boolean = { count ->
        Messages.showDialog(project, msg("clear.confirm", count), msg("clear"),
            arrayOf(msg("clear"), CopySelectionBundle.message("collection.copy.cancel")), 1, Messages.getWarningIcon()) == 0
    },
    private val report: (String) -> Unit = { Messages.showWarningDialog(project, it, msg("clear")) },
) : JPanel(BorderLayout(0, 6)), Disposable {
    private val model = DefaultListModel<ContextCollectionItem>()
    val itemList = JBList(model)
    internal val capturedViewer = ContextCollectionTextViewer(project, msg("captured"))
    internal val outputViewer = ContextCollectionTextViewer(project, msg("output"))
    private val metadata = textLabel(msg("metadata.name"))
    internal val summary = textLabel(msg("summary.name"))
    internal val outputStatus = textLabel(msg("output.status"))
    private val formatLabel = JLabel()
    internal val includeCode = JCheckBox(msg("include"))
    internal val copyButton = button("copy", copy)
    internal val removeButton = iconButton("remove", AllIcons.General.Remove, ::removeSelected)
    internal val upButton = iconButton("up", AllIcons.Actions.MoveUp) { selected()?.let { collection.moveUp(it.id) } }
    internal val downButton = iconButton("down", AllIcons.Actions.MoveDown) { selected()?.let { collection.moveDown(it.id) } }
    internal val clearButton = iconButton("clear", AllIcons.Actions.GC, ::clearAll)
    private var snapshot = collection.snapshot()
    private var rebuilding = false
    private var disposed = false
    private var shownId: Long? = null

    init {
        border = JBUI.Borders.empty(12)
        preferredSize = Dimension(520, 650)
        minimumSize = Dimension(280, 300)
        Disposer.register(this, capturedViewer)
        Disposer.register(this, outputViewer)
        copyButton.icon = AllIcons.Actions.Copy
        copyButton.font = copyButton.font.deriveFont(Font.BOLD)
        val top = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(BorderLayout()).apply {
                add(heading(msg("list")), BorderLayout.WEST)
                add(copyButton, BorderLayout.EAST)
            })
            add(summary.apply {
                border = JBUI.Borders.empty(8, 0, 8, 0)
                toolTipText = msg("capacity")
                accessibleContext.accessibleDescription = msg("capacity")
            })
            add(JPanel(BorderLayout(6, 0)).apply {
                add(JPanel(FlowLayout(FlowLayout.LEADING, 2, 0)).apply {
                    add(removeButton); add(upButton); add(downButton); add(clearButton)
                }, BorderLayout.WEST)
                add(includeCode, BorderLayout.EAST)
            })
        }
        includeCode.accessibleContext.accessibleName = msg("include")
        includeCode.toolTipText = msg("include.hint")
        includeCode.addActionListener { if (!rebuilding) collection.setIncludeCode(includeCode.isSelected) }
        add(top, BorderLayout.NORTH)

        itemList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        itemList.visibleRowCount = 3
        itemList.emptyText.text = msg("empty.title")
        itemList.emptyText.appendLine(msg("empty.hint"))
        itemList.accessibleContext.accessibleName = msg("list")
        itemList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                val label = super.getListCellRendererComponent(list, value, index, selected, focus) as JLabel
                val item = value as? ContextCollectionItem ?: return label
                val status = collection.sourceTracker.snapshot().statuses[item.id]
                label.text = ContextCollectionPresentation.row(item, status)
                label.accessibleContext.accessibleName = ContextCollectionPresentation.details(item, status)
                label.border = JBUI.Borders.empty(8, 8)
                return label
            }
        }
        itemList.addListSelectionListener { if (!disposed && !rebuilding && !it.valueIsAdjusting) refreshSelection() }
        itemList.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                if (itemList.selectedIndex < 0 && !model.isEmpty) itemList.selectedIndex = 0
            }
        })
        itemList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "removeCapture")
        itemList.actionMap.put("removeCapture", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = removeSelected()
        })
        val captured = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(10, 0, 8, 0)
            add(JPanel(BorderLayout(0, 6)).apply {
                add(heading(msg("captured")), BorderLayout.NORTH)
                add(labelScroll(metadata, 3))
            }, BorderLayout.NORTH)
            add(viewerScroll(capturedViewer.component))
        }
        val finalOutput = JPanel(BorderLayout(0, 8)).apply {
            border = JBUI.Borders.empty(10, 0, 0, 0)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JPanel(BorderLayout()).apply {
                    add(heading(msg("output")), BorderLayout.WEST)
                    add(iconButton("settings", AllIcons.General.Settings) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, CopySelectionConfigurable::class.java)
                    }, BorderLayout.EAST)
                })
                add(formatLabel.apply { border = JBUI.Borders.empty(4, 0, 4, 0) })
                add(labelScroll(outputStatus, 2))
                components.forEach { (it as? JComponent)?.alignmentX = LEFT_ALIGNMENT }
            }, BorderLayout.NORTH)
            add(viewerScroll(outputViewer.component))
        }
        val viewers = split(captured, finalOutput, 0.32f)
        val workspace = split(viewerScroll(itemList).apply {
            border = JBUI.Borders.emptyTop(8)
            minimumSize = JBUI.size(100, 80)
        }, viewers, 0.34f)
        Disposer.register(this, Disposable { workspace.dispose(); viewers.dispose() })
        add(workspace)
        collection.subscribe(this) { if (!disposed) refreshCollection(it) }
        collection.sourceTracker.subscribe(this) { if (!disposed) { itemList.repaint(); refreshMetadata() } }
        output.subscribe(this) { if (!disposed) refreshOutput(it) }
        refreshCollection(collection.snapshot())
        refreshOutput(output.refresh())
    }

    private fun selected(): ContextCollectionItem? = itemList.selectedValue

    private fun refreshCollection(next: ContextCollectionSnapshot) {
        val index = ContextCollectionPresentation.selectedIndex(next.items, selected()?.id, itemList.selectedIndex)
        snapshot = next
        rebuilding = true
        model.clear()
        next.items.forEach(model::addElement)
        itemList.selectedIndex = index
        includeCode.isSelected = next.includeCode
        rebuilding = false
        summary.text = msg("summary", next.items.size, next.rawCodeBytes)
        refreshSelection()
        refreshOutput(output.snapshot())
    }

    private fun refreshSelection() {
        val item = selected()
        removeButton.isEnabled = item != null
        upButton.isEnabled = item != null && itemList.selectedIndex > 0
        downButton.isEnabled = item != null && itemList.selectedIndex < model.size - 1
        clearButton.isEnabled = !model.isEmpty
        if (shownId != item?.id) {
            shownId = item?.id
            capturedViewer.show(item?.code.orEmpty())
        }
        refreshMetadata()
    }

    private fun refreshMetadata() {
        metadata.text = selected()?.let { ContextCollectionPresentation.details(it, collection.sourceTracker.snapshot().statuses[it.id]) }
            ?: msg("select")
    }

    internal fun refreshOutput(state: ContextCollectionOutputState) {
        if (disposed) return
        val current = output.isCurrent(state.key)
        formatLabel.text = msg("format", OutputFormatOption.fromKey(state.key.options.format).toString())
        val result = (state as? ContextCollectionOutputState.Computed)?.result.takeIf { current }
        copyButton.isEnabled = result is ContextCollectionOutputResult.Ready
        outputStatus.text = when (result) {
            null -> msg("calculating")
            is ContextCollectionOutputResult.Ready -> msg("bytes", result.bytes) +
                result.warnings.sortedBy { it.ordinal }.joinToString("", prefix = "") {
                    "\n" + CopySelectionBundle.message("collection.copy.warning.${it.name.lowercase(java.util.Locale.ROOT)}")
                }
            else -> ContextCollectionCopyCommand.errorMessage(result)
        }
        outputViewer.show((result as? ContextCollectionOutputResult.Ready)?.payload.orEmpty())
    }

    internal fun removeSelected() {
        if (disposed) return
        val hadFocus = itemList.isFocusOwner
        selected()?.let { collection.remove(it.id) }
        if (model.isEmpty && hadFocus) includeCode.requestFocusInWindow()
    }

    internal fun clearAll() {
        if (disposed || snapshot.items.isEmpty()) return
        val confirmed = snapshot
        if (!confirmClear(confirmed.items.size) || disposed || project.isDisposed) return
        if (!collection.clear(confirmed.revision)) report(msg("clear.invalidated"))
        else includeCode.requestFocusInWindow()
    }

    override fun dispose() {
        disposed = true
        shownId = null
        model.clear()
        snapshot = ContextCollectionSnapshot(emptyList(), 0, true, 0)
        metadata.text = ""
        outputStatus.text = ""
        itemList.cellRenderer = DefaultListCellRenderer()
        removeAll()
    }

    companion object {
        private fun msg(key: String, vararg args: Any) = CopySelectionBundle.message("collection.ui.$key", *args)
        private fun button(key: String, action: () -> Unit) = JButton(msg(key)).apply {
            toolTipText = msg(key)
            accessibleContext.accessibleName = msg(key)
            addActionListener { action() }
        }
        private fun textLabel(name: String) = JTextArea().apply {
            isEditable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true
            font = javax.swing.UIManager.getFont("Label.font")
            accessibleContext.accessibleName = name
            focusTraversalKeysEnabled = true
        }
        private fun heading(text: String) = JLabel(text).apply { font = font.deriveFont(Font.BOLD) }
        private fun iconButton(key: String, icon: Icon, action: () -> Unit) = button(key, action).apply {
            text = ""
            this.icon = icon
            isContentAreaFilled = false
            preferredSize = JBUI.size(28, 28)
        }
        private fun viewerScroll(component: JComponent) = JBScrollPane(component).apply {
            border = null
            minimumSize = JBUI.size(100, 60)
        }
        // Wrapped paths and warnings stay fully selectable without consuming the code viewport.
        private fun labelScroll(label: JTextArea, rows: Int) = JBScrollPane(label.apply { this.rows = rows }).apply {
            border = null
            val height = label.getFontMetrics(label.font).height * rows
            preferredSize = Dimension(100, height)
            minimumSize = Dimension(100, height)
        }
        private fun split(top: JComponent, bottom: JComponent, weight: Float) =
            OnePixelSplitter(true, weight).apply {
                firstComponent = top
                secondComponent = bottom
                setHonorComponentsMinimumSize(true)
            }
    }
}
