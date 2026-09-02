package com.github.hon454.copyselectioncontext

import com.intellij.configurationStore.ProjectStoreImpl
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class CopyHistoryPersistenceTest {
    private val projectRootFixture = tempPathFixture()

    private lateinit var fixtureDisposable: Disposable
    private lateinit var fixtureProject: StoreBackedMockProject
    private lateinit var fixtureStore: ProjectStoreImpl
    private lateinit var service: CopyHistoryService

    @BeforeEach
    fun setUpStoreFixture() {
        val projectRoot = projectRootFixture.get()
        writeLegacyHistory(projectRoot, listOf(LEGACY_MARKER, OVERSIZED_LEGACY_CONTENT))

        fixtureDisposable = Disposer.newDisposable("copy-history-store-fixture")
        fixtureProject = StoreBackedMockProject(fixtureDisposable)
        fixtureProject.extensionArea.registerExtensionPoint(
            "com.intellij.streamProviderFactory",
            "com.intellij.configurationStore.StreamProviderFactory",
            ExtensionPoint.Kind.INTERFACE,
            false,
        )
        fixtureProject.registerService(
            PathMacroManager::class.java,
            ProjectPathMacroManager.createInstance(
                { projectRoot.resolve(".idea").resolve("misc.xml").toString() },
                { projectRoot.toString() },
                { projectRoot.fileName.toString() },
            ),
        )
        registerProjectIdManager(fixtureProject)
        registerCoroutineScopeService(
            fixtureProject,
            fixtureDisposable,
            "com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEvents",
        )
        registerCoroutineScopeService(
            fixtureProject,
            fixtureDisposable,
            "com.jetbrains.rdserver.settings.BackendBroadcastSettingsStorageService",
        )

        fixtureStore = object : ProjectStoreImpl(fixtureProject) {
            override val serviceContainer: ComponentManagerImpl
                get() = ApplicationManager.getApplication() as ComponentManagerImpl
        }
        fixtureStore.setPath(projectRoot)
        fixtureProject.store = fixtureStore

        service = CopyHistoryService()
        fixtureProject.registerService(CopyHistoryService::class.java, service)
        fixtureStore.initComponent(service, null, PLUGIN_ID)
    }

    @AfterEach
    fun tearDownStoreFixture() {
        fixtureStore.release()
        Disposer.dispose(fixtureDisposable)
    }

    @Test
    fun `legacy history migrates to workspace and reloads from there`() {
        val projectRoot = projectRootFixture.get()
        val legacyStorage = legacyStorage(projectRoot)

        assertEquals(listOf(LEGACY_MARKER), historyContents())
        saveProjectStore()

        val workspaceStorage = workspaceStorage(projectRoot)
        assertTrue(workspaceStorage.exists())
        assertTrue(workspaceStorage.readText().contains(LEGACY_MARKER))
        assertFalse(workspaceStorage.readText().contains(OVERSIZED_LEGACY_CONTENT))
        assertFalse(legacyStorage.exists())

        service.clear()
        fixtureStore.reloadState(CopyHistoryService::class.java)
        assertEquals(listOf(LEGACY_MARKER), historyContents())
    }

    @Test
    fun `zero size clears persisted history across reload`() {
        val projectRoot = projectRootFixture.get()
        val legacyStorage = legacyStorage(projectRoot)

        assertEquals(listOf(LEGACY_MARKER), historyContents())
        service.addEntry("must-not-persist", maxSize = 0)
        saveProjectStore()

        assertFalse(legacyStorage.exists())
        assertWorkspaceDoesNotContain(projectRoot, LEGACY_MARKER, "must-not-persist")

        service.addEntry("in-memory-only")
        service.loadState(CopyHistoryService.State())
        fixtureStore.reloadState(CopyHistoryService::class.java)
        assertTrue(historyContents().isEmpty())
    }

    @Test
    fun `clear removes persisted history across reload`() {
        val projectRoot = projectRootFixture.get()
        val legacyStorage = legacyStorage(projectRoot)

        assertEquals(listOf(LEGACY_MARKER), historyContents())
        service.clear()
        saveProjectStore()

        assertFalse(legacyStorage.exists())
        assertWorkspaceDoesNotContain(projectRoot, LEGACY_MARKER)

        service.addEntry("in-memory-only")
        service.loadState(CopyHistoryService.State())
        fixtureStore.reloadState(CopyHistoryService::class.java)
        assertTrue(historyContents().isEmpty())
    }

    @Test
    fun `consecutive duplicate remains collapsed across persistence reload`() {
        service.addEntry("persisted-duplicate")
        service.addEntry("persisted-duplicate")
        saveProjectStore()

        service.clear()
        fixtureStore.reloadState(CopyHistoryService::class.java)

        assertEquals(1, historyContents().count { it == "persisted-duplicate" })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerProjectIdManager(project: StoreBackedMockProject) {
        val serviceClass =
            Class.forName("com.intellij.configurationStore.ProjectIdManager") as Class<Any>
        var projectId = "copy-history-store-fixture"
        val service = Proxy.newProxyInstance(
            serviceClass.classLoader,
            arrayOf(serviceClass),
        ) { _, method, arguments ->
            when (method.name) {
                "getId" -> projectId
                "setId" -> {
                    projectId = requireNotNull(arguments).single() as String
                    Unit
                }
                "toString" -> "ProjectIdManager($projectId)"
                else -> null
            }
        }
        project.registerService(serviceClass, service)
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerCoroutineScopeService(
        project: StoreBackedMockProject,
        parentDisposable: Disposable,
        className: String,
    ) {
        val serviceClass = Class.forName(className) as Class<Any>
        val scope = CoroutineScope(SupervisorJob())
        Disposer.register(parentDisposable) { scope.cancel() }
        val service = serviceClass.getConstructor(CoroutineScope::class.java).newInstance(scope)
        project.registerService(serviceClass, service)
    }

    private fun writeLegacyHistory(projectRoot: Path, contents: List<String>): Path {
        val component = Element("component").apply {
            setAttribute("name", COMPONENT_NAME)
            XmlSerializer.serializeInto(
                CopyHistoryService.State(
                    contents.mapIndexed { index, content ->
                        CopyHistoryService.HistoryEntry(content = content, timestamp = index.toLong())
                    }.toMutableList()
                ),
                this,
            )
        }
        val projectElement = Element("project").apply {
            setAttribute("version", "4")
            addContent(component)
        }
        return projectRoot.resolve(".idea").createDirectories()
            .resolve("copySelectionHistory.xml")
            .also { JDOMUtil.write(projectElement, it) }
    }

    private fun historyContents(): List<String> =
        CopyHistoryService.getInstance(fixtureProject).getEntries().map { it.content }

    private fun saveProjectStore() = runBlocking {
        fixtureStore.save(true)
    }

    private fun legacyStorage(projectRoot: Path): Path =
        projectRoot.resolve(".idea").resolve("copySelectionHistory.xml")

    private fun workspaceStorage(projectRoot: Path): Path =
        projectRoot.resolve(".idea").resolve("workspace.xml")

    private fun assertWorkspaceDoesNotContain(projectRoot: Path, vararg contents: String) {
        val workspaceStorage = workspaceStorage(projectRoot)
        if (!workspaceStorage.exists()) return

        val persisted = workspaceStorage.readText()
        contents.forEach { content ->
            assertFalse(persisted.contains(content), "Workspace must not contain '$content'")
        }
    }

    private class StoreBackedMockProject(parentDisposable: Disposable) :
        MockProject(null, parentDisposable),
        ComponentStoreOwner {
        lateinit var store: IComponentStore

        override val componentStore: IComponentStore
            get() = store
    }

    companion object {
        private const val COMPONENT_NAME = "CopySelectionHistory"
        private const val LEGACY_MARKER = "legacy-history-marker"
        private val OVERSIZED_LEGACY_CONTENT =
            "oversized-" + "x".repeat(CopyHistoryService.MAX_ENTRY_CONTENT_BYTES)
        private val PLUGIN_ID = PluginId.getId("com.github.hon454.copy-selection-context")
    }
}
