package dev.agentreview.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import dev.agentreview.intellij.ui.ReviewListPanel

class ReviewListToolWindowPanel(project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    private val manager = ReviewManagerService.getInstance(project)
    private val reviewListPanel = ReviewListPanel()
    private val listener: () -> Unit = {
        reviewListPanel.setReviews(manager.listReviews(), manager.currentReviewId)
    }

    init {
        manager.addListener(listener)
        reviewListPanel.onSelectionChanged = { reviewId ->
            manager.selectReview(reviewId)
        }
        reviewListPanel.onDeleteRequested = { review ->
            manager.deleteReview(review.id)
        }
        setContent(reviewListPanel.component)
        reviewListPanel.setReviews(manager.listReviews(), manager.currentReviewId)
    }

    override fun dispose() {
        manager.removeListener(listener)
    }
}
