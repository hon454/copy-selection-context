# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**English** · **[한국어](README.ko.md)** · **[简体中文](README.zh-CN.md)** · **[繁體中文](README.zh-TW.md)** · **[日本語](README.ja.md)**

> Copy file path + line numbers + code to clipboard in one shortcut — formatted for AI assistants.

Tired of manually typing file paths and line numbers when sharing code context with AI coding assistants like Claude or ChatGPT? **Copy Selection Context** copies `@path#Lline` formatted context to your clipboard with a single shortcut.

[![Get from JetBrains Marketplace](https://img.shields.io/badge/Get%20from-JetBrains%20Marketplace-000000?style=for-the-badge&logo=jetbrains&logoColor=white)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)

## Features

- **One-shortcut copy** — `Ctrl+Alt+C` copies file path + line numbers instantly
- **Relative or absolute paths** — Choose between project-relative or absolute paths
- **Flexible output formats** — Use Claude Code references, Path:Line output, or a custom template
- **Code content included** — Optionally include selected code as a markdown code block
- **Copy history** — `Ctrl+Alt+H` to browse recent copy history
- **GitHub/GitLab permalink** — Copy a Git permalink for the selected lines
- **Copy feedback** — Mark the copied lines, show an optional notification, and retain the last copy in the status bar
- **Multi-caret context** — Format every caret independently and separate its path/code block with a blank line
- **Accurate selection ends** — Treat IntelliJ's `selectionEnd - 1` as the final included offset, avoiding an extra trailing line
- **Localized settings** — Use localized output-format labels and matching English/Korean resource keys
- **Smart line handling** — Copies current line number when no text is selected
- **Context menu** — Access all actions from the editor right-click menu
- **Cross-platform** — Works on Windows, macOS, and Linux

## Installation

### From JetBrains Marketplace

1. `File` → `Settings` → `Plugins`
2. Search for **"Copy Selection Context"**
3. Click `Install`

### From Disk

1. Download the latest `.zip` from the [Releases](https://github.com/hon454/copy-selection-context/releases) page
2. `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
3. Select the downloaded `.zip` → Restart IDE

## Usage

### Keyboard Shortcuts

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |
| Show Copy History | `Ctrl+Alt+H` | `Ctrl+Alt+H` |

> Shortcuts can be customized in `Settings` → `Keymap`.

### Context Menu

Right-click in the editor → **Copy Selection Context** submenu:

| Action | Description |
|--------|-------------|
| Copy Selection Context | Copy path + lines based on settings (main action) |
| Copy Relative Path with Line Numbers | Copy with project-relative path |
| Copy Absolute Path with Line Numbers | Copy with absolute path |
| Copy with Code Content | Copy path + lines + code block |
| Copy GitHub/GitLab Permalink | Copy Git remote permalink |
| Show Copy History | Show recent copy history popup |

### Output Format

The main action and explicit path/code actions use the output format selected in settings. Paths are normalized to forward slashes.

Every caret produces its own 1-based inclusive range and optional code block, with blocks separated by a blank line. Since IntelliJ selection ends are exclusive, `selectionEnd - 1` determines the final included line; a selection ending at the next line's start does not include that line.

**Claude Code (`claude`, default)**:
- Single line: ` @src/main/kotlin/App.kt#L42 `
- Multiple lines: ` @src/main/kotlin/App.kt#L250-253 `

**Path:Line (`pathline`)**:
- Single line: `src/main/kotlin/App.kt:42`
- Multiple lines: `src/main/kotlin/App.kt:250-253`

**Custom template (`template`)**:
- Start from the Path and Range, Claude Reference, or With Code Block preset, or enter your own template
- Available variables populated by standard copy actions: `{path}`, `{line}`, `{range}`, `{code}`, `{lang}`, and `{filename}`
- The settings screen previews the result and flags unknown variables

**With code content** (Claude Code and Path:Line append a fenced block; custom templates place it with `{code}`):
````
 @src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

The separate **Copy GitHub/GitLab Permalink** action reads normal-repository or linked-worktree metadata on a background thread and builds a commit-specific URL for each caret. Only the latest request can update the clipboard. If the repository remote or commit cannot be resolved, it reports an error and leaves the clipboard unchanged.

### History, Notifications, and Status

- Standard path/code copy actions prepend entries to project-specific history. `Ctrl+Alt+H` opens the stored entries, and choosing one copies its full content again.
- Copy notifications are enabled by default and can be disabled. They are shown after standard path/code copies and Git permalink copies.
- A standard copy replaces the gutter markers in the active editor and updates the status-bar widget with a single-line, Unicode-safe, markup-escaped preview capped at 40 characters including its prefix. Click the widget to copy the full last value again.
- Optional local usage analytics count successful standard copy actions by output format and detected file language. Settings shows an immutable snapshot of every counter and provides a confirmed reset. Analytics are disabled by default, stored only in local IDE application settings, and never transmitted.

### Settings

`Settings` → `Tools` → `Copy Selection Context`:

- **Path type** — Absolute (default) or Relative
- **Output format** — Claude Code (default), Path:Line, or Custom Template
- **Custom format template** — Choose a preset or edit a six-row multiline template with a focusable six-row live preview, accessible labels, and variable validation
- **Include code content** — Include selected code, or the current line when nothing is selected (off by default)
- **Trim code whitespace** — Remove leading and trailing whitespace from included code (off by default)
- **Show copy notifications** — Show a balloon after supported copy actions (on by default)
- **Copy history size** — Keep 0–100 entries per project (default: 10); set `0` to disable and clear history
- **Local usage analytics** — Inspect and reset opt-in total, output-format, and language counters stored only on this machine (off by default; never transmitted)

Copy history may contain copied code. It is stored only in the IDE's local, non-roaming workspace data and is not written to shareable project settings. Each stored entry is limited to 256 KiB of UTF-8 content, and total history per project is limited to 2 MiB. Results over 256 KiB are still copied in full but are not added to history. When either the configured entry count or total byte budget is exceeded, the oldest entries are removed immediately. Use **Clear all history** at the bottom of the history popup to remove every entry. Existing history, including data migrated from `copySelectionHistory.xml`, is normalized to the same limits when loaded; the legacy file is then cleaned up by the IDE.

#### Settings Screen

![Copy Selection Context settings screen](docs/images/settings-copy-selection-context.png)

Configure path type, output and templates, code handling, notifications, history, and local analytics from one place.

## Compatible IDEs

Works with all IDEs based on IntelliJ Platform 2024.3+:

IntelliJ IDEA · Android Studio · PyCharm · WebStorm · PhpStorm · CLion · GoLand · Rider · RubyMine

## Development

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

# Unix / macOS
./gradlew buildPlugin    # Build plugin ZIP
./gradlew runIde         # Run dev IDE with plugin
./gradlew test           # Run tests

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed development and release guides.

The test suite includes `CopySelectionActionFixtureTest` for real IntelliJ editor/action/clipboard and async permalink flows. CI runs the full tests, project and structure checks, `verifyPlugin` compatibility verification, plugin packaging, ZIP checks, and uploads diagnostics before publishing artifacts.

## Support

If you find this plugin useful, consider buying me a coffee!

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

## Author

Made by [@hon454](https://github.com/hon454)
