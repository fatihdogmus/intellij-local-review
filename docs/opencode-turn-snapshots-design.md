# OpenCode Turn Snapshots via IntelliJ MCP

## Goal

Record one Local History-backed snapshot per OpenCode agent turn and show the resulting diff inside the IntelliJ plugin.

The key requirement is that OpenCode edits do not arrive through IntelliJ's own MCP server tool execution. OpenCode's
built-in tools such as `edit`, `write`, and `apply_patch` modify files directly in the workspace, so the design must
follow OpenCode's plugin lifecycle and IntelliJ Local History, not IntelliJ MCP tool-call listeners.

## Primary Decision

Use a dual-mode OpenCode event adapter with one normalized turn lifecycle.

The OpenCode plugin should support both:

- current documented released events
- future v2 `session.next.*` events when they appear at runtime

Normalize both models into one internal lifecycle:

- `onTurnStarted(...)`
- `onTurnFinished(...)`
- `onTurnFailed(...)`
- `onTurnPatch(...)`

The OpenCode plugin should notify the IntelliJ plugin by calling dedicated IntelliJ MCP tools:

- `review_turn_snapshot_begin`
- `review_turn_snapshot_end`

The IntelliJ plugin should collect Local History change set IDs while a turn is active, then build the final diff from
Local History when the turn ends. These MCP tools do not exist yet and need to be implemented in the plugin.

## Why This Boundary

Do not use `apply_patch` or `edit` before/after hooks as the primary turn boundary.

Reasons:

- one turn may contain multiple edit tools
- one turn may also mutate files through `bash`
- formatters and follow-up writes may happen after the initial edit tool
- tool-level hooks are useful metadata, but they are too narrow to represent one full agent turn

OpenCode has enough lifecycle signals to model turns, but they currently exist in two forms:

- legacy/released message-part events
- future v2 top-level step events

The design should align with both and automatically prefer the newer model when it becomes available.

## OpenCode Capabilities

### Available plugin hooks

OpenCode server plugins can observe:

- global bus events through `event`
- tool lifecycle through `tool.execute.before`
- tool lifecycle through `tool.execute.after`

Relevant locations explored:

- `~/yte/tedarik/opencode/packages/plugin/src/index.ts`
- `~/yte/tedarik/opencode/packages/opencode/src/plugin/index.ts`
- `~/yte/tedarik/opencode/packages/opencode/src/session/prompt.ts`

### Available current released signals

The currently documented and released plugin event surface includes:

- `message.part.updated`
- `session.idle`
- `session.diff`
- `tool.execute.before`
- `tool.execute.after`

The non-v2 message part model includes:

- `step-start`
- `step-finish`
- `patch`
- `tool`

Relevant locations explored:

- `~/yte/tedarik/opencode/packages/sdk/js/src/gen/types.gen.ts`
- `~/yte/tedarik/opencode/packages/opencode/src/session/processor.ts`
- `https://opencode.ai/docs/plugins`

### Available future v2 signals

OpenCode source also contains a newer event model with top-level step events:

- `session.next.step.started`
- `session.next.step.ended`
- `session.next.step.failed`
- `session.next.tool.called`
- `session.next.tool.success`
- `session.next.tool.failed`

These are not currently documented as a stable public feature.

Relevant locations explored:

- `~/yte/tedarik/opencode/packages/opencode/src/v2/session-event.ts`
- `~/yte/tedarik/opencode/packages/sdk/js/src/v2/gen/types.gen.ts`
- `~/yte/tedarik/opencode/packages/opencode/src/session/processor.ts`

### Tool metadata already available

OpenCode edit tools already produce useful metadata:

- `edit` returns `diff` and `filediff`
- `apply_patch` builds per-file patch metadata and emits edited-file events
- `write` returns filepath metadata

Relevant locations explored:

- `~/yte/tedarik/opencode/packages/opencode/src/tool/edit.ts`
- `~/yte/tedarik/opencode/packages/opencode/src/tool/apply_patch.ts`
- `~/yte/tedarik/opencode/packages/opencode/src/tool/write.ts`

## IntelliJ Capabilities

### Local History write-side API

IntelliJ Local History supports named actions and labels, but for this feature the important part is grouping and later
diff reconstruction.

Relevant locations explored:

- `~/yte/tedarik/intellij-community/platform/lvcs-api/src/com/intellij/history/LocalHistory.kt`
- `~/yte/tedarik/intellij-community/platform/lvcs-impl/src/com/intellij/history/core/LocalHistoryFacade.kt`

### Local History diff-side API

The internal `lvcs-impl` layer can:

- enumerate change sets
- identify `changeSet.id`
- build single-file and multi-file diffs from Local History revisions

Relevant locations explored:

-
`~/yte/tedarik/intellij-community/platform/lvcs-impl/src/com/intellij/platform/lvcs/impl/LocalHistoryActivityProvider.kt`
- `~/yte/tedarik/intellij-community/platform/lvcs-impl/src/com/intellij/platform/lvcs/impl/diff/DiffUtils.kt`
- `~/yte/tedarik/intellij-community/platform/lvcs-impl/src/com/intellij/platform/lvcs/impl/diff/ActivityDiffDataImpl.kt`

### IntelliJ MCP transport

The bundled JetBrains MCP server already exposes an HTTP MCP transport.

Important details:

- stream URL: `/stream`
- SSE URL: `/sse`
- requests are JSON-RPC over HTTP
- the session is negotiated with `initialize`
- subsequent requests use the `mcp-session-id` header

Relevant locations explored:

- `~/yte/tedarik/intellij-community/plugins/mcp-server/src/com/intellij/mcpserver/impl/McpServerService.kt`
-
`~/yte/tedarik/intellij-community/plugins/mcp-server/src/com/intellij/mcpserver/impl/util/network/McpServerConnectionAddressProvider.kt`
-
`~/yte/tedarik/intellij-community/plugins/mcp-server/src/com/intellij/mcpserver/impl/util/network/StreamableHttpServerTransport.kt`
- `~/yte/tedarik/intellij-community/plugins/mcp-server/src/com/intellij/mcpserver/impl/util/network/mcp.sdk.util.kt`

## Transport Decision

Do not expose a custom HTTP endpoint from the IntelliJ plugin.

Instead, the OpenCode plugin should call normal IntelliJ MCP tools through the JetBrains MCP server.

### Preferred client approach

First try a standard external JavaScript MCP client dependency instead of writing a hand-rolled `fetch` transport.

Proposed direction:

- use a standard JS MCP client library, for example `@modelcontextprotocol/sdk`, if it works cleanly in OpenCode's
  Bun/TypeScript runtime and supports the required HTTP transport

Why prefer a client library first:

- keeps protocol details out of our plugin code
- avoids hand-maintaining JSON-RPC session logic
- makes future MCP evolution easier to absorb

### Fallback approach

If a standard external client does not fit Bun/runtime constraints or transport expectations, fall back to a very small
custom HTTP MCP caller that only supports:

- `initialize`
- `notifications/initialized`
- `tools/call`

That fallback is acceptable because this feature only needs simple request/response MCP calls.

## OpenCode Event Strategy

### Compatibility goal

The OpenCode plugin should work on current released OpenCode now and automatically adopt the cleaner v2 step events later
without requiring a redesign.

### Event adapter

Implement a small adapter in the OpenCode plugin that converts whichever event model is present into one normalized
internal API:

```ts
onTurnStarted(...)
onTurnFinished(...)
onTurnFailed(...)
onTurnPatch(...)
```

This compatibility logic belongs on the OpenCode side, not the IntelliJ side.

### Legacy mode

Primary supported mode for current releases:

- begin on `message.part.updated` where `part.type === "step-start"`
- mark finishing on `message.part.updated` where `part.type === "step-finish"`
- collect changed files from `message.part.updated` where `part.type === "patch"`
- finalize on `session.idle` or a short bounded flush after `step-finish`

Important detail:

- in current source, `patch` is emitted after `step-finish`
- therefore legacy mode should not finalize the turn immediately on `step-finish`

### v2 mode

Preferred mode when available at runtime:

- begin on `session.next.step.started`
- finish on `session.next.step.ended`
- fail on `session.next.step.failed`

Patch and tool metadata can still be consumed for enrichment if available.

### Auto-detection and precedence

Do runtime detection, not version checks.

Per `sessionID`:

1. start in legacy mode
2. if any `session.next.step.*` event is observed, switch that session to v2 mode
3. once a session is in v2 mode, ignore legacy step-boundary events for that session

This prevents duplicate begin/end handling if OpenCode emits both models during a migration period.

## High-Level Flow

### 1. OpenCode turn starts

The OpenCode plugin receives either:

- legacy: `message.part.updated` with `part.type === "step-start"`
- v2: `session.next.step.started`

It then calls IntelliJ MCP tool:

- `review_turn_snapshot_begin(sessionId, stepId, projectPath, agent, model, timestamp)`

### 2. Tools execute during the turn

The OpenCode plugin observes `tool.execute.after` and/or `session.next.tool.success` events.

It may accumulate optional metadata such as:

- tool name
- call ID
- changed paths inferred from tool metadata
- file diffs reported by OpenCode tools

This metadata is optional enrichment, not the source of truth for the final diff.

### 3. IntelliJ tracks Local History while turn is active

When `review_turn_snapshot_begin` is received, the IntelliJ plugin:

- creates an active turn record for the project/worktree
- subscribes to Local History change set completion if not already subscribed
- starts collecting `changeSet.id` values while that turn is active

### 4. OpenCode turn ends

The OpenCode plugin receives either:

- legacy: `message.part.updated` with `part.type === "step-finish"`, then waits for `patch` and/or `session.idle`
- v2: `session.next.step.ended` or `session.next.step.failed`

It then calls IntelliJ MCP tool:

- `review_turn_snapshot_end(sessionId, stepId, projectPath, timestamp, status, changedPaths?, toolCalls?)`

### 5. IntelliJ finalizes the turn

When `review_turn_snapshot_end` is received, the IntelliJ plugin:

- stops collecting change sets for that active turn
- persists the turn record
- reconstructs the diff from Local History using collected change set IDs
- optionally narrows display scope using reported `changedPaths`

## Source of Truth

The source of truth for the final turn diff should be IntelliJ Local History, not OpenCode-reported file lists.

Use OpenCode metadata only for:

- UI summaries
- debugging
- narrowing visible files when safe
- per-tool drilldown in future iterations

Why:

- it catches edits made by `bash`
- it includes formatter rewrites and follow-up edits
- it preserves rename/move/delete semantics inside IntelliJ's own history model

## IntelliJ Plugin Design

### New MCP tools

Add to `ReviewMcpToolset` or a sibling MCP toolset:

- `review_turn_snapshot_begin`
- `review_turn_snapshot_end`
- optional later: `review_list_turn_snapshots`
- optional later: `review_get_turn_snapshot`

These should keep the existing contract of returning JSON strings.

### New project service

Add a project service, for example:

- `TurnSnapshotService`

Suggested responsibilities:

- manage the current active turn per project/worktree
- persist completed turns
- subscribe to Local History facade listener
- build diff data for the UI

### Suggested data model

```kotlin
data class TurnSnapshot(
    val id: String,
    val sessionId: String,
    val stepId: String,
    val projectPath: String,
    val startedAt: String,
    var endedAt: String? = null,
    var status: String = "running",
    val changeSetIds: MutableList<Long> = mutableListOf(),
    val changedPaths: MutableSet<String> = linkedSetOf(),
    val toolCalls: MutableList<TurnToolCall> = mutableListOf(),
)

data class TurnToolCall(
    val callId: String,
    val tool: String,
    val changedPaths: MutableSet<String> = linkedSetOf(),
    val metadataJson: String? = null,
)
```

### Local History subscription

Use `LocalHistoryImpl.getInstanceImpl().facade?.addListener(...)` or an equivalent internal path through
`LocalHistoryFacade.Listener`.

While a turn is active:

- every finished relevant change set ID is appended to the active turn

Important caveat:

- this relies on `lvcs-impl`, which is internal/experimental IntelliJ API
- acceptable for a pinned IDE build, but not a stable external API surface

### Concurrency rule

Support only one active turn per project/worktree at first.

If another begin signal arrives while one turn is active:

- reject it, or
- mark overlap and degrade gracefully

Do not attempt precise attribution for overlapping turns in the first version.

## OpenCode Plugin Design

### Plugin hooks to use

- `event`
- `tool.execute.after`

### Event handling

On normalized turn start:

- open or reuse MCP client session to IntelliJ
- call `review_turn_snapshot_begin`

On `tool.execute.after`:

- if tool is relevant (`edit`, `write`, `apply_patch`, `bash`), accumulate tool metadata in memory for the active turn

On normalized turn end:

- call `review_turn_snapshot_end`
- include accumulated changed-path/tool metadata when available
- clear local step accumulator

### Legacy implementation details

For current released OpenCode, derive turn lifecycle from:

- `message.part.updated` with `step-start`
- `message.part.updated` with `step-finish`
- `message.part.updated` with `patch`
- `session.idle`

Recommended legacy behavior:

1. begin on `step-start`
2. mark the turn as finishing on `step-finish`
3. collect `patch.files` if emitted
4. finalize on `session.idle` or a short debounce if `patch` never arrives

### v2 implementation details

If `session.next.step.*` events are observed, use them as the authoritative turn boundaries for that session.

The plugin should still accept `tool.execute.after` and optional patch-like metadata for enrichment.

### Step identifier

Use the event ID from OpenCode's step event if available; otherwise derive a deterministic step key from:

- `sessionID`
- event timestamp
- incrementing counter within plugin state

The begin and end call must use the same step identifier.

## Diff Presentation

### First version

Show one net diff per turn snapshot.

This is the most useful first implementation:

- user selects a turn
- plugin shows the full change caused during that turn

### Later version

Add drilldown per tool call:

- turn
- tool calls within turn
- changed files within tool call

That data model is already supported by the design above, but it should not block the first version.

## Why not rely on OpenCode snapshots

OpenCode has its own snapshot system and step events may already include optional snapshot identifiers.

Do not use OpenCode snapshots as the primary diff engine in the first version.

Reasons:

- the IntelliJ plugin already needs Local History diffs for native UI integration
- Local History is closer to the IDE's actual view of file state
- using both snapshot systems as sources of truth would complicate attribution and debugging

OpenCode snapshot IDs can still be stored as diagnostic metadata for troubleshooting.

## Proposed Implementation Order

1. Add design-approved MCP tools in IntelliJ plugin:
    - `review_turn_snapshot_begin`
    - `review_turn_snapshot_end`
2. Add IntelliJ `TurnSnapshotService` and Local History listener wiring.
3. Persist minimal completed turn records with:
    - session ID
    - step ID
    - timestamps
    - change set IDs
    - changed paths
4. Build a minimal turn list UI in the IntelliJ plugin.
5. Render one net diff for a selected turn.
6. Add OpenCode plugin implementation using:
    - dual legacy/v2 lifecycle adapter
    - external JS MCP client dependency
    - optional tool metadata enrichment
7. Later, add per-tool drilldown and better overlap handling.

## Open Questions

1. Which external JS MCP client library is the best fit for OpenCode's Bun runtime and JetBrains streamable HTTP
   transport?
2. Should the OpenCode plugin reuse an already configured IntelliJ MCP server entry, or should it accept a dedicated MCP
   target config for the turn-snapshot integration?
3. Do we want turn snapshot state stored inside the existing review persistence model, or in a separate persistence
   service?
4. Should turn snapshots appear inside the current review UI, or in a separate view/tab?
5. How should overlapping turns be surfaced if they occur unexpectedly?
6. In legacy mode, what debounce/window is safest between `step-finish` and final flush when no `patch` arrives?

## Current Recommendation

Proceed with:

- dual-mode OpenCode event adapter
- legacy documented released events as the primary supported path
- v2 step events as the preferred auto-detected path when available
- IntelliJ MCP tools as the control plane
- IntelliJ Local History as the diff source of truth
- external JS MCP client first, custom `fetch` transport only as fallback
