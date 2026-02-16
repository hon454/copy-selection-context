package com.github.hon45.copyselectioncontext

import com.intellij.openapi.options.Configurable
import javax.swing.*

class CopySelectionConfigurable : Configurable {
    private var panel: JPanel? = null
    private var relativeRadio: JRadioButton? = null
    private var absoluteRadio: JRadioButton? = null

    override fun getDisplayName(): String = "Copy Selection Context"

    override fun createComponent(): JComponent {
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        val label = JLabel("Default path type:")
        mainPanel.add(label)

        val buttonGroup = ButtonGroup()

        relativeRadio = JRadioButton("Relative (from project root)")
        absoluteRadio = JRadioButton("Absolute (full file system path)")

        buttonGroup.add(relativeRadio)
        buttonGroup.add(absoluteRadio)

        mainPanel.add(relativeRadio)
        mainPanel.add(absoluteRadio)

        panel = mainPanel
        reset()

        return mainPanel
    }

    override fun isModified(): Boolean {
        val settings = CopySelectionSettings.getInstance()
        val currentType = settings.state.defaultPathType

        return when {
            relativeRadio?.isSelected == true && currentType != PathType.RELATIVE -> true
            absoluteRadio?.isSelected == true && currentType != PathType.ABSOLUTE -> true
            else -> false
        }
    }

    override fun apply() {
        val settings = CopySelectionSettings.getInstance()
        settings.state.defaultPathType = when {
            relativeRadio?.isSelected == true -> PathType.RELATIVE
            else -> PathType.ABSOLUTE
        }
    }

    override fun reset() {
        val settings = CopySelectionSettings.getInstance()
        when (settings.state.defaultPathType) {
            PathType.RELATIVE -> relativeRadio?.isSelected = true
            PathType.ABSOLUTE -> absoluteRadio?.isSelected = true
        }
    }

    override fun disposeUIResources() {
        panel = null
        relativeRadio = null
        absoluteRadio = null
    }
}
