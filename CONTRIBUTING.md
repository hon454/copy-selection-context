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

## Release Process

Releases are automated via GitHub Actions. Pushing a version tag triggers the workflow.

### Steps

1. **Update the version** in `build.gradle.kts`:
   ```kotlin
   version = "1.2.0"
   ```

2. **Commit the version bump**:
   ```bash
   git add build.gradle.kts
   git commit -m "chore: bump version to 1.2.0"
   ```

3. **Create and push the tag**:
   ```bash
   git tag v1.2.0
   git push origin main v1.2.0
   ```

4. The **Release workflow** (`release.yml`) runs automatically and:
   - Verifies the tag version matches `build.gradle.kts`
   - Builds the plugin
   - Creates a GitHub Release with commit-based release notes
   - Attaches the plugin ZIP to the release
   - Publishes to JetBrains Marketplace (if signing secrets are configured)

### Version Rules

- Tag format: `v<major>.<minor>.<patch>` (e.g., `v1.2.0`)
- Tag version **must match** the `version` in `build.gradle.kts` — the workflow fails otherwise
- Follow [Semantic Versioning](https://semver.org/): breaking → major, feature → minor, fix → patch

### Release Notes

Release notes are generated automatically from the commit history between the previous tag and the current tag. Merge commits are excluded. Each entry links to the full commit on GitHub.

Since commit messages become the public release notes, write them clearly following the [Commit Convention](#commit-convention) below.

### JetBrains Marketplace Publishing

Publishing activates automatically when the following GitHub repository secrets are configured:

| Secret | Description |
|--------|-------------|
| `PUBLISH_TOKEN` | JetBrains Marketplace API token |
| `CERTIFICATE_CHAIN` | Plugin signing certificate (`chain.crt` contents) |
| `PRIVATE_KEY` | Unencrypted private key (`private.pem` contents) |
| `PRIVATE_KEY_PASSWORD` | Private key password |

To generate signing certificates:

```bash
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096
openssl rsa -in private_encrypted.pem -out private.pem
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
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
