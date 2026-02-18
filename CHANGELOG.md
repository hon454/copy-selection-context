<h2>1.0.2</h2>
<h3>New Features</h3>
<ul>
  <li>Copy History browser — browse and re-copy recent clipboard entries (<code>Ctrl+Alt+H</code>)</li>
  <li>GitHub/GitLab permalink generation for selected lines</li>
  <li>Template-based output formatting with built-in presets (Claude Code, Path:Line, GitHub Permalink, Custom)</li>
  <li>Live template preview and variable validation in settings UI</li>
  <li>Status bar widget — displays last copied text, click to re-copy</li>
  <li>Web Help integration — F1 help links to plugin documentation</li>
</ul>
<h3>Improvements</h3>
<ul>
  <li>Refactored core copy actions to shared utility functions</li>
  <li>Template variable validation warns on unknown placeholders</li>
  <li>Code trimming option for cleaner code block output</li>
  <li>Configurable notification toggle</li>
  <li>Configurable history size</li>
  <li>Removed project-level settings override (simplified to application-level only)</li>
  <li>Cross-platform build command documentation (Windows <code>gradlew.bat</code> + Unix <code>./gradlew</code>)</li>
</ul>
<h3>Bug Fixes</h3>
<ul>
  <li>Aligned plugin notification group metadata with runtime notifier usage</li>
  <li>Replaced vendor email placeholder with project email</li>
  <li>Corrected help anchor to match README heading</li>
</ul>

<h2>1.0.0</h2>
<h3>New Features</h3>
<ul>
  <li>Copy relative file path with line numbers to clipboard</li>
  <li>Copy absolute file path with line numbers to clipboard</li>
  <li>Copy file path, line numbers, and code content in markdown format</li>
  <li>Toast notification on copy success</li>
  <li>Smart line handling — copies current line number when no text is selected</li>
  <li>Settings UI for default path type selection</li>
  <li>Keyboard shortcuts for all copy actions (<code>Ctrl+Alt+C</code> / <code>Cmd+Alt+C</code>)</li>
  <li>Context menu integration in editor right-click menu</li>
  <li>Support for IntelliJ Platform 2024.3+ and all JetBrains IDEs</li>
</ul>
