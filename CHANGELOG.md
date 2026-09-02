# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Bound project copy history by documented per-entry and total UTF-8 content budgets while preserving oversized clipboard results (#42)
- Show local-only copy totals, output-format usage, and language usage in Settings with a confirmed reset action (#41)

### Fixed
- Localize action menus, Git permalink presentation, gutter feedback, and template preset labels without changing persisted settings or template contents (#43)
- Record the detected file language exactly once per successful standard copy action, including multi-caret copies (#41)

### Fixed
- Keep Gradle wrapper download and initialization output out of generated GitHub Release notes (#53)

## [1.1.1] - 2026-09-02

### Changed
- Harden the Java and Gradle bootstrap path with a verified setup action and Gradle distribution checksum (#47)

### Fixed
- Prevent custom-template replacement values from recursively expanding placeholder-like text and align blank previews with the runtime fallback (#40)
- Add actionable, localized Git permalink failure diagnostics with credential-safe logging (#46)

## [1.1.0]

> **v1.1.0 release candidate scope:** All component pull requests are integrated.
> The changes below remain unreleased until this release-candidate pull request
> passes every release gate and v1.1.0 is tagged.

### Added
- Add local-only copy-history privacy controls, including disable, clear-all, deterministic trimming, and legacy cleanup behavior (#10)
- Add accessible multiline custom-template editing with bounded preview and apply-time variable validation (#15)

### Changed
- Gate packaging and publication on tests, plugin structure checks, and supported-IDE compatibility verification (#12)
- Show localized output-format names while preserving stable settings keys (#13)
- Keep the English and Korean resource bundles on the same active key set (#14)
- Synchronize architecture and user guides with the integrated settings, output, history, notification, analytics, and status behavior (#17)
- Align contributor release instructions with the changelog-driven workflow (#18)
- Exercise complete action flows with IntelliJ Platform fixtures, including clipboard and feedback side effects (#19)

### Fixed
- Preserve each caret's own range and code when copying multi-caret selections (#5)
- Honor exclusive selection end offsets without adding the following line (#6)
- Populate `{filename}` consistently in runtime custom-template output (#7)
- Resolve project-relative paths by path boundaries instead of string prefixes (#8)
- Generate reliable GitHub and GitLab permalinks for worktrees, packed refs, detached HEADs, remote variants, and encoded paths (#9)
- Keep notification and status previews concise, single-line, Unicode-safe, and markup-safe without truncating clipboard history (#11)
- Prevent generic formatter settings from emitting unresolved GitHub permalink placeholders (#16)

## [1.0.4] - 2026-08-21

### Changed
- Update Kotlin, Gradle, the IntelliJ Platform Gradle Plugin, test dependencies, and GitHub Actions
- Preserve IntelliJ Platform 2024.3 compatibility while verifying against newer IDE releases
- Refresh README badges with a focused GitHub and JetBrains Marketplace set
- Suppress Class Data Sharing warnings during searchable options generation

### Fixed
- Scope selection gutter highlighters to their owning editor, preventing cross-editor ownership assertions

## [1.0.3] - 2026-02-18

### Changed
- Switch release notes from commit-based to CHANGELOG.md-based generation
- Integrate gradle-changelog-plugin with CHANGELOG.md as single source of truth
- Improve Marketplace listing with detailed plugin description and change notes

## [1.0.2] - 2026-02-18

### Added
- Copy History browser — browse and re-copy recent entries (`Ctrl+Alt+H`)
- GitHub/GitLab permalink generation for selected lines
- Template-based output formatting with presets (Claude Code, Path:Line, Custom)
- Live template preview and variable validation in settings
- Status bar widget — shows last copied text, click to re-copy

### Changed
- Removed project-level settings (simplified to application-level only)
- Configurable notification toggle and history size
- Cross-platform build command documentation

### Fixed
- Corrected help anchor link
- Aligned notification group metadata

## [1.0.1] - 2026-02-18

### Added
- Status bar widget showing last copied text with click-to-copy
- Output formatter engine with Claude Code and path:line presets
- Template engine with custom format variables
- Copy history service with popup (`Ctrl+Alt+H`)
- GitHub/GitLab permalink generation
- Multi-caret support
- Context menu submenu with SVG icons
- Settings: notification toggle, history size, format selection, code trimming
- 30+ language detection for code blocks
- Unit and integration test infrastructure

### Changed
- Package renamed to `com.github.hon454.copyselectioncontext`
- Unified shortcut to `Ctrl+Alt+C`

### Fixed
- CI/CD workflow compatibility
- Notification group metadata alignment

## [1.0.0] - 2026-02-18

### Added
- One-shortcut copy (`Ctrl+Alt+C`) — file path + line numbers to clipboard
- Relative and absolute path modes
- Code content copy as markdown code block
- Settings UI (Tools → Copy Selection Context)
- Editor context menu integration
- Toast notifications
- IntelliJ Platform 2024.3+ compatibility

[Unreleased]: https://github.com/hon454/copy-selection-context/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/hon454/copy-selection-context/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/hon454/copy-selection-context/compare/v1.0.4...v1.1.0
[1.0.4]: https://github.com/hon454/copy-selection-context/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/hon454/copy-selection-context/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/hon454/copy-selection-context/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/hon454/copy-selection-context/commits/v1.0.1
