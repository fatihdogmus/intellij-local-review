package dev.agentreview.intellij

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import dev.agentreview.intellij.vcs.ChangedFile
import dev.agentreview.intellij.vcs.ChangedFileStatus

object ReviewFileNavigator {
    fun openChangedFile(project: Project, repositoryRoot: String, changedFile: ChangedFile) {
        if (changedFile.status == ChangedFileStatus.DELETED) return
        val absolutePath = repositoryRoot.trimEnd('/') + "/" + changedFile.filePath
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(absolutePath) ?: return
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }
}
