# MCP Agent Usage

Current build ships MCP review access through JetBrains bundled MCP Server.

## Available Now

- Create review for uncommitted changes.
- Create review from commit hash.
- Persist comments in workspace state.
- Copy agent prompt with Markdown payload.
- MCP tools:
  - `review_list_reviews`
  - `review_get_review`
  - `review_list_unresolved_comments`
  - `review_get_comment_context`
  - `review_mark_comment_addressed`
  - `review_mark_comment_resolved` (returns disabled by default)
  - `review_export`

## Agent Workflow

1. Create review in `Review` tool window.
2. Add comments.
3. Agent calls review MCP tools against current project.
4. Agent implements requested changes.
5. Agent marks implemented comments `ADDRESSED` through MCP.
6. Human reviews and marks comments `RESOLVED` in UI if desired.

## MCP Status

JetBrains bundled MCP Server exists in IntelliJ IDEA 2025.2+.

This plugin now registers custom review tools through `com.intellij.mcpServer.mcpToolset`.

Current behavior:

- Review read tools are available directly to MCP clients.
- `review_mark_comment_addressed` updates stored comment state and agent metadata.
- `review_mark_comment_resolved` stays disabled by default and returns guidance to use `ADDRESSED` instead.
- Review export supports `markdown` and `json`.
