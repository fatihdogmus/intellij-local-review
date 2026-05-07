package dev.agentreview.intellij.editor

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class ReviewPageManager(
    private val project: Project,
) {
    private val file = ReviewPageVirtualFile()

    fun open() {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    fun file(): ReviewPageVirtualFile = file

    companion object {
        fun getInstance(project: Project): ReviewPageManager = project.getService(ReviewPageManager::class.java)
    }
}
