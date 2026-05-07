# Local Review

Local PR-like review workflow for IntelliJ IDEA.

## Current Features

- `Review` tool window.
- Review uncommitted Git changes.
- Review single commit by commit hash.
- Browse changed files and embedded IntelliJ diff.
- Add persistent file/line review comments.
- Mark comments `OPEN`, `ADDRESSED`, `RESOLVED`, `WONT_FIX`.
- Copy agent prompt with embedded review context.
- MCP tools for listing reviews, reading comments, exporting review data, and marking comments `ADDRESSED`.

## Current Gaps

- Git Log selected-commit integration still falls back to commit-hash flow.
- Agent comment resolve through MCP is intentionally disabled by default.
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
