# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.1] - Unreleased

### Added
- Unit tests for `CopySelectionUtils` covering path resolution, line range resolution, code extraction, language detection, and output formatting (`3ff2c1f`)
- Release checklist document with `runIde`-based manual smoke test procedure (`516751c`)

### Changed
- Refactored core copy actions to use shared utility functions via `CopySelectionUtils` and base template method flow (`e77f3ff`)
- Updated roadmap structure to keep versioning in the `v1.x` line (`516751c`)

### Fixed
- Aligned plugin notification group metadata with runtime notifier usage (`02e2bbd`)
- Replaced vendor email placeholder in plugin metadata with the project email (`02e2bbd`)
- Improved repository hygiene by ignoring common log and JVM crash dump artifacts (`516751c`)

## [1.0.0] - Initial

### Added
- Copy relative file path with line numbers to clipboard
- Copy absolute file path with line numbers to clipboard
- Copy file path, line numbers, and code content in markdown format
- Toast notification on copy success
- Status bar widget update hook (stub; full widget implementation planned)
- No selection handling: copies current line number when no text is selected
- Minimal Settings UI for default path type selection
- Keyboard shortcuts for all three copy actions
- Context menu integration in editor
- Support for IntelliJ Platform 2024.3+
- Compatible with all JetBrains IDEs (Rider, IntelliJ IDEA, PyCharm, etc.)
