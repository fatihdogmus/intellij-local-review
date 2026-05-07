package dev.fatihdogmus.localreview.diff

import com.intellij.openapi.util.Key
import dev.fatihdogmus.localreview.model.DiffSide
import dev.fatihdogmus.localreview.vcs.ChangedFile

data class ReviewDiffRequestData(
    val reviewId: String,
    val changedFile: ChangedFile,
    val commentSide: DiffSide,
)

val REVIEW_DIFF_REQUEST_DATA_KEY: Key<ReviewDiffRequestData> = Key.create("local.review.diff.request.data")
