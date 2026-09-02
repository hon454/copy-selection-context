package com.github.hon454.copyselectioncontext

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.Collections

@Service(Service.Level.APP)
@State(name = "CopySelectionAnalytics", storages = [Storage("copySelectionAnalytics.xml")])
class CopySelectionAnalytics : PersistentStateComponent<CopySelectionAnalytics.State> {

    data class State(
        var totalCopyCount: Int = 0,
        val formatUsage: MutableMap<String, Int> = mutableMapOf(),
        val languageUsage: MutableMap<String, Int> = mutableMapOf()
    )

    data class Snapshot(
        val totalCopyCount: Int,
        val formatUsage: Map<String, Int>,
        val languageUsage: Map<String, Int>,
    )

    private var myState = State()

    @Synchronized
    override fun getState(): State = myState.deepCopy()

    @Synchronized
    override fun loadState(state: State) {
        myState = state.deepCopy()
    }

    @Synchronized
    fun recordCopy(format: String, language: String = "") {
        myState.totalCopyCount++
        myState.formatUsage[format] = (myState.formatUsage[format] ?: 0) + 1
        if (language.isNotBlank()) {
            myState.languageUsage[language] = (myState.languageUsage[language] ?: 0) + 1
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        totalCopyCount = myState.totalCopyCount,
        formatUsage = immutableCopyOf(myState.formatUsage),
        languageUsage = immutableCopyOf(myState.languageUsage),
    )

    fun getTotalCopyCount(): Int = snapshot().totalCopyCount
    fun getFormatUsage(): Map<String, Int> = snapshot().formatUsage
    fun getLanguageUsage(): Map<String, Int> = snapshot().languageUsage

    @Synchronized
    fun reset() {
        myState = State()
    }

    private fun State.deepCopy() = State(
        totalCopyCount = totalCopyCount,
        formatUsage = LinkedHashMap(formatUsage),
        languageUsage = LinkedHashMap(languageUsage),
    )

    companion object {
        private fun <K, V> immutableCopyOf(source: Map<K, V>): Map<K, V> =
            Collections.unmodifiableMap(LinkedHashMap(source))

        fun getInstance(): CopySelectionAnalytics =
            ApplicationManager.getApplication()
                .getService(CopySelectionAnalytics::class.java)
    }
}
