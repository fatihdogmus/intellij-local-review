package dev.fatihdogmus.localreview.persistence

import dev.fatihdogmus.localreview.model.ReviewComment
import dev.fatihdogmus.localreview.model.ReviewStatus
import dev.fatihdogmus.localreview.model.ReviewTargetType
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class SavedReviewArchive(
    val version: Int = 1,
    val originalReviewId: String,
    val title: String,
    val targetType: ReviewTargetType,
    val beginCommit: String? = null,
    val endCommit: String? = null,
    val subject: String? = null,
    val reviewStatus: ReviewStatus = ReviewStatus.OPEN,
    val createdAt: String,
    val updatedAt: String,
    val comments: List<ReviewComment>,
)

data class ReviewSavePlan(
    val reviewId: String,
    val title: String,
    val filePath: Path,
    val payload: String,
    val fileExists: Boolean,
)

data class ReviewLoadResult(
    val reviewId: String? = null,
    val error: String? = null,
) {
    val ok: Boolean
        get() = error == null
}
