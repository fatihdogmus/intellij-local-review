package dev.agentreview.intellij.export

import dev.agentreview.intellij.model.CommentAnchor
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewComment

class AgentPromptBuilder {
    fun build(review: Review): String = buildString {
        val openComments = review.comments
            .filter { it.status == CommentStatus.OPEN }
            .sortedWith(compareBy({ it.filePath }, { it.anchor.newLine ?: it.anchor.oldLine ?: Int.MAX_VALUE }, { it.createdAt }))

        appendLine("# Local Review")
        appendLine()
        appendLine("## Instructions")
        appendLine("- Address only comments with status `OPEN` unless told otherwise.")
        appendLine("- Preserve existing behavior unless a comment explicitly asks for behavior change.")
        appendLine("- Add or update tests when appropriate.")
        appendLine("- After implementing a comment, mark it `ADDRESSED` if tooling is available.")
        appendLine("- Do not mark comments `RESOLVED` unless explicitly allowed.")
        appendLine()
        appendLine("## Review")
        appendLine("- Title: ${review.title}")
        appendLine("- Review ID: ${review.id}")
        appendLine("- Status: ${review.status}")
        appendLine("- Repository Root: `${review.repositoryRoot}`")
        appendLine("- Created At: ${review.createdAt}")
        appendLine("- Updated At: ${review.updatedAt}")
        appendLine("- Target Type: ${review.target.type}")
        review.target.baseRef?.let { appendLine("- Base Ref: `$it`") }
        review.target.headRef?.let { appendLine("- Head Ref: `$it`") }
        review.target.commitHash?.let { appendLine("- Commit Hash: `$it`") }
        review.target.parentHash?.let { appendLine("- Parent Hash: `$it`") }
        review.target.subject?.let { appendLine("- Subject: ${it}") }
        appendLine("- Open Comments: ${openComments.size}")
        appendLine()
        appendLine("## Open Comments")

        if (openComments.isEmpty()) {
            appendLine("No open comments.")
            return@buildString
        }

        openComments.forEachIndexed { index, comment ->
            appendCommentBlock(index + 1, comment)
        }
    }

    private fun StringBuilder.appendCommentBlock(index: Int, comment: ReviewComment) {
        appendLine("### Comment $index")
        appendLine("- Comment ID: ${comment.id}")
        appendLine("- File: `${comment.filePath}`")
        appendLine("- Status: ${comment.status}")
        appendLine("- Severity: ${comment.severity}")
        appendLine("- Side: ${comment.anchor.side}")
        appendLine("- Lines: ${comment.anchor.lineLabel()}")
        comment.anchor.commitHash?.let { appendLine("- Anchor Commit: `$it`") }
        comment.author?.let { appendLine("- Author: ${it}") }
        appendLine("- Created At: ${comment.createdAt}")
        appendLine("- Updated At: ${comment.updatedAt}")
        appendLine()
        appendLine("**Comment**")
        appendLine(comment.body)
        appendLine()

        comment.anchor.selectedText?.takeIf { it.isNotBlank() }?.let {
            appendLine("**Selected Text**")
            appendCodeBlock(it)
        }
        if (comment.anchor.beforeContext.isNotEmpty()) {
            appendLine("**Before Context**")
            appendCodeBlock(comment.anchor.beforeContext.joinToString("\n"))
        }
        if (comment.anchor.afterContext.isNotEmpty()) {
            appendLine("**After Context**")
            appendCodeBlock(comment.anchor.afterContext.joinToString("\n"))
        }
    }

    private fun StringBuilder.appendCodeBlock(content: String) {
        appendLine("```text")
        appendLine(content.replace("```", "'''"))
        appendLine("```")
        appendLine()
    }
}

private fun CommentAnchor.lineLabel(): String {
    val start = newLine ?: oldLine ?: 0
    val end = endNewLine ?: endOldLine
    return if (end != null && end > start) "$start-$end" else "$start"
}
