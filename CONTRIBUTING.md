# Contributing to Copy Selection Context

## Development Setup

### Prerequisites

- JDK 21+
- IntelliJ IDEA (Community or Ultimate)

Windows contributors who run `test` or `allTests` also need a
Bash executable and the basic Unix command-line tools used by the release
script tests. Those tests invoke the repository's `scripts/*.sh` files through
`ProcessBuilder("bash", ...)`; the scripts use tools such as `mktemp`, `dirname`,
`find`, `sort`, `basename`, `mv`, `rm`, and a SHA-256 utility (`sha256sum` or
`shasum`). This is a development
and test requirement only. Plugin users do not need Bash or these tools to
install or use the plugin.

Git for Windows with Git Bash is one example of an environment that provides
these tools. From the same Windows Command Prompt (`cmd.exe`) that will launch
Gradle, check the tools inside the Bash environment before starting Gradle:

```text
bash --version
where.exe bash
bash -c "command -v bash mktemp dirname find sort basename mv rm && (command -v sha256sum || command -v shasum)"
```

`where.exe bash` confirms which Bash a Windows shell would start, while
`command -v` checks the Unix tools in Bash rather than Windows `find` or
`sort` commands. Confirm that the printed paths point into the Bash
distribution's Unix tools, not Windows `System32` binaries. The check should
find every required tool and at least one of `sha256sum` or `shasum`. If a
command is not found, install or enable a Bash distribution that provides the
required Unix tools, then open a new terminal with that distribution's `bin`
directory on `PATH`.
The important detail is that `bash` and those tools must be discoverable by the
Gradle process itself, not only by a separate Git Bash window. Re-run the
checks in the terminal where `gradlew.bat` will be invoked; this guide does
not modify the user's PATH automatically.

### Build & Run

```bash
git clone https://github.com/hon454/copy-selection-context.git
cd copy-selection-context

# Unix / macOS
./gradlew buildPlugin    # Build plugin ZIP (build/distributions/)
./gradlew runIde         # Run dev IDE with plugin installed
./gradlew test           # Run reusable pure unit tests
./gradlew platformTest   # Run isolated IntelliJ Platform tests
./gradlew allTests       # Run the complete test suite
./gradlew verifyPlugin   # Verify plugin structure

# Windows
gradlew.bat buildPlugin
gradlew.bat runIde
gradlew.bat test
gradlew.bat platformTest
gradlew.bat allTests
gradlew.bat verifyPlugin
```

`allTests` is the aggregate command used locally and in CI. The `test` task reuses
its Gradle worker for pure unit tests, while `platformTest` starts a fresh JVM for
each IntelliJ application or editor-fixture test class. CI adds `--continue` so
both tasks can produce reports even if one task fails.

## IDE and Operating System Validation

`build.gradle.kts` keeps the explicit Plugin Verifier targets in the single
`pluginVerificationTargets` list. As of 2026-09-08, the recorded targets are:

| Purpose | Gradle type | Product code | Version | Build |
|---------|-------------|--------------|---------|-------|
| Minimum supported IntelliJ IDEA Community | `IntellijIdeaCommunity` | `IC` | `2024.3` | `243.21565.193` |
| Latest stable IntelliJ IDEA | `IntellijIdea` | `IU` | `2026.2.2` | `262.10315.125` |
| Latest stable Rider | `Rider` | `RD` | `2026.2.1` | `262.9437.287` |

The minimum build remains a compatibility floor and is not advanced during a
routine target refresh. For the two latest-stable rows, query JetBrains'
[official release service](https://data.services.jetbrains.com/products/releases?code=IIU,RD&latest=true&type=release)
and accept only `release` entries. Update the version and build together in
`pluginVerificationTargets`, recording the query date in the pull request.
IntelliJ IDEA 2025.3 and later uses the unified `IntellijIdea` (`IU`) type; the
legacy Community helper remains correct for the 2024.3 minimum. See the official
[target-platform DSL](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#target-platforms)
and [Plugin Verifier IDE DSL](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellij-platform-plugin-verification-ides).

Run the recorded compatibility set on Linux with:

```bash
./gradlew verifyPlugin --stacktrace --console=plain
```

The target-specific reports under `build/reports/pluginVerifier/` reproduce the
same product versions selected in `build.gradle.kts`. Do not replace these fixed
targets with `recommended()`, because that makes the recorded validation set
change without review.

Validation is deliberately split by responsibility:

| Path | Operating systems | Checks | Artifact |
|------|-------------------|--------|----------|
| Pull request `Build` | Linux | Detekt, unit and isolated platform tests, coverage, project/structure checks, the three-IDE Plugin Verifier set, packaging | `linux-validation-reports`, `linux-plugin-artifact` |
| `Release Validation` OS matrix | Linux, macOS, Windows | `allTests` (`test` plus `platformTest`); Kotlin coverage once on Linux | `release-validation-<runner>` |
| `Release Validation` compatibility job | Linux only | Detekt, project/structure checks, the three-IDE Plugin Verifier set | `release-validation-linux-ide-compatibility` |
| Gated `Release` job | Linux only | Canonical ZIP build, optional signing and signature verification, checksum, attestation, GitHub Release and conditional Marketplace publication | `release-linux-build-diagnostics` |

The release job directly needs the reusable validation workflow and runs only
when its result is `success`. The reusable workflow has its own final gate that
fails unless both the complete OS matrix and Linux compatibility job succeed.
Matrix `fail-fast` is disabled and report upload uses `always()`, so a failing
target does not cancel sibling diagnostics. A failed, cancelled, or skipped
required validation cannot reach either publication step. Signing, canonical
ZIP selection, checksum, attestation, GitHub Release creation, and Marketplace
publication remain one Linux job and are never repeated per OS.

For a pre-merge, non-publishing execution, push the exact candidate commit to a
branch matching `codex/verify-*`. That branch trigger runs `Release Validation`
with read-only token permissions and no release secrets or publication steps.
`workflow_dispatch` is also available after the workflow exists on the default
branch; GitHub does not permit a new dispatch-only workflow to run before that.
Record the candidate SHA and the three OS jobs plus the compatibility job in the
pull request. Never use a release tag to validate this workflow.

The automated layers do not replace actual IDE use:

- Pure tests validate formatting, policy, and workflow contracts without an IDE.
- `platformTest` fixtures exercise standard copy, multi-caret copy, Git
  permalink, history/status re-copy, collection opening and Copy All, cancelled
  collection copy, and project-disposal cleanup against an IntelliJ test
  application. They are API-level checks, not GUI evidence.
- Manual GUI verification must separately record the operating system, IDE
  product/build, JBR, plugin ZIP and source SHA, steps, and actual result for
  those scenarios. An untested IDE product, external clipboard manager, or GUI
  interaction remains a documented gap rather than an inferred success.

The localized READMEs describe the broader set of compatible IntelliJ-based
products. The automated compatibility set above samples the minimum IntelliJ
IDEA platform, current IntelliJ IDEA, and current Rider; it does not claim that
every listed JetBrains product, version, operating-system/IDE combination, or
real desktop clipboard interaction ran in CI.

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
   - Resolves signed or unsigned release mode, failing on partial signing configuration
   - When both signing credentials are present, signs the plugin and verifies the signed ZIP; otherwise selects the unsigned ZIP and explicitly skips Marketplace publication
   - Selects exactly one canonical plugin ZIP, writes and verifies `SHA256SUMS`, and generates and verifies GitHub build-provenance attestation for that exact ZIP
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

The plugin ZIP attached to a GitHub Release by [`.github/workflows/release.yml`](.github/workflows/release.yml) is the canonical release artifact. When `CERTIFICATE_CHAIN` and `PRIVATE_KEY` are both configured, the workflow runs `signPlugin` and `verifyPluginSignature`, then selects the single `-signed.zip` output. When both signing credentials are absent, it logs that the canonical ZIP is unsigned and skips Marketplace publication. Supplying only one signing credential fails the release before artifact selection. The workflow grants only `contents: write` for release creation, `id-token: write` for the GitHub OIDC identity, and `attestations: write` for provenance storage. Signing, checksum generation and verification, attestation generation, and offline verification of the returned attestation bundle all run before release creation, so any failure prevents publication.

Cross-environment byte-for-byte reproducibility is not currently supported. Gradle 9 makes archive order and timestamps reproducible by default, and two clean builds in one environment produce identical ZIPs. However, IntelliJ Platform Gradle Plugin 2.18.1's [`GenerateManifestTask`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/2.18.1/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/GenerateManifestTask.kt) unconditionally records `Build-JVM` and `Build-OS` from the build host and exposes no supported setting to normalize or omit them. Editing the generated manifest would rely on internal task behavior and would change the input to signing and Marketplace publication, so the repository preserves those fields until JetBrains provides a supported contract. See Gradle's [byte-for-byte reproducibility guidance](https://docs.gradle.org/current/userguide/best_practices_security.html#build_output_should_be_byte_for_byte_reproducible) and GitHub's [artifact attestation guidance](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations).

Release users verify the checksum and provenance with the platform commands documented in all localized README files. The release-time attestation check additionally binds the ZIP to the exact triggering commit, tag ref, repository, and `.github/workflows/release.yml` signer workflow. Marketplace publication passes that same selected path through the official [`PublishPluginTask.archiveFile`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/2.18.1/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/PublishPluginTask.kt) property. The explicit `canonicalPluginArchive` opt-in removes `publishPlugin`'s default build/sign dependencies so the already verified ZIP cannot be regenerated or replaced before upload; normal invocations without that property retain JetBrains' default task wiring. The signed path and signature verification follow the official [`SignPluginTask`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/2.18.1/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/SignPluginTask.kt) and [`VerifyPluginSignatureTask`](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/2.18.1/src/main/kotlin/org/jetbrains/intellij/platform/gradle/tasks/VerifyPluginSignatureTask.kt) contracts.

IntelliJ Platform Gradle Plugin 2.18.1 does not declare `verifyPluginSignature` as depending on `signPlugin`, so the release runs them in separate ordered Gradle invocations. Its certificate-content verification path also passes the PEM content as an extra CLI argument. The workflow therefore writes `CERTIFICATE_CHAIN` to a permission-restricted runner-temporary file, removes the content variable only for those Gradle processes, uses the official `certificateChainFile` property, and deletes the temporary file with a shell trap. These workarounds remain necessary until the upstream task wiring and content argument handling are corrected.

### JetBrains Marketplace Publishing

Publishing activates automatically when the following GitHub repository secrets are configured:

| Secret | Description |
|--------|-------------|
| `PUBLISH_TOKEN` | JetBrains Marketplace API token |
| `CERTIFICATE_CHAIN` | Plugin signing certificate (`chain.crt` contents) |
| `PRIVATE_KEY` | Unencrypted private key (`private.pem` contents) |
| `PRIVATE_KEY_PASSWORD` | Password for an encrypted private key; passed to Gradle when configured |

The workflow resolves signing independently from publishing. `CERTIFICATE_CHAIN` and `PRIVATE_KEY` must either both be present or both be absent. With both present, the canonical GitHub Release artifact is signed even if `PUBLISH_TOKEN` is missing. With both absent, the canonical artifact is explicitly unsigned and Marketplace publishing is skipped. Marketplace publishing runs only when `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, and `PRIVATE_KEY` are all non-empty, and receives the same canonical signed path through `-PcanonicalPluginArchive=...`. `PRIVATE_KEY_PASSWORD` is available to the signing configuration but is not part of the workflow condition, so it may be empty when the private key is unencrypted.

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
