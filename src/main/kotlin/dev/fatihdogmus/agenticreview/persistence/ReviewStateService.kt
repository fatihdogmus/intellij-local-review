package dev.fatihdogmus.agenticreview.persistence

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import dev.fatihdogmus.agenticreview.model.Review

@State(name = "AgenticReviewState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class ReviewStateService : PersistentStateComponent<ReviewState> {
    private var state = ReviewState()

    override fun getState(): ReviewState = state

    override fun loadState(state: ReviewState) {
        this.state = state
    }

    fun reviews(): List<Review> = state.reviews

    fun addReview(review: Review) {
        state.reviews.add(review)
    }

    fun removeReview(reviewId: String) {
        state.reviews.removeIf { it.id == reviewId }
    }

    fun findReview(reviewId: String): Review? = state.reviews.firstOrNull { it.id == reviewId }

    companion object {
        fun getInstance(project: Project): ReviewStateService = project.getService(ReviewStateService::class.java)
    }
}
