# Contributing to Copy Selection Context

## Development Setup

### Prerequisites

- JDK 21+
- IntelliJ IDEA (Community or Ultimate)

### Build & Run

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

./gradlew buildPlugin    # Build plugin ZIP (build/distributions/)
./gradlew runIde         # Run dev IDE with plugin installed
./gradlew test           # Run tests
./gradlew verifyPlugin   # Verify plugin structure
```

## Project Structure

```
src/main/kotlin/com/github/hon454/copyselectioncontext/
├── CopySelectionContextAction.kt    # Main unified action (Ctrl+Alt+C)
├── CopySelectionBaseAction.kt       # Abstract base (clipboard logic)
├── CopyRelativePathAction.kt        # Relative path (context menu)
├── CopyAbsolutePathAction.kt        # Absolute path (context menu)
├── CopyWithCodeContentAction.kt     # Path + code block (context menu)
├── CopyGitPermalinkAction.kt        # GitHub/GitLab permalink
├── ShowCopyHistoryAction.kt         # Copy history popup
├── CopySelectionNotifier.kt         # Toast notifications
├── CopySelectionStatusBarWidget.kt  # Status bar widget
├── CopySelectionSettings.kt         # Settings persistence (@Service + @State)
└── CopySelectionConfigurable.kt     # Settings UI (Tools menu)
```

## Plugin Signing (for Marketplace Publishing)

Plugin signing is required to publish to the JetBrains Marketplace.

### Generate Certificates

```bash
# Generate encrypted private key
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096

# Extract unencrypted private key
openssl rsa -in private_encrypted.pem -out private.pem

# Generate certificate chain
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

### Set Environment Variables

```bash
export CERTIFICATE_CHAIN=$(cat chain.crt)
export PRIVATE_KEY=$(cat private.pem)
export PRIVATE_KEY_PASSWORD="your-password"
export PUBLISH_TOKEN="your-jetbrains-token"
```

### Publish

```bash
./gradlew publishPlugin
```

## Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/) format.

```
type[(scope)]: concise subject

Body explaining WHY this change was made.
```

**Allowed types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`, `style`, `build`, `asset`

## Pull Requests

- One feature/fix per PR
- Include manual testing steps
- Update AGENTS.md when architecture changes
