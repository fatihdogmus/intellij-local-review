# Local Review

Local PR-like review workflow for IntelliJ IDEA.

## Current Features

- `Review` tool window.
- Review uncommitted Git changes.
- Review single commit by commit hash.
- Browse changed files and embedded IntelliJ diff.
- Add persistent file/line review comments.
- Mark comments `OPEN`, `ADDRESSED`, `RESOLVED`, `WONT_FIX`.
- Export review as JSON or XML.
- Copy agent prompt with embedded review JSON.

## Current Gaps

- Git Log selected-commit integration still falls back to commit-hash flow.
- MCP custom tools not wired yet.
- Comment anchoring uses manual line entry for MVP.
- Multi-commit range review not implemented.

## Development

Build plugin:

```bash
./gradlew build
```

Run sandbox IDE:

```bash
./gradlew runIde
```
