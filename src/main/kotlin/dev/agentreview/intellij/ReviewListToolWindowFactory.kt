package dev.agentreview.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
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
            if (manager.confirmDelete(review)) {
                manager.deleteReview(review.id)
            }
        }
        setContent(reviewListPanel.component)
        reviewListPanel.setReviews(manager.listReviews(), manager.currentReviewId)
    }

    override fun dispose() {
        manager.removeListener(listener)
    }
}

class ReviewListToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewListToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
