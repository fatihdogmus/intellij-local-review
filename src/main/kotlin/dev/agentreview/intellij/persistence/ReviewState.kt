package dev.agentreview.intellij.persistence

import dev.agentreview.intellij.model.Review

data class ReviewState(
    var schemaVersion: Int = 1,
    var reviews: MutableList<Review> = mutableListOf(),
)
