package dev.agentreview.intellij

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.agentreview.intellij.ui.ReviewToolWindowPanel

class ReviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ReviewToolWindowPanel(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setPreferredFocusableComponent(panel.focusTarget)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        toolWindow.show {
            expandReviewToolWindow(project, toolWindow)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
