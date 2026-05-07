# MCP Agent Usage

Current build ships core review workflow first.

## Available Now

- Create review for uncommitted changes.
- Create review from commit hash.
- Persist comments in workspace state.
- Export review JSON.
- Copy agent prompt with JSON payload.

## Temporary Agent Workflow

1. Create review in `Review` tool window.
2. Add comments.
3. Use `Copy Agent Prompt` or `Export JSON`.
4. Give prompt or JSON to coding agent.
5. After code changes, mark comments `ADDRESSED` or `RESOLVED` in UI.

## MCP Status

JetBrains bundled MCP Server exists in IntelliJ IDEA 2025.2+.

Custom tool registration API still not wired in this codebase. Research done against JetBrains MCP docs and deprecated `mcp-server-plugin` README. Next step: inspect bundled MCP plugin extension point classes and register tools for:

- `review_list_reviews`
- `review_get_review`
- `review_list_unresolved_comments`
- `review_get_comment_context`
- `review_mark_comment_addressed`
- `review_mark_comment_resolved`
- `review_export`
