package dev.fatihdogmus.localreview

import com.intellij.ide.FileSelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowId
import dev.fatihdogmus.localreview.vcs.ChangedFile
import dev.fatihdogmus.localreview.vcs.ChangedFileStatus

object ReviewFileNavigator {
    fun openChangedFile(project: Project, repositoryRoot: String, changedFile: ChangedFile) {
        if (changedFile.status == ChangedFileStatus.DELETED) return
        val absolutePath = repositoryRoot.trimEnd('/') + "/" + changedFile.filePath
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath) ?: return
        OpenFileDescriptor(project, virtualFile).navigate(true)
        val context = FileSelectInContext(project, virtualFile)
        SelectInManager.getInstance(project).targetList
            .firstOrNull { it.toolWindowId == ToolWindowId.PROJECT_VIEW && ApplicationManager.getApplication().runReadAction<Boolean> { it.canSelect(context) } }
            ?.selectIn(context, true)
    }
}
