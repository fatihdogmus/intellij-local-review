package dev.agentreview.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import javax.swing.Icon
import javax.swing.JPanel

class ReviewStripeToolWindowFactory : ToolWindowFactory, DumbAware {
    override val icon: Icon = IconLoader.getIcon("/icons/review.svg", ReviewStripeToolWindowFactory::class.java)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.contentManager.contentCount == 0) {
            toolWindow.contentManager.addContent(toolWindow.contentManager.factory.createContent(JPanel(), "", false))
        }

        project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(shownToolWindow: ToolWindow) {
                if (shownToolWindow.id != toolWindow.id) return
                openReviewAndHide(project, shownToolWindow)
            }
        })

        openReviewAndHide(project, toolWindow)
    }

    private fun openReviewAndHide(project: Project, toolWindow: ToolWindow) {
        ToolWindowManager.getInstance(project).invokeLater {
            ReviewManagerService.getInstance(project).openDefaultReview()
            if (toolWindow.isVisible) {
                toolWindow.hide()
            }
        }
    }
}
