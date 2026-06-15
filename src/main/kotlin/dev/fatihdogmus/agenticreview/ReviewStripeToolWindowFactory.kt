package dev.fatihdogmus.agenticreview

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import javax.swing.Icon
import javax.swing.JPanel

class ReviewStripeToolWindowFactory : ToolWindowFactory, DumbAware {
    override val icon: Icon = IconLoader.getIcon("/icons/review.svg", ReviewStripeToolWindowFactory::class.java)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (toolWindow.contentManager.contentCount == 0) {
            val content = toolWindow.contentManager.factory.createContent(JPanel(), "", false)
            val disposable = Disposable {}
            content.setDisposer(disposable)
            toolWindow.contentManager.addContent(content)

            project.messageBus.connect(disposable)
                .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                    override fun toolWindowShown(shownToolWindow: ToolWindow) {
                        if (shownToolWindow.id != toolWindow.id) return
                        openReviewAndHide(project, shownToolWindow)
                    }
                })
        }

        openReviewAndHide(project, toolWindow)
    }

    private fun openReviewAndHide(project: Project, toolWindow: ToolWindow) {
        ToolWindowManager.getInstance(project).invokeLater {
            hideOtherSideToolWindows(project, toolWindow.id)
            ReviewManagerService.getInstance(project).openDefaultReview()
            if (toolWindow.isVisible) {
                toolWindow.hide()
            }
        }
    }

    private fun hideOtherSideToolWindows(project: Project, reviewToolWindowId: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.toolWindowIds
            .asSequence()
            .filter { it != reviewToolWindowId }
            .mapNotNull(toolWindowManager::getToolWindow)
            .filter { it.isVisible && (it.anchor == ToolWindowAnchor.LEFT || it.anchor == ToolWindowAnchor.RIGHT) }
            .forEach { it.hide() }
    }
}
