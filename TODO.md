# Local Review Plugin Status

## Done

- [x] Replace template plugin setup with `Local Review` plugin metadata and actions.
- [x] Add project-level persistent review state service.
- [x] Add review domain models for reviews, targets, comments, anchors, statuses.
- [x] Add `Review` tool window and empty state.
- [x] Add review list, changed files list, comments panel, embedded diff panel.
- [x] Add uncommitted changes review flow.
- [x] Add commit-hash review flow.
- [x] Add persistent comment create/edit/status actions.
- [x] Add JSON export, XML export, and agent prompt copy.
- [x] Add basic automated tests.
- [x] Make `./gradlew build` pass.

## Missing

- [x] Wire real Git Log selected-commit integration.
- [ ] Implement MCP custom tools for review access and comment status updates.
- [ ] Add stale anchor detection and re-anchoring.
- [ ] Add comment context extraction from current diff caret/selection.
- [ ] Add multi-commit or commit-range review support.
- [ ] Add settings for MCP enablement and agent resolve permissions.

## Next Suggested Order

1. MCP tool registration.
2. Better anchoring and stale detection.
3. Settings and polish.
