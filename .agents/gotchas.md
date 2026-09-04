# Gotchas & Pitfalls

## Critical Configuration

| # | Issue | Detail |
|---|-------|--------|
| 1 | **untilBuild** | Must NOT be set. Forward compatibility requirement. Setting it breaks plugin on future IDE versions. |
| 2 | **Kotlin stdlib** | `kotlin.stdlib.default.dependency=false` in gradle.properties. IDE provides its own; bundling causes classloader conflicts. |
| 3 | **SVG only** | Icons must be SVG (40x40px, <3KB). PNG not supported in modern IntelliJ Platform. |
| 4 | **Notification group** | Must register in plugin.xml before use: `<notificationGroup id="CopySelectionContext" displayType="BALLOON"/>`. ID is PascalCase, no spaces. |
| 5 | **Plugin naming** | Name cannot contain "Plugin" or "IntelliJ" (JetBrains Marketplace requirement). |

## IntelliJ Platform API Quirks

| # | Issue | Detail |
|---|-------|--------|
| 6 | **No selection** | Copy current line number when `hasSelection()` is false. Don't fail silently or show error. |
| 7 | **CommonDataKeys null** | Always null-check `e.getData()` results. Can return null outside editor context. |
| 8 | **Relative paths** | Use `virtualFile.path` + `project.basePath`. Handle null `basePath` (default project). |
| 9 | **Line numbers** | 0-indexed internally, display as 1-indexed. Always add 1 when formatting. |
| 10 | **StatusBarWidget** | Use the public `CustomStatusBarWidget` API through the Java adapter and create its Swing component lazily; direct Kotlin implementation generates deprecated default-method bridges, `EditorBasedWidget` is implementation-only, and `com.intellij.util.Consumer` is obsolete. |
| 11 | **Shortcut syntax** | Use `"control"` not `"ctrl"` in plugin.xml keyboard-shortcut elements. |
| 12 | **Plugin metadata** | Read this plugin's build-expanded version resource instead of `PluginManagerCore`; plugin descriptor lookup is internal in 2026.3+. Use the class-aware `DynamicBundle(Class, String)` constructor rather than subclassing it. |

`ContextCollectionToolWindowFactory` also inherits the public `ToolWindowFactory` API through a Java adapter. Direct Kotlin implementation generates bridges to internal `getIcon`, `getAnchor` and `manage` defaults, which fail plugin verification.

## Build & Deployment

| # | Issue | Detail |
|---|-------|--------|
| 13 | **Gradle memory** | IntelliJ Platform plugin is memory-intensive. May need `org.gradle.jvmargs=-Xmx2048m`. |
| 14 | **Verify first** | Always `./gradlew verifyPlugin` before publishing. Catches compatibility issues. |
| 15 | **Marketplace token** | `PUBLISH_TOKEN` env var or `~/.gradle/gradle.properties`. |
| 16 | **Windows builds** | Use PowerShell wrapper: `powershell.exe -Command ".\gradlew.bat buildPlugin"` or run via Java directly. |
| 17 | **Java 21** | `JAVA_HOME` must point to JDK 21 installation. Build fails with other versions. |
