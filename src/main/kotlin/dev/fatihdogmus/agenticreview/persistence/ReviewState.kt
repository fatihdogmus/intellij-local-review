package dev.fatihdogmus.agenticreview.persistence

import dev.fatihdogmus.agenticreview.model.Review

data class ReviewState(
    var schemaVersion: Int = 1,
    var reviews: MutableList<Review> = mutableListOf(),
)
