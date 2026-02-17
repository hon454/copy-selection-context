# Copy Selection Context

> A JetBrains plugin to copy file paths with line numbers (and optionally code content) to clipboard for easy AI context sharing.

## Features

- **Copy with line numbers** — Get file path and exact line range
- **Relative or absolute paths** — Choose project-relative or full file system paths
- **Optional code content** — Include actual code in markdown format
- **Toast notifications** — Visual feedback on copy success
- **Status bar widget** — Shows last copied information
- **No selection handling** — Copies current line when no text is selected
- **Context menu integration** — Right-click in editor for quick access
- **Keyboard shortcuts** — Customizable shortcuts for all actions
- **Cross-platform** — Works on Windows, macOS, and Linux

## Installation

### From JetBrains Marketplace

1. Go to `File` → `Settings` → `Plugins`
2. Search for **"Copy Selection Context"**
3. Click `Install`

### From Disk

1. Download the latest release `.zip` file
2. Go to `File` → `Settings` → `Plugins`
3. Click gear icon → `Install Plugin from Disk...`
4. Select the downloaded `.zip` file
5. Restart the IDE

## Usage

### Keyboard Shortcut

| Action | Windows/Linux | macOS |
|--------|---------------|-------|
| Copy Selection Context | `Ctrl+Alt+C` | `Cmd+Alt+C` |

One unified shortcut. Behavior is controlled via settings (path type + code content toggle).

> You can customize this shortcut in `Settings` → `Keymap`
>
> Additional explicit actions (Copy Relative Path, Copy Absolute Path, Copy with Code Content) are available in the editor context menu.

### Context Menu

1. Select text in the editor (or just place cursor on a line)
2. Right-click to open the context menu
3. Choose one of the Copy Selection Context actions

### Output Format (Claude Code Style)

Output uses the ` @path#Lline ` format for seamless AI assistant integration.

**Plain path (default)**:
- Single line: ` @src/main/kotlin/App.kt#L42 `
- Multiple lines: ` @src/main/kotlin/App.kt#L250-253 `

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

Go to `Settings` → `Tools` → `Copy Selection Context` to configure:

- **Path type** — Absolute (default) or Relative to project root
- **Include code content** — Append a markdown code block with the selected code

## Development

```bash
# Clone the repository
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

# Build the plugin
./gradlew buildPlugin

# Run in development IDE
./gradlew runIde
```

## Plugin Signing (for Marketplace Publishing)

Generate a signing certificate using OpenSSL:

```bash
# Generate encrypted private key
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096

# Extract unencrypted private key
openssl rsa -in private_encrypted.pem -out private.pem

# Generate certificate chain
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

Set environment variables before publishing:
```bash
export CERTIFICATE_CHAIN=$(cat chain.crt)
export PRIVATE_KEY=$(cat private.pem)
export PRIVATE_KEY_PASSWORD="your-password"
export PUBLISH_TOKEN="your-jetbrains-token"
```

## Compatible IDEs

This plugin works with all IntelliJ Platform-based IDEs (2024.3+):

- IntelliJ IDEA
- Android Studio
- PyCharm
- WebStorm
- PhpStorm
- CLion
- GoLand
- Rider
- RubyMine

## Requirements

- Build 243 or higher (IntelliJ Platform 2024.3+)

## License

MIT License - see [LICENSE](LICENSE) for details.

## Author

Made by [hon454](https://github.com/hon454)
