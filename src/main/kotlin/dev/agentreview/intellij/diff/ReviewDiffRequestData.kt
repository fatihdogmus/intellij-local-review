package dev.agentreview.intellij.diff

import com.intellij.openapi.util.Key
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.vcs.ChangedFile

data class ReviewDiffRequestData(
    val reviewId: String,
    val changedFile: ChangedFile,
    val commentSide: DiffSide,
)

val REVIEW_DIFF_REQUEST_DATA_KEY: Key<ReviewDiffRequestData> = Key.create("local.review.diff.request.data")
