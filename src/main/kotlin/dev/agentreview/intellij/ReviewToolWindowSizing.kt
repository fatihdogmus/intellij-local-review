package dev.agentreview.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx

internal fun expandReviewToolWindow(project: Project, toolWindow: ToolWindow) {
    ApplicationManager.getApplication().invokeLater {
        val manager = ToolWindowManager.getInstance(project)
        manager.setMaximized(toolWindow, true)

        val toolWindowEx = toolWindow as? ToolWindowEx ?: return@invokeLater
        when (toolWindow.anchor) {
            ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT -> toolWindowEx.stretchWidth(100_000)
            ToolWindowAnchor.TOP, ToolWindowAnchor.BOTTOM -> toolWindowEx.stretchHeight(100_000)
        }
    }
}
