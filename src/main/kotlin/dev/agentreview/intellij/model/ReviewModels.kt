package dev.agentreview.intellij.model

import kotlinx.serialization.Serializable

@Serializable
data class Review(
    var id: String = "",
    var title: String = "",
    var target: ReviewTarget = ReviewTarget(),
    var repositoryRoot: String = "",
    var createdAt: String = "",
    var updatedAt: String = "",
    var status: ReviewStatus = ReviewStatus.OPEN,
    var comments: MutableList<ReviewComment> = mutableListOf(),
)

@Serializable
data class ReviewTarget(
    var type: ReviewTargetType = ReviewTargetType.UNCOMMITTED,
    var baseRef: String? = null,
    var headRef: String? = null,
    var commitHash: String? = null,
    var parentHash: String? = null,
    var subject: String? = null,
    var changelistId: String? = null,
)

@Serializable
enum class ReviewTargetType {
    UNCOMMITTED,
    COMMIT,
    COMMIT_RANGE,
}

@Serializable
data class ReviewComment(
    var id: String = "",
    var reviewId: String = "",
    var filePath: String = "",
    var anchor: CommentAnchor = CommentAnchor(),
    var body: String = "",
    var status: CommentStatus = CommentStatus.OPEN,
    var createdAt: String = "",
    var updatedAt: String = "",
    var author: String? = null,
    var agentMetadata: AgentMetadata? = null,
)

@Serializable
data class CommentAnchor(
    var side: DiffSide = DiffSide.RIGHT,
    var oldLine: Int? = null,
    var newLine: Int? = null,
    var endOldLine: Int? = null,
    var endNewLine: Int? = null,
    var hunkHeader: String? = null,
    var selectedText: String? = null,
    var beforeContext: List<String> = emptyList(),
    var afterContext: List<String> = emptyList(),
    var commitHash: String? = null,
)

@Serializable
data class AgentMetadata(
    var addressedBy: String? = null,
    var addressedAt: String? = null,
    var message: String? = null,
    var runId: String? = null,
)

@Serializable
enum class ReviewStatus {
    OPEN,
}

@Serializable
enum class CommentStatus {
    OPEN,
    ADDRESSED,
    RESOLVED,
    WONT_FIX,
}

@Serializable
enum class DiffSide {
    LEFT,
    RIGHT,
}

fun ReviewTarget.commitHashIfAny(): String? = commitHash
