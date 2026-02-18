# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.2] - 2026-02-18

### Added
- Copy History browser — browse and re-copy recent entries (`Ctrl+Alt+H`)
- GitHub/GitLab permalink generation for selected lines
- Template-based output formatting with presets (Claude Code, Path:Line, GitHub Permalink, Custom)
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

[Unreleased]: https://github.com/hon454/copy-selection-context/compare/v1.0.2...HEAD
[1.0.2]: https://github.com/hon454/copy-selection-context/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/hon454/copy-selection-context/commits/v1.0.1
