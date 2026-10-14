# Shortcut Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give only the main Copy Selection Context action a single-stroke default shortcut (`Ctrl+Alt+Shift+X` / `Cmd+Alt+Shift+X`), show it directly in the editor context menu above a renamed "Copy Selection Context Tools" submenu, add a Settings link to Keymap, and bring every document and the CHANGELOG in line with the real shortcuts.

**Architecture:** All shortcut and menu changes are declarative in `plugin.xml` plus resource bundles. The Settings link is one Kotlin UI DSL `link()` that navigates the open Settings dialog to Keymap through the public `Settings` API. Tests change only where they would break or where they guard a likely regression.

**Tech Stack:** Kotlin, IntelliJ Platform SDK 2024.3+ (Kotlin UI DSL v2, `com.intellij.openapi.options.ex.Settings`), Gradle IntelliJ Platform Plugin, JUnit 5 (`kotlin.test`), `BasePlatformTestCase`.

**Spec:** `docs/superpowers/specs/2026-10-14-shortcut-simplification-design.md`

## Global Constraints

- Main action `CopySelectionContext.Copy`: `$default` → `control alt shift X`. The eight Mac keymaps `Mac OS X`, `Mac OS X 10.5+`, `macOS System Shortcuts`, `Visual Studio OSX`, `ReSharper OSX`, `VSCode OSX`, `Visual Assist OSX` and `Sublime Text (Mac OS X)` → `meta alt shift X` with `replace-all="true"`.
- No `second-keystroke` anywhere. The other eight actions declare no `<keyboard-shortcut>`.
- No action ID or group ID changes. `CopySelectionContextGroup` stays the submenu ID.
- Top-level menu text stays "Copy Selection Context" with no `override-text`. Submenu text becomes "Copy Selection Context Tools", localized.
- No Internal or Deprecated APIs, no impl classes such as `KeymapPanel`, no error dialogs, no `untilBuild`, no PNG icons.
- Resource bundles are ISO-8859-1 with `\uXXXX` escapes. All five locales must have every key, and non-English values must be localized; `CopySelectionBundleTest` enforces both.
- Tests are pragmatic. Update existing tests and add no new test suites. UI and platform behavior goes to manual QA.
- Unit tests: `./gradlew test --tests '<FQN>'`. Platform fixture tests listed in `platformStateTestClasses` (`build.gradle.kts`): `./gradlew platformTest --tests '<FQN>'`.

## Review Focus

1. **Localized IDE (ko/ja/zh):** the Keymap link must still show all nine plugin actions. The filter uses the action-ID substring `CopySelectionContext`, which Keymap's `ActionsTreeUtil.createActionFilter` matches through `actionManager.getId(action)` regardless of UI language. Manual QA step in Task 4.
2. **Mac keymaps that only inherit** (Eclipse (Mac OS X), Xcode, QtCreator (Mac OS X), Visual Studio for Mac, macOS System Shortcuts) must resolve to `⌘⌥⇧X`, not `⌃⌥⇧X`. Task 1's fixture test checks `macOS System Shortcuts`, a child of `Mac OS X 10.5+`.
3. **Users who customized shortcuts on v1.7.0–1.8.1** must keep them after upgrading. This holds because action IDs are unchanged. Manual QA step in Task 4.
4. **Clicking the Keymap link when no Settings data context is available** must do nothing: no exception, no dialog. A null-safe chain in Task 2 handles this.
5. **The top-level menu item with no editor or file** must follow the existing `CopySelectionBaseAction` update logic, so it is disabled or hidden exactly as the submenu entry was. Manual QA step in Task 4.

---

### Task 1: Single-stroke default and context menu restructure

**Files:**
- Modify: `src/main/resources/META-INF/plugin.xml` (`<actions>` block)
- Modify: `src/main/resources/messages/CopySelectionBundle.properties:52`
- Modify: `src/main/resources/messages/CopySelectionBundle_ko.properties:52`
- Modify: `src/main/resources/messages/CopySelectionBundle_ja.properties:52`
- Modify: `src/main/resources/messages/CopySelectionBundle_zh_CN.properties:52`
- Modify: `src/main/resources/messages/CopySelectionBundle_zh_TW.properties:52`
- Delete: `src/main/kotlin/com/github/hon454/copyselectioncontext/CopySelectionShortcuts.kt`
- Test: `src/test/kotlin/com/github/hon454/copyselectioncontext/PluginDescriptorLocalizationTest.kt:97-134`
- Test: `src/test/kotlin/com/github/hon454/copyselectioncontext/CopySelectionShortcutFixtureTest.kt` (whole file)

**Spec refinement:** the spec says to shrink `CopySelectionShortcuts.kt`. After this change no main-source code uses it; only `activeDefaultShortcuts` ever existed for main code, and nothing called it. Delete the file and keep the few constants inside the two tests. `.agents/architecture.md` drops its row in Task 3.

**Interfaces:**
- Consumes: nothing
- Produces: the `plugin.xml` layout that Task 3's docs describe, and the bundle key `group.CopySelectionContextGroup.text` = "Copy Selection Context Tools"

- [ ] **Step 1: Replace the G-prefix descriptor test with the new contract**

In `PluginDescriptorLocalizationTest.kt`, delete the whole test `` `descriptor and shared command table declare the same g prefix defaults` `` (lines 97–134) and insert these two tests in its place:

```kotlin
    @Test
    fun `only the main copy action declares a single stroke default shortcut`() {
        val shortcutsByAction = registeredActions().mapValues { (_, action) ->
            val shortcuts = action.getElementsByTagName("keyboard-shortcut")
            (0 until shortcuts.length).map { shortcuts.item(it) as org.w3c.dom.Element }
        }
        assertEquals(setOf(COPY_ACTION_ID), shortcutsByAction.filterValues { it.isNotEmpty() }.keys)

        val declared = shortcutsByAction.getValue(COPY_ACTION_ID).associateBy { it.getAttribute("keymap") }
        assertEquals(MAC_KEYMAP_IDS + "\$default", declared.keys)
        val default = declared.getValue("\$default")
        assertEquals("control alt shift X", default.getAttribute("first-keystroke"))
        assertEquals("", default.getAttribute("replace-all"))
        MAC_KEYMAP_IDS.forEach { keymapId ->
            val mac = declared.getValue(keymapId)
            assertEquals("meta alt shift X", mac.getAttribute("first-keystroke"), keymapId)
            assertEquals("true", mac.getAttribute("replace-all"), keymapId)
        }

        val allShortcuts = shortcutsByAction.values.flatten()
        assertTrue(allShortcuts.all { it.getAttribute("second-keystroke").isEmpty() }, "two-stroke defaults are not allowed")
        val conflicting = setOf("control alt C", "meta alt C", "control alt shift C", "meta alt shift C", "control alt H")
        assertFalse(allShortcuts.any { it.getAttribute("first-keystroke") in conflicting })
    }

    @Test
    fun `main copy action sits directly before the tools submenu in the editor popup`() {
        val copy = registeredActions().getValue(COPY_ACTION_ID)
        assertEquals("actions", (copy.parentNode as org.w3c.dom.Element).tagName)
        val placement = copy.getElementsByTagName("add-to-group").item(0) as org.w3c.dom.Element
        assertEquals("EditorPopupMenu", placement.getAttribute("group-id"))
        assertEquals("before", placement.getAttribute("anchor"))
        assertEquals("CopySelectionContextGroup", placement.getAttribute("relative-to-action"))
    }

    private fun registeredActions(): Map<String, org.w3c.dom.Element> {
        val actions = descriptor.getElementsByTagName("action")
        return (0 until actions.length)
            .map { actions.item(it) as org.w3c.dom.Element }
            .associateBy { it.getAttribute("id") }
    }
```

At the end of the class, before the final `}`, add:

```kotlin
    private companion object {
        const val COPY_ACTION_ID = "CopySelectionContext.Copy"
        val MAC_KEYMAP_IDS = setOf(
            "Mac OS X",
            "Mac OS X 10.5+",
            "macOS System Shortcuts",
            "Visual Studio OSX",
            "ReSharper OSX",
            "VSCode OSX",
            "Visual Assist OSX",
            "Sublime Text (Mac OS X)",
        )
    }
```

- [ ] **Step 2: Replace the fixture test file**

Overwrite `CopySelectionShortcutFixtureTest.kt` with:

```kotlin
package com.github.hon454.copyselectioncontext

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

class CopySelectionShortcutFixtureTest : BasePlatformTestCase() {
    fun testMainCopyActionOwnsTheOnlyDefaultShortcutWithoutConflicts() {
        val manager = KeymapManager.getInstance()
        val expected = mapOf(
            "\$default" to "control alt shift X",
            "Mac OS X 10.5+" to "meta alt shift X",
            "macOS System Shortcuts" to "meta alt shift X",
        )

        expected.forEach { (keymapId, keystroke) ->
            val keymap = requireNotNull(manager.getKeymap(keymapId)) { keymapId }
            val shortcut = KeyboardShortcut(KeyStroke.getKeyStroke(keystroke), null)

            assertEquals(
                keymapId,
                listOf(shortcut),
                keymap.getShortcuts(COPY_ACTION_ID).filterIsInstance<KeyboardShortcut>(),
            )
            assertEquals(keymapId, listOf(COPY_ACTION_ID), keymap.getActionIdList(shortcut))
            UNBOUND_ACTION_IDS.forEach { actionId ->
                assertEmpty("$keymapId / $actionId", keymap.getShortcuts(actionId).toList())
            }
        }
    }

    private companion object {
        const val COPY_ACTION_ID = "CopySelectionContext.Copy"
        val UNBOUND_ACTION_IDS = listOf(
            "CopySelectionContext.ShowHistory",
            "CopySelectionContext.AddToCollection",
            "CopySelectionContext.ShowCollection",
            "CopySelectionContext.CopyAllCollection",
            "CopySelectionContext.CopyRelativePath",
            "CopySelectionContext.CopyAbsolutePath",
            "CopySelectionContext.CopyWithCodeContent",
            "CopySelectionContext.CopyGitPermalink",
        )
    }
}
```

- [ ] **Step 3: Delete the shared shortcut table**

```bash
git rm src/main/kotlin/com/github/hon454/copyselectioncontext/CopySelectionShortcuts.kt
```

- [ ] **Step 4: Run the descriptor tests and confirm they fail**

Run: `./gradlew test --tests 'com.github.hon454.copyselectioncontext.PluginDescriptorLocalizationTest'`
Expected: FAIL. `only the main copy action…` fails because nine actions declare shortcuts, and `main copy action sits directly before…` fails because Copy's parent is `group`.

- [ ] **Step 5: Restructure the `<actions>` block in plugin.xml**

Replace everything from `<!-- Submenu group for all Copy Selection Context actions -->` through the closing `</group>` (just before `</actions>`) with:

```xml
        <!-- Main Action: Copy Selection Context (settings-driven), shown directly in the editor popup -->
        <action id="CopySelectionContext.Copy"
                class="com.github.hon454.copyselectioncontext.CopySelectionContextAction"
                icon="/icons/copyContext.svg">
            <keyboard-shortcut keymap="$default" first-keystroke="control alt shift X"/>
            <keyboard-shortcut keymap="Mac OS X" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="Mac OS X 10.5+" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="macOS System Shortcuts" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="Visual Studio OSX" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="ReSharper OSX" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="VSCode OSX" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="Visual Assist OSX" first-keystroke="meta alt shift X" replace-all="true"/>
            <keyboard-shortcut keymap="Sublime Text (Mac OS X)" first-keystroke="meta alt shift X" replace-all="true"/>
            <add-to-group group-id="EditorPopupMenu" anchor="before" relative-to-action="CopySelectionContextGroup"/>
        </action>

        <!-- Copy Selection Context Tools submenu: no default shortcuts; users assign them in Keymap -->
        <group id="CopySelectionContextGroup"
               popup="true"
               icon="/icons/copyContext.svg">

            <action id="CopySelectionContext.ShowHistory"
                    class="com.github.hon454.copyselectioncontext.ShowCopyHistoryAction"/>

            <action id="CopySelectionContext.ShowCollection"
                    class="com.github.hon454.copyselectioncontext.ShowContextCollectionAction"/>
            <action id="CopySelectionContext.CopyAllCollection"
                    class="com.github.hon454.copyselectioncontext.CopyAllContextCollectionAction"/>
            <action id="CopySelectionContext.AddToCollection"
                    class="com.github.hon454.copyselectioncontext.AddToContextCollectionAction"/>

            <!-- Explicit copy actions -->
            <action id="CopySelectionContext.CopyRelativePath"
                    class="com.github.hon454.copyselectioncontext.CopyRelativePathAction"
                    icon="/icons/copyRelative.svg"/>

            <action id="CopySelectionContext.CopyAbsolutePath"
                    class="com.github.hon454.copyselectioncontext.CopyAbsolutePathAction"
                    icon="/icons/copyAbsolute.svg"/>

            <action id="CopySelectionContext.CopyWithCodeContent"
                    class="com.github.hon454.copyselectioncontext.CopyWithCodeContentAction"
                    icon="/icons/copyWithCode.svg"/>

            <action id="CopySelectionContext.CopyGitPermalink"
                    class="com.github.hon454.copyselectioncontext.CopyGitPermalinkAction"/>

            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </group>
```

The old `<separator/>` between Copy and ShowHistory is dropped, because Copy is no longer in the group.

Leave `<description>` alone for now; Task 3 rewrites it.

- [ ] **Step 6: Rename the submenu in all five bundles**

Replace line 52 in each file exactly:

| File | New line 52 |
|---|---|
| `CopySelectionBundle.properties` | `group.CopySelectionContextGroup.text=Copy Selection Context Tools` |
| `CopySelectionBundle_ko.properties` | `group.CopySelectionContextGroup.text=선택 컨텍스트 복사 도구` |
| `CopySelectionBundle_ja.properties` | `group.CopySelectionContextGroup.text=選択コンテキストのコピー ツール` |
| `CopySelectionBundle_zh_CN.properties` | `group.CopySelectionContextGroup.text=复制所选上下文工具` |
| `CopySelectionBundle_zh_TW.properties` | `group.CopySelectionContextGroup.text=複製所選內容工具` |

Decoded: 선택 컨텍스트 복사 도구 / 選択コンテキストのコピー ツール / 复制所选上下文工具 / 複製所選內容工具.

- [ ] **Step 7: Run the descriptor and bundle unit tests**

Run: `./gradlew test --tests 'com.github.hon454.copyselectioncontext.PluginDescriptorLocalizationTest' --tests 'com.github.hon454.copyselectioncontext.CopySelectionBundleTest'`
Expected: PASS.

- [ ] **Step 8: Run the fixture test**

Run: `./gradlew platformTest --tests 'com.github.hon454.copyselectioncontext.CopySelectionShortcutFixtureTest'`
Expected: PASS. If `getActionIdList` does not resolve on 2024.3, use `keymap.getActionIds(shortcut).toList()`; both are public `Keymap` API.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/META-INF/plugin.xml src/main/resources/messages/ src/test/kotlin/com/github/hon454/copyselectioncontext/PluginDescriptorLocalizationTest.kt src/test/kotlin/com/github/hon454/copyselectioncontext/CopySelectionShortcutFixtureTest.kt
git commit -m "feat(shortcuts): single-stroke main copy shortcut and direct context menu entry"
```

---

### Task 2: Settings link to Keymap

**Files:**
- Modify: `src/main/kotlin/com/github/hon454/copyselectioncontext/CopySelectionConfigurable.kt` (imports at lines 3–14, Behavior group at lines 147–157, companion object at line 291)
- Modify: all five `src/main/resources/messages/CopySelectionBundle*.properties` (add a key after line 36, `settings.review.marketplace`)

**Interfaces:**
- Consumes: nothing from Task 1. The filter relies on the action IDs keeping the prefix `CopySelectionContext`, which Task 1 preserves.
- Produces: bundle key `settings.keymap.configure`, which Task 3's README settings bullet names as **Configure shortcuts in Keymap…**

No automated test, per the spec's pragmatic scope. Existing `CopySelectionConfigurableTest` finds links by text, so it is unaffected.

- [ ] **Step 1: Add the bundle key to all five bundles**

Insert one new line directly after `settings.review.marketplace=…` (line 36) in each file:

| File | New line |
|---|---|
| `CopySelectionBundle.properties` | `settings.keymap.configure=Configure shortcuts in Keymap…` |
| `CopySelectionBundle_ko.properties` | `settings.keymap.configure=Keymap에서 단축키 설정…` |
| `CopySelectionBundle_ja.properties` | `settings.keymap.configure=Keymap でショートカットを設定…` |
| `CopySelectionBundle_zh_CN.properties` | `settings.keymap.configure=在 Keymap 中配置快捷键…` |
| `CopySelectionBundle_zh_TW.properties` | `settings.keymap.configure=在 Keymap 中設定快速鍵…` |

Decoded: Keymap에서 단축키 설정… / Keymap でショートカットを設定… / 在 Keymap 中配置快捷键… / 在 Keymap 中設定快速鍵…

- [ ] **Step 2: Add imports**

In `CopySelectionConfigurable.kt`, add these to the import list, keeping the existing alphabetical grouping:

```kotlin
import com.intellij.ide.DataManager
import com.intellij.openapi.options.ex.Settings
import java.awt.Component
```

- [ ] **Step 3: Add the link row to the Behavior group**

Change the Behavior group so the Keymap link row follows the Marketplace link row:

```kotlin
            group(CopySelectionBundle.message("settings.group.behavior")) {
                row {
                    checkBox(CopySelectionBundle.message("settings.notification.enable"))
                        .bindSelected(state::enableNotification)
                }
                row {
                    link(CopySelectionBundle.message("settings.review.marketplace")) {
                        openMarketplaceReviewPage()
                    }
                }
                row {
                    link(CopySelectionBundle.message("settings.keymap.configure")) { event ->
                        openKeymapSettings(event.source as? Component)
                    }
                }
            }
```

- [ ] **Step 4: Add the navigation helper and constants**

Add this private function to the class, just above `companion object` at line 291:

```kotlin
    private fun openKeymapSettings(source: Component?) {
        val settingsDialog = source
            ?.let { Settings.KEY.getData(DataManager.getInstance().getDataContext(it)) }
            ?: return
        val keymap = settingsDialog.find(KEYMAP_CONFIGURABLE_ID) ?: return
        settingsDialog.select(keymap, KEYMAP_FILTER)
    }
```

Add these inside the existing `companion object`, next to `TEMPLATE_COLUMNS`:

```kotlin
        private const val KEYMAP_CONFIGURABLE_ID = "preferences.keymap"

        // Keymap search also matches action IDs, so this lists every plugin action in any UI language.
        private const val KEYMAP_FILTER = "CopySelectionContext"
```

- [ ] **Step 5: Compile and run the settings and bundle tests**

Run: `./gradlew test --tests 'com.github.hon454.copyselectioncontext.CopySelectionConfigurableTest' --tests 'com.github.hon454.copyselectioncontext.CopySelectionBundleTest'`
Expected: PASS.

- [ ] **Step 6: Manual check in the sandbox IDE**

Run: `./gradlew runIde`. Open Settings → Tools → Copy Selection Context → click **Configure shortcuts in Keymap…**.
Expected: the dialog switches to Keymap with `CopySelectionContext` in the search field, and the tree shows all nine plugin actions. If the filter does not apply on 2024.3, report it and do not swap in impl APIs.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/github/hon454/copyselectioncontext/CopySelectionConfigurable.kt src/main/resources/messages/
git commit -m "feat(settings): add link to configure plugin shortcuts in Keymap"
```

---

### Task 3: Documentation, CLAUDE.md and CHANGELOG

**Files:**
- Test: `src/test/kotlin/com/github/hon454/copyselectioncontext/DocumentationSyncTest.kt` (required list line 67, new test)
- Modify: `src/main/resources/META-INF/plugin.xml` (`<description>` block)
- Modify: `README.md`, `README.ko.md`, `README.ja.md`, `README.zh-CN.md`, `README.zh-TW.md`
- Modify: `AGENTS.md:67`, `CONTRIBUTING.md:157`, `.agents/architecture.md:23,104,125`, `.agents/patterns.md:23`
- Modify: `CLAUDE.md` (Testing section)
- Modify: `CHANGELOG.md` (`[Unreleased]`)

**Interfaces:**
- Consumes: Task 1's menu layout and shortcut; Task 2's link text "Configure shortcuts in Keymap…"
- Produces: nothing

The README structure-parity test compares heading, list, table and fence line kinds across the five READMEs. Apply the same structural edit to all five: one fewer table row in "Keyboard Shortcuts", one fewer row in "Context Menu", and one more bullet in "Settings".

- [ ] **Step 1: Update DocumentationSyncTest**

In `` `localized readmes document current formats settings and copy feedback` ``, replace the list entry `"Ctrl+Alt+H",` (line 67) with:

```kotlin
            "Ctrl+Alt+Shift+X",
            "Cmd+Alt+Shift+X",
            "**Configure shortcuts in Keymap…**",
```

Add this test after that test method:

```kotlin
    @Test
    fun `docs advertise only the current default shortcut`() {
        val docs = readmePaths + listOf(
            "AGENTS.md",
            "CLAUDE.md",
            "CONTRIBUTING.md",
            ".agents/architecture.md",
            ".agents/patterns.md",
            "src/main/resources/META-INF/plugin.xml",
        )
        val obsolete = listOf("Ctrl+Alt+C", "Cmd+Alt+C", "Ctrl+Alt+H", "Shift+G")
        docs.forEach { path ->
            val content = repositoryRoot.resolve(path).readText()
            obsolete.forEach { shortcut ->
                assertFalse(content.contains(shortcut), "$path must not advertise obsolete shortcut $shortcut")
            }
        }
    }
```

`CHANGELOG.md` and `docs/superpowers/` are deliberately excluded, because they record history.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew test --tests 'com.github.hon454.copyselectioncontext.DocumentationSyncTest'`
Expected: FAIL. The READMEs lack `Ctrl+Alt+Shift+X`, and several files still contain `Ctrl+Alt+C`.

- [ ] **Step 3: Rewrite the plugin description**

In `plugin.xml` `<description>`:
- Replace the sentence `One shortcut (<code>Ctrl+Alt+C</code> / <code>Cmd+Alt+C</code>) copies your current selection` with `One shortcut (<code>Ctrl+Alt+Shift+X</code> / <code>Cmd+Alt+Shift+X</code>) copies your current selection`.
- Replace the first feature `<li>` with:
  `<li><strong>One-shortcut copy</strong> — <code>Ctrl+Alt+Shift+X</code> (Windows/Linux) / <code>Cmd+Alt+Shift+X</code> (macOS) copies path + line numbers based on your settings</li>`
- Replace the Private copy history `<li>` with:
  `<li><strong>Private copy history</strong> — Show Copy History opens locally stored history with clear-all and disable controls</li>`
- Replace the Context menu integration `<li>` with:
  `<li><strong>Context menu integration</strong> — Copy Selection Context runs directly from the editor right-click menu; every other action is in the Copy Selection Context Tools submenu</li>`
- Add after it:
  `<li><strong>Your own shortcuts</strong> — only the main copy has a default shortcut; assign others in <em>Settings → Keymap</em> or via <em>Configure shortcuts in Keymap…</em> in the plugin settings</li>`

- [ ] **Step 4: Update README.md**

Line 19: `- **One-shortcut copy** — `Ctrl+Alt+Shift+X` copies file path + line numbers instantly`
Line 23: `- **Copy history** — Browse recent copy history from the Copy Selection Context Tools submenu`
Line 32: `- **Context menu** — Run the main copy directly from the editor right-click menu; other actions live in the Copy Selection Context Tools submenu`

Replace lines 82–106 ("Keyboard Shortcuts" and "Context Menu" through the table end) with:

```markdown
### Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+Shift+X` | `Cmd+Alt+Shift+X` |

> Only the main copy has a default shortcut. Assign shortcuts to any other action in `Settings` → `Keymap`, or click **Configure shortcuts in Keymap…** in the plugin settings.

### Context Menu

Right-click in the editor → **Copy Selection Context** copies immediately using your settings. The **Copy Selection Context Tools** submenu holds the other actions:

| Action | Description |
|--------|-------------|
| Copy Relative Path with Line Numbers | Copy with project-relative path |
| Copy Absolute Path with Line Numbers | Copy with absolute path |
| Copy with Code Content | Copy path + lines + code block |
| Copy GitHub/GitLab Permalink | Copy Git remote permalink |
| Show Copy History | Show recent copy history popup |
| Add to Context Collection | Capture a selection or current line for the project session |
| Open Context Collection | Open, review and organize the project session collection |
| Copy All Context Collection | Copy the collection without an active editor |
```

Line 144: replace `` `Ctrl+Alt+H` opens the stored entries`` with `**Show Copy History** opens the stored entries`.
Settings list: after the `- **Review on Marketplace** — …` bullet add:
`- **Configure shortcuts in Keymap…** — Open Keymap filtered to this plugin's actions to assign your own shortcuts`
Line 175: replace `Copy Selection Context submenu` with `Copy Selection Context Tools submenu`. Lines 183 and 191 say only "the submenu" and stay unchanged.

- [ ] **Step 5: Update README.ko.md**

Line 19: `- **원클릭 복사** — `Ctrl+Alt+Shift+X` 하나로 파일 경로 + 라인 번호 복사`
Line 23: `- **복사 이력** — Copy Selection Context Tools 서브메뉴에서 최근 복사 이력 조회`
Line 32: `- **컨텍스트 메뉴** — 에디터 우클릭 메뉴에서 메인 복사를 바로 실행하고, 나머지 액션은 Copy Selection Context Tools 서브메뉴에서 사용`

Lines 82–105 become:

```markdown
### 단축키

| 액션 | Windows/Linux | macOS |
|------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+Shift+X` | `Cmd+Alt+Shift+X` |

> 기본 단축키는 메인 복사에만 있습니다. 다른 액션의 단축키는 `Settings` → `Keymap`에서 지정하거나, 플러그인 설정의 **Configure shortcuts in Keymap…** 링크를 이용하세요.

### 컨텍스트 메뉴

에디터에서 우클릭 → **Copy Selection Context**를 누르면 설정에 따라 바로 복사합니다. 나머지 액션은 **Copy Selection Context Tools** 서브메뉴에 있습니다:

| 액션 | 설명 |
|------|------|
| Copy Relative Path with Line Numbers | 프로젝트 상대 경로로 복사 |
| Copy Absolute Path with Line Numbers | 절대 경로로 복사 |
| Copy with Code Content | 경로 + 라인 + 코드 블록 복사 |
| Copy GitHub/GitLab Permalink | Git 원격 저장소 퍼머링크 복사 |
| Show Copy History | 최근 복사 이력 팝업 |
| 컨텍스트 컬렉션에 추가 | 프로젝트 세션에 선택 영역 또는 현재 줄 캡처 |
| 컨텍스트 컬렉션 열기 | 프로젝트 세션 컬렉션 열기·검토·정리 |
| 컨텍스트 컬렉션 전체 복사 | 활성 에디터 없이 컬렉션 복사 |
```

Line 144: replace `` `Ctrl+Alt+H`를 누르면`` with `**Show Copy History**를 실행하면`.
Settings list: after `- **Review on Marketplace** — …` add:
`- **Configure shortcuts in Keymap…** — 이 플러그인 액션만 걸러진 Keymap을 열어 원하는 단축키 지정`
Line 175: replace `Copy Selection Context 하위 메뉴` with `Copy Selection Context Tools 하위 메뉴`.

- [ ] **Step 6: Update README.ja.md**

Line 19: `- **ショートカットでコピー** — `Ctrl+Alt+Shift+X`でファイルパスと行番号をすぐにコピー`
Line 23: `- **コピー履歴** — Copy Selection Context Tools サブメニューから最近のコピー履歴を表示`
Line 32: `- **コンテキストメニュー** — エディターの右クリックメニューからメインのコピーを直接実行し、その他のアクションは Copy Selection Context Tools サブメニューから利用`

Lines 82–105 become:

```markdown
### キーボードショートカット

| アクション | Windows/Linux | macOS |
|------------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+Shift+X` | `Cmd+Alt+Shift+X` |

> 既定のショートカットはメインのコピーのみです。その他のアクションには`Settings` → `Keymap`、またはプラグイン設定の **Configure shortcuts in Keymap…** からショートカットを割り当てられます。

### コンテキストメニュー

エディター内で右クリック → **Copy Selection Context** で設定に基づいてすぐにコピーします。その他のアクションは **Copy Selection Context Tools** サブメニューにあります：

| アクション | 説明 |
|------------|------|
| Copy Relative Path with Line Numbers | プロジェクト相対パスでコピー |
| Copy Absolute Path with Line Numbers | 絶対パスでコピー |
| Copy with Code Content | パス、行番号、コードブロックをコピー |
| Copy GitHub/GitLab Permalink | Gitリモートリポジトリのパーマリンクをコピー |
| Show Copy History | 最近のコピー履歴ポップアップを表示 |
| コンテキストコレクションに追加 | プロジェクトセッションに選択範囲または現在行を取得 |
| コンテキストコレクションを開く | プロジェクトセッションのコレクションを開いて確認・整理 |
| コンテキストコレクションをすべてコピー | エディターなしでコレクションをコピー |
```

Line 144: replace `` `Ctrl+Alt+H`で保存済みの項目を開き`` with `**Show Copy History** で保存済みの項目を開き`.
Settings list: after `- **Review on Marketplace** — …` add:
`- **Configure shortcuts in Keymap…** — このプラグインのアクションに絞り込んだ Keymap を開き、独自のショートカットを割り当て`
Line 175: replace `Copy Selection Context サブメニュー` with `Copy Selection Context Tools サブメニュー`.

- [ ] **Step 7: Update README.zh-CN.md**

Line 19: `- **快捷键复制** — 按下 `Ctrl+Alt+Shift+X`，立即复制文件路径和行号`
Line 23: `- **复制历史** — 从 Copy Selection Context Tools 子菜单浏览最近的复制历史`
Line 32: `- **上下文菜单** — 从编辑器右键菜单直接运行主复制操作，其他操作位于 Copy Selection Context Tools 子菜单`

Lines 82–105 become:

```markdown
### 键盘快捷键

| 操作 | Windows/Linux | macOS |
|------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+Shift+X` | `Cmd+Alt+Shift+X` |

> 只有主复制操作有默认快捷键。可在 `Settings` → `Keymap` 中，或通过插件设置中的 **Configure shortcuts in Keymap…** 为其他操作分配快捷键。

### 上下文菜单

在编辑器中右键单击 → **Copy Selection Context** 会按设置立即复制。其他操作位于 **Copy Selection Context Tools** 子菜单：

| 操作 | 说明 |
|------|------|
| Copy Relative Path with Line Numbers | 使用项目相对路径复制 |
| Copy Absolute Path with Line Numbers | 使用绝对路径复制 |
| Copy with Code Content | 复制路径、行号和代码块 |
| Copy GitHub/GitLab Permalink | 复制 Git 远程仓库永久链接 |
| Show Copy History | 显示最近的复制历史弹窗 |
| 添加到上下文集合 | 在项目会话中捕获选择内容或当前行 |
| 打开上下文集合 | 打开、检查并整理项目会话集合 |
| 复制整个上下文集合 | 无需活动编辑器即可复制集合 |
```

Line 144: replace `` 按下 `Ctrl+Alt+H` 可打开已存储的条目`` with `执行 **Show Copy History** 可打开已存储的条目`.
Settings list: after `- **Review on Marketplace** — …` add:
`- **Configure shortcuts in Keymap…** — 打开仅显示本插件操作的 Keymap，以分配自己的快捷键`
Line 175: replace `Copy Selection Context 子菜单` with `Copy Selection Context Tools 子菜单`.

- [ ] **Step 8: Update README.zh-TW.md**

Line 19: `- **快速鍵複製** — 按下 `Ctrl+Alt+Shift+X`，立即複製檔案路徑和行號`
Line 23: `- **複製歷史記錄** — 從 Copy Selection Context Tools 子選單瀏覽最近的複製歷史記錄`
Line 32: `- **內容選單** — 從編輯器右鍵選單直接執行主要複製操作，其他操作位於 Copy Selection Context Tools 子選單`

Lines 82–105 become:

```markdown
### 鍵盤快速鍵

| 操作 | Windows/Linux | macOS |
|------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+Shift+X` | `Cmd+Alt+Shift+X` |

> 只有主要複製操作有預設快速鍵。可在 `Settings` → `Keymap` 中，或透過外掛設定中的 **Configure shortcuts in Keymap…** 為其他操作指派快速鍵。

### 內容選單

在編輯器中按一下滑鼠右鍵 → **Copy Selection Context** 會依設定立即複製。其他操作位於 **Copy Selection Context Tools** 子選單：

| 操作 | 說明 |
|------|------|
| Copy Relative Path with Line Numbers | 使用專案相對路徑複製 |
| Copy Absolute Path with Line Numbers | 使用絕對路徑複製 |
| Copy with Code Content | 複製路徑、行號和程式碼區塊 |
| Copy GitHub/GitLab Permalink | 複製 Git 遠端儲存庫永久連結 |
| Show Copy History | 顯示最近的複製歷史記錄彈出視窗 |
| 加入上下文集合 | 在專案工作階段擷取選取內容或目前行 |
| 開啟上下文集合 | 開啟、檢查並整理專案工作階段集合 |
| 複製整個上下文集合 | 無需作用中編輯器即可複製集合 |
```

Line 144: replace `` 按下 `Ctrl+Alt+H` 可開啟已儲存項目`` with `執行 **Show Copy History** 可開啟已儲存項目`.
Settings list: after `- **Review on Marketplace** — …` add:
`- **Configure shortcuts in Keymap…** — 開啟僅顯示本外掛操作的 Keymap，以指派自己的快速鍵`
Line 175: replace `Copy Selection Context 子選單` with `Copy Selection Context Tools 子選單`.

- [ ] **Step 9: Update developer docs**

- `AGENTS.md:67` → `| `CopySelectionContextAction.kt` | Main unified action (`Ctrl+Alt+Shift+X` / `Cmd+Alt+Shift+X`, the only default shortcut) |`
- `CONTRIBUTING.md:157` → `├── CopySelectionContextAction.kt    # Main unified action (Ctrl+Alt+Shift+X)`
- `.agents/architecture.md:23` → `1. The user invokes `CopySelectionContextAction` with `Ctrl+Alt+Shift+X` / `Cmd+Alt+Shift+X` or directly from the editor context menu, or chooses an explicit path/code action from the Copy Selection Context Tools submenu.`
- `.agents/architecture.md:104` → `| `ShowContextCollectionAction.kt` | No-editor, localized open action without a default shortcut (assignable in Keymap) |`
- `.agents/architecture.md:125`: delete the `CopySelectionShortcuts.kt` row.
- `.agents/patterns.md:23` stays as is; it already says no default shortcut.

- [ ] **Step 10: Rewrite the CLAUDE.md testing section**

Replace from `## Testing (Manual QA Only)` up to, but not including, `## Must NOT Do` with:

```markdown
## Testing

Automated tests exist: `./gradlew test` (unit) and `./gradlew platformTest` (IntelliJ fixture tests). Keep them pragmatic — add or change a test only where it guards a real regression risk (plugin.xml descriptor, output/contract rules, documentation sync). Verify UI and platform behavior with manual QA instead of new test suites.

Manual QA: run `./gradlew runIde` to launch the IDE with the plugin.

1. Select code → `Ctrl+Alt+Shift+X` (`Cmd+Alt+Shift+X` on macOS) → verify absolute path copied
2. Settings: enable "Include code content" → shortcut → verify markdown code block
3. Settings: switch to Relative path → shortcut → verify relative path
4. No selection → shortcut → verify current line number
5. Right-click → "Copy Selection Context" copies directly; "Copy Selection Context Tools ▸" lists the other eight actions
6. Verify toast notification on copy
7. Settings → Tools → Copy Selection Context → verify radio buttons + checkbox; "Configure shortcuts in Keymap…" opens Keymap filtered to the plugin actions
8. Settings → Keymap → no conflict warning on `Ctrl/Cmd+Alt+Shift+X`

```

- [ ] **Step 11: Add the CHANGELOG entry**

Under `## [Unreleased]` in `CHANGELOG.md`, insert, keeping one blank line before `## [1.8.1]`:

```markdown
### Added

- Open Keymap filtered to the plugin's actions from **Configure shortcuts in Keymap…** in Settings → Tools → Copy Selection Context

### Changed

- Copy Selection Context now has the single-stroke default `Ctrl+Alt+Shift+X` (`Cmd+Alt+Shift+X` on macOS), free in every bundled keymap
- Show Copy Selection Context directly in the editor context menu and rename the submenu for the remaining actions to Copy Selection Context Tools

### Removed

- The `Ctrl+Alt+Shift+G` / `Cmd+Alt+Shift+G` two-stroke defaults for all actions; shortcuts you assigned yourself in Keymap are kept

### Fixed

- Documentation and the Marketplace description advertised `Ctrl+Alt+C` and `Ctrl+Alt+H`, which v1.7.0 had already replaced
```

- [ ] **Step 12: Run the documentation tests**

Run: `./gradlew test --tests 'com.github.hon454.copyselectioncontext.DocumentationSyncTest' --tests 'com.github.hon454.copyselectioncontext.ReleaseDocumentationTest' --tests 'com.github.hon454.copyselectioncontext.ReleaseNotesGenerationTest'`
Expected: PASS. If the structure-parity test fails, diff `markdownStructure` output between README.md and the failing locale; usually a bullet or table row is missing.

- [ ] **Step 13: Commit**

```bash
git add README*.md AGENTS.md CLAUDE.md CONTRIBUTING.md .agents/ CHANGELOG.md src/main/resources/META-INF/plugin.xml src/test/kotlin/com/github/hon454/copyselectioncontext/DocumentationSyncTest.kt
git commit -m "docs: document the single default shortcut, context menu layout and Keymap link"
```

---

### Task 4: Full verification and manual QA

**Files:** none

- [ ] **Step 1: Run all automated checks**

Run: `./gradlew allTests detekt verifyPlugin`
Expected: BUILD SUCCESSFUL. `verifyPlugin` reports no internal or deprecated API usage for the new `Settings` and `DataManager` calls.

- [ ] **Step 2: Manual QA in `./gradlew runIde`**

Walk through CLAUDE.md QA steps 1–8, plus:
- Switch Settings → Keymap to "macOS System Shortcuts" or "Eclipse (Mac OS X)" → Copy shows `⌘⌥⇧X`. On Windows/Linux, switch to "Eclipse" or "VSCode" → Copy shows `Ctrl+Alt+Shift+X`.
- Assign a custom shortcut to Show Copy History → it works and persists after restarting the sandbox IDE.
- Right-click in an editor without a backing file, such as the commit message editor: the top-level item follows the same enabled or visible state the submenu entry had before.
- If a language pack is available in the sandbox, repeat the Keymap link check with a non-English UI.

- [ ] **Step 3: Report**

Summarize the test output and the manual QA results, including anything that was not checked.
