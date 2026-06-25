package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import java.nio.file.Path

class DiffRequestBuilder(private val project: Project) {
    fun buildForFile(reviewId: String, changedFile: ChangedFile, repositoryRoot: String): DiffRequest {
        val beforeContent = createContent(
            changedFile.beforeContent,
            repositoryRoot,
            changedFile.previousFilePath ?: changedFile.filePath,
        )
        val afterContent = createContent(changedFile.afterContent, repositoryRoot, changedFile.filePath)

        val beforeTitle = changedFile.beforeContent?.let { "${it.revisionTitle}  ${it.filePath}" }
            ?: "Missing  ${changedFile.filePath}"
        val afterTitle = changedFile.afterContent?.let { "${it.revisionTitle}  ${it.filePath}" }
            ?: "Missing  ${changedFile.filePath}"

        val request = SimpleDiffRequest(changedFile.filePath, beforeContent, afterContent, beforeTitle, afterTitle)

        val beforeIsWorktree = changedFile.beforeContent?.isWorktreeRevision() == true
        val afterIsWorktree = changedFile.afterContent?.isWorktreeRevision() == true
        if (!beforeIsWorktree && afterIsWorktree) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT)
        if (beforeIsWorktree && !afterIsWorktree) request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.LEFT)

        return request.apply {
            putUserData(
                REVIEW_DIFF_REQUEST_DATA_KEY,
                ReviewDiffRequestData(
                    reviewId = reviewId,
                    changedFile = changedFile,
                    commentSide = if (changedFile.afterContent != null) DiffSide.RIGHT else DiffSide.LEFT,
                ),
            )
        }
    }

    private fun createContent(
        revision: ReviewContent?,
        repositoryRoot: String,
        fallbackPath: String,
    ): DiffContent {
        if (revision == null) return DiffContentFactory.getInstance().createEmpty()
        val factory = DiffContentFactory.getInstance()
        val absolutePath = Path.of(repositoryRoot, revision.filePath.ifBlank { fallbackPath }).toString()
        val filePath = LocalFilePath(absolutePath, false)
        val virtualFile: VirtualFile? = filePath.virtualFile

        if (revision.isWorktreeRevision() && virtualFile != null) {
            return factory.create(project, virtualFile)
        }

        val highlightFile: VirtualFile = virtualFile ?: LightVirtualFile(
            filePath.name,
            FileTypeManager.getInstance().getFileTypeByFileName(filePath.name),
            revision.text,
        )
        return factory.create(project, revision.text, highlightFile)
    }

    private fun ReviewContent.isWorktreeRevision(): Boolean = revisionTitle.isBlank() || revisionTitle == "WORKTREE"
}
