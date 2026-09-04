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
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
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
    internal val removeButton = button("remove", ::removeSelected)
    internal val upButton = button("up") { selected()?.let { collection.moveUp(it.id) } }
    internal val downButton = button("down") { selected()?.let { collection.moveDown(it.id) } }
    internal val clearButton = button("clear", ::clearAll)
    private var snapshot = collection.snapshot()
    private var rebuilding = false
    private var disposed = false
    private var shownId: Long? = null

    init {
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
        preferredSize = Dimension(560, 650)
        minimumSize = Dimension(260, 250)
        Disposer.register(this, capturedViewer)
        Disposer.register(this, outputViewer)
        val top = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        top.add(JPanel(GridLayout(2, 3, 4, 4)).apply {
            add(copyButton); add(removeButton); add(clearButton); add(upButton); add(downButton)
            add(button("settings") { ShowSettingsUtil.getInstance().showSettingsDialog(project, CopySelectionConfigurable::class.java) })
        })
        includeCode.accessibleContext.accessibleName = msg("include")
        includeCode.toolTipText = msg("include.hint")
        includeCode.addActionListener { if (!rebuilding) collection.setIncludeCode(includeCode.isSelected) }
        top.add(includeCode)
        top.add(summary)
        top.add(formatLabel)
        top.add(outputStatus)
        add(top, BorderLayout.NORTH)

        itemList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        itemList.visibleRowCount = 3
        itemList.accessibleContext.accessibleName = msg("list")
        itemList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): java.awt.Component {
                val label = super.getListCellRendererComponent(list, value, index, selected, focus) as JLabel
                val item = value as? ContextCollectionItem ?: return label
                val status = collection.sourceTracker.snapshot().statuses[item.id]
                label.text = ContextCollectionPresentation.row(item, status)
                label.accessibleContext.accessibleName = ContextCollectionPresentation.details(item, status)
                label.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
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
        val captured = JPanel(BorderLayout(0, 3)).apply {
            add(JPanel(BorderLayout()).apply { add(JLabel(msg("captured")), BorderLayout.NORTH); add(metadata) }, BorderLayout.NORTH)
            add(JBScrollPane(capturedViewer.component))
        }
        val finalOutput = JPanel(BorderLayout()).apply {
            add(JLabel(msg("output")), BorderLayout.NORTH)
            add(JBScrollPane(outputViewer.component))
        }
        val viewers = split(captured, finalOutput, 0.40)
        add(split(JBScrollPane(itemList), viewers, 0.35))
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
            accessibleContext.accessibleName = name
            focusTraversalKeysEnabled = true
        }
        private fun split(top: JComponent, bottom: JComponent, weight: Double) =
            JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom).apply {
                resizeWeight = weight
                top.minimumSize = Dimension(100, 60)
                bottom.minimumSize = Dimension(100, 80)
                border = null
            }
    }
}
