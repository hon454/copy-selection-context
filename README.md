# Copy Selection Context

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.github.hon454.copy-selection-context?label=Marketplace)](https://plugins.jetbrains.com/plugin/30262-copy-selection-context)
[![Release](https://img.shields.io/github/v/release/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/hon454/copy-selection-context/total)](https://github.com/hon454/copy-selection-context/releases)
[![Contributors](https://img.shields.io/github/contributors/hon454/copy-selection-context)](https://github.com/hon454/copy-selection-context/graphs/contributors)
[![License](https://img.shields.io/github/license/hon454/copy-selection-context)](LICENSE)

**[한국어](README.ko.md)**

> Copy file path + line numbers + code to clipboard in one shortcut — formatted for AI assistants.

Tired of manually typing file paths and line numbers when sharing code context with AI coding assistants like Claude or ChatGPT? **Copy Selection Context** copies `@path#Lline` formatted context to your clipboard with a single shortcut.

## Features

- **One-shortcut copy** — `Ctrl+Alt+C` copies file path + line numbers instantly
- **Relative or absolute paths** — Choose between project-relative or absolute paths
- **Code content included** — Optionally include selected code as a markdown code block
- **Copy history** — `Ctrl+Alt+H` to browse recent copy history
- **GitHub/GitLab permalink** — Copy a Git permalink for the selected lines
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

Outputs in `@path#Lline` format, ready to paste into AI assistants.

**Path only (default)**:
- Single line: `@src/main/kotlin/App.kt#L42`
- Multiple lines: `@src/main/kotlin/App.kt#L250-253`

**With code content (enable in settings)**:
````
@src/main/kotlin/App.kt#L42-53
```kotlin
fun calculateTotal(items: List<Item>): Double {
    return items.sumOf { it.price }
}
```
````

### Settings

`Settings` → `Tools` → `Copy Selection Context`:

- **Path type** — Absolute (default) or Relative
- **Include code content** — Whether to include the code block

#### Settings Screen

![Copy Selection Context settings screen](docs/images/settings-copy-selection-context.png)

Configure path type, output format, code inclusion, notification behavior, and history options from one place.

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

## Support

If you find this plugin useful, consider buying me a coffee!

<a href="https://www.buymeacoffee.com/hon454s" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" style="height: 60px !important;width: 217px !important;" ></a>

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

## Author

Made by [@hon454](https://github.com/hon454)
