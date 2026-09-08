# Git permalink target contract (#110)

## User policy and ownership

Only **Copy GitHub/GitLab Permalink** requires a system Git executable. Ordinary path/code copies, collection copies, and history/status re-copy never discover or invoke Git. Missing or unlaunchable Git produces a localized permalink failure; the next explicit invocation discovers it again. The plugin neither installs Git nor changes PATH/configuration, bundles an object-reading library, or depends on an IDE Git plugin. The existing platform-only, dumb-aware action and remote-selection rules remain in place.

The target is the original captured HEAD commit, not a replacement object, the index, or the latest remote branch. `GitHeadTargetValidator` produces an immutable `GitPreparedPermalink(content, state, head)` only after checking the literal repository-relative path in that tree and comparing its raw blob with the full current document snapshot. A HEAD-absent path (including untracked or renamed destinations) is blocked. A missing/deleted/non-regular local file, unsupported tree entry, unavailable object or unsafe text decoding is a typed failure, never clean.

Current document equality is the clean criterion. Unsaved insertion/deletion/replacement and saved or staged edits are compared against HEAD through that same document snapshot; a different index or disk alone does not make an editor that equals HEAD dirty. A dirty request gets one localized confirmation, for all carets together, with Cancel selected by default. Approval keeps the captured SHA and current inclusive line ranges in document order, with existing URL escaping and blank-line separators. It does not map lines. Cancel, failure and stale results write nothing and perform no success effects.

The raw blob decoder reports malformed/unmappable data instead of replacing characters. It supports the captured IDE charset, UTF-8/UTF-16 BOMs, and the IDE's CRLF/CR-to-LF normalization. NUL-containing decoded text is rejected. For paths with a `working-tree-encoding` attribute, HEAD bytes are decoded as UTF-8 as required by Git's storage contract. Attribute lookup reads the captured tree through `check-attr --source` and does not run conversion filters. Other conversions are not performed; undecodable input fails, and decodable content differing from the editor requires confirmation.

There is no automatic save, commit, stash, fetch, push, remote change or unverified-URL fallback. The link does not establish remote commit existence, pushed state or access rights.

## Local process boundary

`SystemGitExecutable` searches the IDE environment's PATH on each request (`git` on Unix/macOS, `git.exe` on Windows). Executable and repository paths remain separate argv elements, including spaces, Unicode, quotes and shell metacharacters. A found executable must actually run successfully; existence is not sufficient.

`GitProcessRunner` invokes only local plumbing used by the validator:

- `ls-tree -z --full-tree <original-sha> -- <literal-path>` checks the regular blob entry. NUL termination prevents quoted-path ambiguity.
- `cat-file blob <original-sha>:<literal-path>` reads original bytes without `--textconv` or `--filters`.
- `check-attr --source=<original-sha> -z working-tree-encoding -- <literal-path>` resolves the encoding storage rule without a working-tree conversion.

Every invocation uses `--no-pager`, `--no-replace-objects`, `--no-lazy-fetch`, `--no-optional-locks`, `--literal-pathspecs` and `-C <explicit-root>`. Unsupported Git options are an execution failure; they are never removed for a fallback query. See the official [Git options/environment contract](https://git-scm.com/docs/git) and [raw cat-file semantics](https://git-scm.com/docs/git-cat-file).

The child environment removes all inherited `GIT_*` variables, case-insensitively, including repository/worktree/common-dir, object alternates, namespaces, config injection, trace and helper overrides. It explicitly disables replacement objects, lazy fetch, terminal prompting, optional locks and system/global configuration. An empty `GIT_ALLOW_PROTOCOL` disallows every transport even if a repository enables one; command configuration also disables protocols, credentials and fsmonitor. The repository's own `.git`/`commondir`, local configuration and `objects/info/alternates` remain available. No global environment or repository file is changed. Raw plumbing does not execute external diff/textconv/smudge/clean helpers. Missing promisor objects fail locally.

Each query has a five-second deadline, an 8 MiB stdout limit and an independent 64 KiB stderr limit. Both pipes drain concurrently; stderr is counted and discarded. Stdin is closed immediately. On every exit the runner terminates its owned process and observed descendants and closes the three streams. Cleanup waits are bounded to 500 ms each. Current documents above 4,194,304 UTF-16 code units are rejected before copying their text; metadata reads are individually bounded to 1 MiB. Exceeding a limit is a typed verification failure and never a truncated comparison.

`ProcessCanceledException` and `CancellationException` propagate after cleanup through the runner, metadata resolver, target validator and action lookup boundary. Interruption retains the interrupt flag and propagates cancellation. Background progress cancellation, project/editor disposal and a newer application copy stop obsolete lookup work. Cancellations are not logged or shown as failures. Ordinary diagnostics contain only reason, operation, sanitized remote host where applicable and exception class, never raw stderr, argv, paths or code.

## Request state and synchronization

The action acquires its application request token before capture. `GitPermalinkInput` contains only root/path strings, one bounded full-document snapshot, charset and immutable line ranges. Once preparation finishes, the confirmation/publication flow retains only the prepared URL, head evidence and ranges, not that source text.

`GitPermalinkLifetime` has request-scoped disposable listeners and weak references to live project/editor/document/file inputs. A document before-change listener latches invalidation, independently of text equality or a restored modification stamp. VFS rename/move/delete events for the file or its ancestors latch structural invalidation. Final UI checks also compare the original document object/stamp, file object/URL/path, editor-document-file association and project/editor lifetime. Closing any final path releases the listeners and weak owners. No action instance or application coordinator retains a prepared request.

`GitHeadSnapshot` records the NIO resolver's actual SHA/remote and metadata file identity, modified time, size and digest. Re-resolving the whole metadata result detects changed symbolic HEAD, loose/packed ref selection, local includes, worktree pointers, and ordinary HEAD/ref ABA. Preparation performs an authoritative background revalidation after the object read and before scheduling the initial UI decision. A dirty approval is checked against the document/file/token/lifetime again, then a second background metadata revalidation runs before the final UI transaction. No Git process or filesystem read runs on EDT.

The final publisher transaction validates document/file identity and revision, current application request and lifetime on EDT immediately before writing. Requests share the existing global sequence with standard, collection, history and status copies across projects. A failed or canceled newer request never revives an older one, and a token is never retried. Localized failure display repeats the current-request/lifetime check immediately before notification, even if logging or notification preparation reenters application code.

External Git writers and the OS clipboard do not share an atomic transaction. The authoritative HEAD observation is the last background revalidation; an external repository change after that observation and before EDT publication is outside that filesystem snapshot. We do not claim a delayed filesystem watcher closes this gap, add repository locks or block EDT with synchronous Git/file reads. Document/VFS events and managed copy requests remain serialized and checked in the final EDT transaction. This boundary applies equally to clean publication and approved dirty publication.

## Publication and verification

Successful results use the existing `GIT_PERMALINK` policy: clipboard, gutter, history, optional notification and status; no analytics or review accounting. `Published(feedbackFailures)` remains success. The shared #109 reporter handles only a current clipboard write failure after publication is attempted; typed Git failures remain in this pre-publication boundary. No side effect or clipboard write is retried.

`GitHeadTargetValidatorTest` uses installed Git and actual commit/tree/blob fixtures: unsaved versus disk/index states, new/renamed/deleted paths, encodings/newlines, regular repositories, linked worktrees, detached HEAD, replace refs, hostile inherited environments, local alternates and missing promisor objects with helper configuration. `GitProcessRunnerTest` separately injects discovery/startup/exit/timeout/output/pipe/cancellation failures, and starts small Java helper processes (128 MiB heap) to check real process termination, stderr pressure and drain-thread release. These helpers and injections do not represent actual Git or OS malfunctions. `GitPermalinkFixtureTest` uses real editor documents, local Git objects and controlled modal/background/UI queues to exercise dirty approval/cancel, document/HEAD/path ABA, project/editor disposal and cross-project request ordering. The existing action, dumb-mode, publisher, #109 clipboard and locale regressions remain part of `allTests`.

Record actual OS, Git/JDK versions, source SHA and executed commands with each result. Windows execution must use installed `git.exe`; an argv unit test alone is not a Windows run. Fixture/headless evidence is not GUI or remote-page evidence. No remote page is fetched to establish a local permalink target.
