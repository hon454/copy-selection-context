package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "CopySelectionAnalytics", storages = [Storage("copySelectionAnalytics.xml")])
class CopySelectionAnalytics : PersistentStateComponent<CopySelectionAnalytics.State> {

    data class State(
        var totalCopyCount: Int = 0,
        val formatUsage: MutableMap<String, Int> = mutableMapOf(),
        val languageUsage: MutableMap<String, Int> = mutableMapOf()
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    fun recordCopy(format: String, language: String = "") {
        myState.totalCopyCount++
        myState.formatUsage[format] = (myState.formatUsage[format] ?: 0) + 1
        if (language.isNotBlank()) {
            myState.languageUsage[language] = (myState.languageUsage[language] ?: 0) + 1
        }
    }

    fun getTotalCopyCount(): Int = myState.totalCopyCount
    fun getFormatUsage(): Map<String, Int> = myState.formatUsage.toMap()
    fun getLanguageUsage(): Map<String, Int> = myState.languageUsage.toMap()
    fun reset() { myState = State() }

    companion object {
        fun getInstance(): CopySelectionAnalytics =
            ApplicationManager.getApplication()
                .getService(CopySelectionAnalytics::class.java)
    }
}
