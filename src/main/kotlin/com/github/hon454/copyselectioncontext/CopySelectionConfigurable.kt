package com.github.hon454.copyselectioncontext

import com.intellij.openapi.options.Configurable
import javax.swing.*

class CopySelectionConfigurable : Configurable {
    private var panel: JPanel? = null
    private var relativeRadio: JRadioButton? = null
    private var absoluteRadio: JRadioButton? = null
    private var includeCodeCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "Copy Selection Context"

    override fun createComponent(): JComponent {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        val pathLabel = JLabel("Default path type:")
        mainPanel.add(pathLabel)

        val buttonGroup = ButtonGroup()

        relativeRadio = JRadioButton("Relative (from project root)")
        absoluteRadio = JRadioButton("Absolute (full file system path)")

        buttonGroup.add(relativeRadio)
        buttonGroup.add(absoluteRadio)

        mainPanel.add(relativeRadio)
        mainPanel.add(absoluteRadio)

        mainPanel.add(Box.createVerticalStrut(12))

        val contentLabel = JLabel("Copy content:")
        mainPanel.add(contentLabel)

        includeCodeCheckbox = JCheckBox("Include code content (markdown code block)")
        mainPanel.add(includeCodeCheckbox)

        panel = mainPanel
        reset()

        return mainPanel
    }

    override fun isModified(): Boolean {
        val settings = CopySelectionSettings.getInstance()
        val state = settings.state

        val pathModified = when {
            relativeRadio?.isSelected == true && state.defaultPathType != PathType.RELATIVE -> true
            absoluteRadio?.isSelected == true && state.defaultPathType != PathType.ABSOLUTE -> true
            else -> false
        }

        val codeModified = includeCodeCheckbox?.isSelected != state.includeCodeContent

        return pathModified || codeModified
    }

    override fun apply() {
        val settings = CopySelectionSettings.getInstance()
        settings.state.defaultPathType = when {
            relativeRadio?.isSelected == true -> PathType.RELATIVE
            else -> PathType.ABSOLUTE
        }
        settings.state.includeCodeContent = includeCodeCheckbox?.isSelected == true
    }

    override fun reset() {
        val settings = CopySelectionSettings.getInstance()
        when (settings.state.defaultPathType) {
            PathType.RELATIVE -> relativeRadio?.isSelected = true
            PathType.ABSOLUTE -> absoluteRadio?.isSelected = true
        }
        includeCodeCheckbox?.isSelected = settings.state.includeCodeContent
    }

    override fun disposeUIResources() {
        panel = null
        relativeRadio = null
        absoluteRadio = null
        includeCodeCheckbox = null
    }
}
