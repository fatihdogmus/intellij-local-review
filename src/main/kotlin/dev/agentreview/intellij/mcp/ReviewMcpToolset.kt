@file:Suppress("unused")

package dev.agentreview.intellij.mcp

import com.intellij.mcpserver.McpCallAdditionalDataElement
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.noSuitableProjectError
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import dev.agentreview.intellij.ReviewManagerService
import dev.agentreview.intellij.model.AgentMetadata
import dev.agentreview.intellij.model.CommentAnchor
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewComment
import dev.agentreview.intellij.model.ReviewStatus
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

class ReviewMcpToolset : McpToolset {
    private val json = Json { prettyPrint = true }

    @McpTool(name = "review_list_reviews")
    @McpDescription("List known local reviews for current project.")
    suspend fun reviewListReviews(
        @McpDescription("Optional review status filter. Example: OPEN")
        status: ReviewStatus? = null,
    ): String {
        val manager = manager()
        return json.encodeToString(ReviewListResult(manager.listReviews(status).map(::toReviewSummary)))
    }

    @McpTool(name = "review_get_review")
    @McpDescription("Get one review and optionally include comments.")
    suspend fun reviewGetReview(
        @McpDescription("Exact review id. Wins over selector when both are provided")
        reviewId: String? = null,
        @McpDescription("Review selector: current, latest, latest-open, uncommitted, or commit:<hash>")
        selector: String? = "current",
        @McpDescription("Include comments attached to review")
        includeComments: Boolean = true,
        @McpDescription("Include comments with RESOLVED status when includeComments is true")
        includeResolved: Boolean = false,
    ): String {
        val review = resolveReview(reviewId, selector)
        val comments = if (!includeComments) {
            emptyList()
        } else {
            review.comments
                .asSequence()
                .filter { includeResolved || it.status != CommentStatus.RESOLVED }
                .sortedWith(compareBy({ it.filePath }, { it.anchor.newLine ?: it.anchor.oldLine ?: Int.MAX_VALUE }, { it.createdAt }))
                .map(::toCommentSummary)
                .toList()
        }
        return json.encodeToString(ReviewResult(toReviewDetails(review, comments)))
    }

    @McpTool(name = "review_list_unresolved_comments")
    @McpDescription("List OPEN comments an agent should work on for one review.")
    suspend fun reviewListUnresolvedComments(
        @McpDescription("Exact review id. Wins over selector when both are provided")
        reviewId: String? = null,
        @McpDescription("Review selector: current, latest, latest-open, uncommitted, or commit:<hash>")
        selector: String? = "current",
    ): String {
        val review = resolveReview(reviewId, selector)
        val comments = review.comments
            .asSequence()
            .filter { it.status == CommentStatus.OPEN }
            .sortedWith(compareBy({ it.filePath }, { it.anchor.newLine ?: it.anchor.oldLine ?: Int.MAX_VALUE }, { it.createdAt }))
            .map(::toCommentSummary)
            .toList()
        return json.encodeToString(CommentListResult(comments))
    }

    @McpTool(name = "review_get_comment_context")
    @McpDescription("Get full context for one review comment, including anchor and review metadata.")
    suspend fun reviewGetCommentContext(
        @McpDescription("Comment id")
        commentId: String,
    ): String {
        val manager = manager()
        val (review, comment) = manager.findCommentWithReview(commentId)
            ?: mcpFail("Comment not found: $commentId")
        return json.encodeToString(
            CommentContextResult(
                comment = toCommentSummary(comment),
                anchor = toCommentAnchorPayload(comment.anchor),
                review = toReviewReference(review),
            ),
        )
    }

    @McpTool(name = "review_mark_comment_resolved")
    @McpDescription("Resolve one comment after implementing requested code change.")
    suspend fun reviewMarkCommentResolved(
        @McpDescription("Comment id")
        commentId: String,
        @McpDescription("Optional implementation note from agent")
        message: String? = null,
        @McpDescription("Agent name. Defaults to MCP client name when available")
        agentName: String? = null,
        @McpDescription("Optional agent run id")
        runId: String? = null,
    ): String {
        val manager = manager()
        manager.findCommentWithReview(commentId) ?: mcpFail("Comment not found: $commentId")
        manager.markCommentResolved(
            commentId = commentId,
            message = message,
            agentName = agentName?.takeIf { it.isNotBlank() } ?: defaultAgentName(),
            runId = runId,
        )
        return json.encodeToString(MutationResult(ok = true, commentId = commentId, newStatus = CommentStatus.RESOLVED.name))
    }

    @McpTool(name = "review_export")
    @McpDescription("Export one review as JSON or Markdown for agent consumption.")
    suspend fun reviewExport(
        @McpDescription("Exact review id. Wins over selector when both are provided")
        reviewId: String? = null,
        @McpDescription("Review selector: current, latest, latest-open, uncommitted, or commit:<hash>")
        selector: String? = "current",
        @McpDescription("Export format: json or markdown")
        format: String = "markdown",
    ): String {
        val manager = manager()
        val review = resolveReview(reviewId, selector)
        val normalizedFormat = format.lowercase()
        val content = when (normalizedFormat) {
            "json" -> json.encodeToString(review)
            "markdown" -> manager.buildAgentPrompt(review.id) ?: mcpFail("Review not found: ${review.id}")
            else -> mcpFail("Unsupported export format '$format'. Supported: json, markdown")
        }
        return json.encodeToString(ExportResult(format = normalizedFormat, content = content))
    }

    private suspend fun manager(): ReviewManagerService = ReviewManagerService.getInstance(currentProject())

    private suspend fun currentProject(): Project = currentCoroutineContext().requireProject()

    private suspend fun resolveReview(reviewId: String?, selector: String?): Review {
        val manager = manager()
        reviewId?.takeIf { it.isNotBlank() }?.let {
            return manager.findReview(it) ?: mcpFail("Review not found: $it")
        }

        return when (val resolvedSelector = selector?.takeIf { it.isNotBlank() } ?: "current") {
            "current" -> manager.getCurrentReview() ?: mcpFail("No current review selected")
            "latest" -> manager.listReviews().firstOrNull() ?: mcpFail("No reviews found")
            "latest-open" -> manager.listReviews(ReviewStatus.OPEN).firstOrNull() ?: mcpFail("No open reviews found")
            "uncommitted" -> manager.listReviews()
                .filter { it.target.type == ReviewTargetType.UNCOMMITTED }
                .firstOrNull() ?: mcpFail("No uncommitted review found")
            else -> resolveSelectorReview(manager, resolvedSelector)
        }
    }

    private fun resolveSelectorReview(manager: ReviewManagerService, selector: String): Review {
        if (!selector.startsWith("commit:")) {
            mcpFail("Unsupported review selector '$selector'. Supported: current, latest, latest-open, uncommitted, commit:<hash>")
        }
        val commitHash = selector.removePrefix("commit:").trim()
        if (commitHash.isBlank()) {
            mcpFail("Commit selector must include hash, example: commit:abc123")
        }
        val matches = manager.listReviews().filter { review ->
            review.target.type == ReviewTargetType.COMMIT &&
                review.target.commitHash?.startsWith(commitHash, ignoreCase = true) == true
        }
        return when (matches.size) {
            0 -> mcpFail("No review found for commit selector '$selector'")
            1 -> matches.single()
            else -> mcpFail(
                "Multiple reviews match selector '$selector': ${matches.joinToString { "${it.id} (${it.title})" }}. Use exact reviewId.",
            )
        }
    }

    private suspend fun defaultAgentName(): String =
        currentCoroutineContext()[McpCallAdditionalDataElement]?.additionalData?.clientInfo?.name ?: "agent"

    private fun CoroutineContext.requireProject(): Project {
        this[McpCallAdditionalDataElement]?.additionalData?.project?.let { return it }
        val openProjects = ProjectManager.getInstance().openProjects.filterNot { it.isDisposed }
        return when (openProjects.size) {
            0 -> mcpFail("No project opened")
            1 -> openProjects.single()
            else -> throw noSuitableProjectError("No exact project is specified while multiple projects are opened.")
        }
    }

    private fun toReviewSummary(review: Review): ReviewSummary {
        return ReviewSummary(
            id = review.id,
            title = review.title,
            type = review.target.type,
            status = review.status,
            openCommentCount = review.comments.count { it.status == CommentStatus.OPEN },
            resolvedCommentCount = review.comments.count { it.status == CommentStatus.RESOLVED },
            repositoryRoot = review.repositoryRoot,
            updatedAt = review.updatedAt,
        )
    }

    private fun toReviewDetails(review: Review, comments: List<CommentSummary>): ReviewDetails {
        return ReviewDetails(
            id = review.id,
            title = review.title,
            target = review.target,
            repositoryRoot = review.repositoryRoot,
            createdAt = review.createdAt,
            updatedAt = review.updatedAt,
            status = review.status,
            openCommentCount = review.comments.count { it.status == CommentStatus.OPEN },
            resolvedCommentCount = review.comments.count { it.status == CommentStatus.RESOLVED },
            comments = comments,
        )
    }

    private fun toReviewReference(review: Review): ReviewReference {
        return ReviewReference(
            id = review.id,
            title = review.title,
            target = review.target,
            repositoryRoot = review.repositoryRoot,
            status = review.status,
        )
    }

    private fun toCommentSummary(comment: ReviewComment): CommentSummary {
        return CommentSummary(
            id = comment.id,
            reviewId = comment.reviewId,
            filePath = comment.filePath,
            line = comment.anchor.newLine ?: comment.anchor.oldLine,
            endLine = comment.anchor.endNewLine ?: comment.anchor.endOldLine,
            body = comment.body,
            status = comment.status,
            selectedText = comment.anchor.selectedText,
            createdAt = comment.createdAt,
            updatedAt = comment.updatedAt,
            author = comment.author,
            agentMetadata = comment.agentMetadata,
        )
    }

    private fun toCommentAnchorPayload(anchor: CommentAnchor): CommentAnchorPayload {
        return CommentAnchorPayload(
            oldLine = anchor.oldLine,
            newLine = anchor.newLine,
            endOldLine = anchor.endOldLine,
            endNewLine = anchor.endNewLine,
            hunkHeader = anchor.hunkHeader,
            selectedText = anchor.selectedText,
            beforeContext = anchor.beforeContext,
            afterContext = anchor.afterContext,
            commitHash = anchor.commitHash,
        )
    }
}

@Serializable
data class ReviewListResult(
    val reviews: List<ReviewSummary>,
)

@Serializable
data class ReviewSummary(
    val id: String,
    val title: String,
    val type: ReviewTargetType,
    val status: ReviewStatus,
    val openCommentCount: Int,
    val resolvedCommentCount: Int,
    val repositoryRoot: String,
    val updatedAt: String,
)

@Serializable
data class ReviewResult(
    val review: ReviewDetails,
)

@Serializable
data class ReviewDetails(
    val id: String,
    val title: String,
    val target: ReviewTarget,
    val repositoryRoot: String,
    val createdAt: String,
    val updatedAt: String,
    val status: ReviewStatus,
    val openCommentCount: Int,
    val resolvedCommentCount: Int,
    val comments: List<CommentSummary>,
)

@Serializable
data class CommentListResult(
    val comments: List<CommentSummary>,
)

@Serializable
data class CommentSummary(
    val id: String,
    val reviewId: String,
    val filePath: String,
    val line: Int?,
    val endLine: Int?,
    val body: String,
    val status: CommentStatus,
    val selectedText: String?,
    val createdAt: String,
    val updatedAt: String,
    val author: String?,
    val agentMetadata: AgentMetadata?,
)

@Serializable
data class CommentContextResult(
    val comment: CommentSummary,
    val anchor: CommentAnchorPayload,
    val review: ReviewReference,
)

@Serializable
data class CommentAnchorPayload(
    val oldLine: Int?,
    val newLine: Int?,
    val endOldLine: Int?,
    val endNewLine: Int?,
    val hunkHeader: String?,
    val selectedText: String?,
    val beforeContext: List<String>,
    val afterContext: List<String>,
    val commitHash: String?,
)

@Serializable
data class ReviewReference(
    val id: String,
    val title: String,
    val target: ReviewTarget,
    val repositoryRoot: String,
    val status: ReviewStatus,
)

@Serializable
data class MutationResult(
    val ok: Boolean,
    val commentId: String? = null,
    val newStatus: String? = null,
    val error: String? = null,
)

@Serializable
data class ExportResult(
    val format: String,
    val content: String,
)
