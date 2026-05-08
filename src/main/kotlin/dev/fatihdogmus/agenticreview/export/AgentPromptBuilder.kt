package dev.fatihdogmus.agenticreview.export

import dev.fatihdogmus.agenticreview.model.CommentAnchor
import dev.fatihdogmus.agenticreview.model.CommentStatus
import dev.fatihdogmus.agenticreview.model.Review
import dev.fatihdogmus.agenticreview.model.ReviewComment

class AgentPromptBuilder {
    fun build(review: Review): String = buildString {
        val openComments = review.comments
            .filter { it.status == CommentStatus.OPEN }
            .sortedWith(compareBy({ it.filePath }, { it.anchor.newLine ?: it.anchor.oldLine ?: Int.MAX_VALUE }, { it.createdAt }))

        appendLine("# Agentic Review")
        appendLine()
        appendLine("## Instructions")
        appendLine("- Address only comments with status `OPEN` unless told otherwise.")
        appendLine("- Preserve existing behavior unless a comment explicitly asks for behavior change.")
        appendLine("- Add or update tests when appropriate.")
        appendLine("- Mark a comment `RESOLVED` only after the requested change is implemented.")
        appendLine()
        appendLine("## Review")
        appendLine("- Title: ${review.title}")
        appendLine("- Review ID: ${review.id}")
        appendLine("- Repository Root: `${review.repositoryRoot}`")
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
        appendLine("- File: `${comment.filePath}`")
        appendLine("- Lines: ${comment.anchor.lineLabel()}")
        appendLine("- Comment:")
        appendLine(comment.body)
        appendLine()
    }
}

private fun CommentAnchor.lineLabel(): String {
    val start = newLine ?: oldLine ?: 0
    val end = endNewLine ?: endOldLine
    return if (end != null && end > start) "$start-$end" else "$start"
}
