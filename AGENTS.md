# AGENTS

## Source of truth
- `README.md` and `docs/mcp-agent-usage.md` are stale in multiple places. Trust `build.gradle.kts`, `src/main/resources/META-INF/plugin.xml`, `ReviewManagerService`, and current action wiring instead.
- IntelliJ platform target in use is `intellijIdea("2026.1.1")` from `build.gradle.kts`. `gradle.properties` still has older template-era platform metadata; do not treat that as the runtime target.

## Commands
- Build distributable plugin: `./gradlew buildPlugin`
- Fast compile check: `./gradlew compileKotlin`
- Run test suite: `./gradlew check`
- Run one test class: `./gradlew test --tests "dev.agentreview.intellij.ReviewManagerServiceTest"`
- Verify plugin against IntelliJ: `./gradlew verifyPlugin`
- Run sandbox IDE: `./gradlew runIde`

## CI / release
- CI runs `buildPlugin`, then `check`, then `verifyPlugin` in separate jobs. Match that order for confidence-sensitive changes.
- Marketplace publish path is `./gradlew publishPlugin` from `.github/workflows/release.yml`; release workflow also patches `CHANGELOG.md` from release notes.

## Architecture
- Plugin entrypoints live in `src/main/resources/META-INF/plugin.xml`.
- Review UI is not a normal toolwindow panel anymore: the left stripe `Review` toolwindow is just a launcher, and the actual review page opens as a custom editor tab via `editor/ReviewPageManager.kt` + `ReviewPageEditorProvider`.
- `ReviewManagerService` is the central project service for review state, selection state, save/load, uncommitted syncing, and prompt export. Start there before changing behavior.
- Uncommitted review is a singleton workspace review created eagerly in `ReviewManagerService.init` and refreshed from `ChangeListListener`. Do not treat it like a normal deletable review.
- Saved review archives are written under `<repo>/.agentic-review/*.json`. Avoid committing that directory.

## Review creation flow
- Commit review selection now uses native IDEA VCS Log only. `Create Review -> Pick in VCS Log...` opens/focuses the main log; the actual review is created from `AgenticReview.StartReviewFromGitLog` on the VCS Log context menu / toolbar.
- There is intentionally no embedded VCS Log picker dialog and no Java bridge to internal VCS Log UI factories.
- Multi-commit reviews are range diffs from the oldest selected commit's first parent to the newest selected commit, with selected commits ordered by commit timestamp (`CommitChangesProvider.getCombinedCommitMetadata`). Non-contiguous selections therefore include the full net range.
- Branch review compares current branch against local `main`, falling back to local `master`; it is unavailable when already on `main`/`master`.

## MCP and prompt quirks
- MCP tools are registered through bundled JetBrains MCP Server (`com.intellij.mcpServer`), not a standalone server.
- `runIde` enables MCP by default with `-Dlocal.review.enable.mcp.by.default=true`.
- `ReviewMcpToolset` returns JSON strings, not raw Kotlin objects. Keep that contract if you touch MCP methods.

## OpenCode plugin publish
- The `agentic-review` opencode plugin lives at `packages/agentic-review/` as an npm package named `opencode-agentic-review`.
- Publish via `.github/workflows/publish-agentic-review-plugin.yml`: `workflow_dispatch` with `patch`/`minor`/`major` — bumps version in `package.json`, builds TS → JS, publishes to npm, tags `agentic-review-vX.Y.Z`.
- Requires `NPM_TOKEN` secret with npm publish permissions.
- Local development copy stays at `.opencode/plugins/agentic-review.ts`; the npm package source in `src/index.ts` adds a `default` export.

## Tests
- Tests use IntelliJ Platform test framework with `@TestApplication` and `projectFixture()`; see `src/test/kotlin/dev/agentreview/intellij/ReviewManagerServiceTest.kt` for the standard pattern.
