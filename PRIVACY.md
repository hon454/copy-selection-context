# Privacy Policy for Copy Selection Context

Effective date: September 8, 2026

Copy Selection Context is an open-source JetBrains IDE plugin maintained by
hon454. This policy describes how the plugin handles data. The plugin does not
send your source code, file paths, copy history, or usage statistics to the
maintainer or automatically submit them to any AI service.

## Code context and clipboard

When you use the plugin, it processes the selected code, file paths, line ranges,
file names, and language information in your IDE environment to format context
or capture it in a collection. Git permalink generation reads local repository
metadata, including the remote URL and commit identifier, to construct a link;
it does not upload code or contact the Git hosting provider to generate that link.

Copy actions place the formatted result on the system clipboard. The IDE, your
operating system, clipboard managers, and clipboard synchronization services may
retain or synchronize clipboard content according to their own settings. Content
you paste into another application or AI service is handled by that service.
Clearing the plugin's history or collection does not clear those separate copies.

## Copy history

Standard path/code copies and Git permalink copies can be saved with a timestamp
in project-specific copy history. History may contain source code and identifying
information present in file paths or copied text. It is stored in the IDE's local,
non-roaming workspace data, rather than shareable project settings.

The default is 10 entries per project, configurable from 0 to 100. Each entry is
limited to 256 KiB of UTF-8 content, and total history per project is limited to
2 MiB. The oldest entries are removed when limits are exceeded. History can
persist across IDE sessions. Use **Clear all history** in the history popup to
delete it, or set **Copy history size** to **0** to disable and clear it.

## Context collections

Context collections retain captured code, paths, ranges, and related snapshot
metadata in memory for the project session. They are not persisted and are
discarded when the project closes or the plugin unloads. You can remove items or
clear the collection in its tool window. Copying a collection retains its items
and does not add the combined output to persistent plugin copy history.

## Optional usage statistics

Local usage analytics are disabled by default. If you enable them, the plugin
stores aggregate copy counts, output-format counts, and language counts in local,
non-roaming IDE application storage. These counters do not contain copied code or
file paths and are never transmitted by the plugin. You can inspect and reset
the counters in the plugin settings. Disabling analytics stops further counting;
use the reset control to delete existing counters.

## Settings and review prompts

The plugin persists preferences, including formatting options, custom templates,
history size, notification preferences, and whether analytics are enabled. These
general preferences use IDE settings storage and may follow the IDE's settings
synchronization configuration.

Review-prompt state, including the last prompted plugin version, permanent
suppression preference, and whether the Marketplace review page was opened, is
stored separately in local, non-roaming storage. The session copy count used for
review eligibility is not persisted. Opening the review page requires a user
action and does not transmit copied content or usage counters.

## External services and support

This policy covers the plugin's behavior. JetBrains Marketplace, IDE platform
services, and external websites you choose to open operate under their own
privacy policies. The plugin does not provide its own remote telemetry service.

If you contact the maintainer by email or submit a GitHub issue, the maintainer
receives the information you choose to provide and uses it to respond to your
request. GitHub issues are public; please avoid posting secrets or private code.
GitHub and your email provider also process those communications under their own
policies. You may contact the maintainer about deletion of support information
under the maintainer's control.

## Changes and contact

This policy may be updated to reflect changes in the plugin. Updates will be
published in this file with a revised effective date.

For privacy questions or requests, contact **hon454** at
[hon454@gmail.com](mailto:hon454@gmail.com).
