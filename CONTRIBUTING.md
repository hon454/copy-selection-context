# Contributing to Copy Selection Context

## Development Setup

### Prerequisites

- JDK 21+
- IntelliJ IDEA (Community or Ultimate)

### Build & Run

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

# Unix / macOS
./gradlew buildPlugin    # Build plugin ZIP (build/distributions/)
./gradlew runIde         # Run dev IDE with plugin installed
./gradlew test           # Run tests
./gradlew verifyPlugin   # Verify plugin structure

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat test
gradlew.bat verifyPlugin
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

Releases are automated by [`.github/workflows/release.yml`](.github/workflows/release.yml). Pushing a tag that starts with `v` triggers the workflow.

### Steps

1. **Update the `[Unreleased]` section** in [`CHANGELOG.md`](CHANGELOG.md) with the user-visible changes in the release. Keep entries under the appropriate Keep a Changelog headings, such as `Added`, `Changed`, or `Fixed`.

2. **Update the version** in `build.gradle.kts`:
   ```kotlin
   version = "1.2.0"
   ```

3. **Patch the changelog** with the Gradle Changelog Plugin. The task moves the `[Unreleased]` entries into a versioned `1.2.0` section and creates a new `[Unreleased]` section:
   ```bash
   # Unix / macOS
   ./gradlew patchChangelog

   # Windows
   gradlew.bat patchChangelog
   ```

4. **Preview the release notes** with the same changelog task options used by the release workflow:
   ```bash
   ./gradlew getChangelog \
     --console=plain \
     -q \
     --no-header \
     --no-links \
     --no-summary
   ```

5. **Commit the version and changelog updates** using the repository's commit convention:
   ```bash
   git add build.gradle.kts CHANGELOG.md
   git commit -m "chore(release): prepare 1.2.0" \
     -m "Move the accumulated changelog entries into the 1.2.0 release and align the Gradle project version with the release tag."
   ```

6. **Create and push the tag** after the release commit is on `main`:
   ```bash
   git tag v1.2.0
   git push origin main v1.2.0
   ```

7. The **Release workflow** runs automatically and:
   - Verifies the tag version matches `build.gradle.kts`
   - Generates release notes from the matching version section in `CHANGELOG.md`
   - Builds the plugin
   - Selects exactly one plugin ZIP, writes and verifies `SHA256SUMS`, and generates and verifies GitHub build-provenance attestation for that exact ZIP
   - Creates a non-draft, non-prerelease GitHub Release named after the tag
   - Attaches the attested plugin ZIP and `SHA256SUMS` to the release
   - Publishes to JetBrains Marketplace only when `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` are all non-empty

### Version Rules

- The workflow is triggered by any pushed tag matching `v*`; release tags use `v<major>.<minor>.<patch>` (for example, `v1.2.0`)
- The part after `v` **must match exactly** the `version` in `build.gradle.kts` — the workflow fails otherwise
- Follow [Semantic Versioning](https://semver.org/): breaking → major, feature → minor, fix → patch

### Release Notes

[`CHANGELOG.md`](CHANGELOG.md) is the single source of truth for release notes. The workflow invokes `scripts/generate-release-notes.sh`, which initializes the Gradle wrapper before separately capturing `getChangelog` output for the project version without the section header, comparison links, or summary. Only that captured output is written to `release-notes.md`; if it is empty, the script uses `Release v<version>` as a fallback.

Keep `[Unreleased]` current as changes land, then run `patchChangelog` after setting the release version so the workflow can find the matching version section. Commit messages remain important for review and repository history, but they are not used to generate release notes.

### Release Artifact Provenance

The plugin ZIP attached to a GitHub Release by [`.github/workflows/release.yml`](.github/workflows/release.yml) is the canonical release artifact. The workflow grants only `contents: write` for release creation, `id-token: write` for the GitHub OIDC identity, and `attestations: write` for provenance storage. Checksum generation, checksum verification, attestation generation, and offline verification of the returned attestation bundle all run before release creation, so any failure prevents publication.

Cross-environment byte-for-byte reproducibility is not currently supported. Gradle 9 makes archive order and timestamps reproducible by default, and two clean builds in one environment produce identical ZIPs. However, IntelliJ Platform Gradle Plugin 2.18.1's [`GenerateManifestTask`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/2.18.1/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/GenerateManifestTask.kt) unconditionally records `Build-JVM` and `Build-OS` from the build host and exposes no supported setting to normalize or omit them. Editing the generated manifest would rely on internal task behavior and would change the input to signing and Marketplace publication, so the repository preserves those fields until JetBrains provides a supported contract. See Gradle's [byte-for-byte reproducibility guidance](https://docs.gradle.org/current/userguide/best_practices_security.html#build_output_should_be_byte_for_byte_reproducible) and GitHub's [artifact attestation guidance](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).

Release users verify the checksum and provenance with the platform commands documented in all localized README files. The release-time attestation check additionally binds the ZIP to the exact triggering commit, tag ref, repository, and `.github/workflows/release.yml` signer workflow.

### JetBrains Marketplace Publishing

Publishing activates automatically when the following GitHub repository secrets are configured:

| Secret | Description |
|--------|-------------|
| `PUBLISH_TOKEN` | JetBrains Marketplace API token |
| `CERTIFICATE_CHAIN` | Plugin signing certificate (`chain.crt` contents) |
| `PRIVATE_KEY` | Unencrypted private key (`private.pem` contents) |
| `PRIVATE_KEY_PASSWORD` | Password for an encrypted private key; passed to Gradle when configured |

The workflow checks `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` before running `publishPlugin`. If any of those three values is empty, the GitHub Release is still created but Marketplace publishing is skipped. `PRIVATE_KEY_PASSWORD` is available to the signing configuration but is not part of the workflow condition, so it may be empty when the private key is unencrypted.

To generate signing certificates:

```bash
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096
openssl rsa -in private_encrypted.pem -out private.pem
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

## Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/) and the repository rules in [`AGENTS.md`](AGENTS.md#commit-convention).

```
type[(scope)]: concise subject (imperative mood, lowercase, no period)

Body paragraph explaining WHY this change was made and WHAT it accomplishes.
Include context that is not obvious from the diff alone.
```

**Allowed types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `perf`, `style`, `build`, `asset`

- Keep the subject at 72 characters or fewer, use imperative mood, start the text after the colon in lowercase, and omit a trailing period
- Include a body for every non-trivial commit, separated from the subject by a blank line
- Do not add AI agent attribution, co-author trailers, or tool-credit footers

## Pull Requests

- One feature/fix per PR
- Include manual testing steps
- Update AGENTS.md when architecture changes
