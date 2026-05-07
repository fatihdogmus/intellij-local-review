package dev.fatihdogmus.localreview.persistence

import dev.fatihdogmus.localreview.model.Review

data class ReviewState(
    var schemaVersion: Int = 1,
    var reviews: MutableList<Review> = mutableListOf(),
)
