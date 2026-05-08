package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile

class DiffRequestBuilder(private val project: Project) {
    fun buildForFile(reviewId: String, changedFile: ChangedFile): DiffRequest {
        val factory = DiffContentFactory.getInstance()
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(changedFile.filePath)
        val beforeText = changedFile.beforeContent?.text.orEmpty()
        val afterText = changedFile.afterContent?.text.orEmpty()
        val beforeTitle = changedFile.beforeContent?.let { "${it.revisionTitle}  ${it.filePath}" } ?: "Missing  ${changedFile.filePath}"
        val afterTitle = changedFile.afterContent?.let { "${it.revisionTitle}  ${it.filePath}" } ?: "Missing  ${changedFile.filePath}"
        val beforeContent = if (beforeText.isEmpty()) factory.createEmpty() else factory.create(project, beforeText, fileType)
        val afterContent = if (afterText.isEmpty()) factory.createEmpty() else factory.create(project, afterText, fileType)
        return SimpleDiffRequest(
            changedFile.filePath,
            beforeContent,
            afterContent,
            beforeTitle,
            afterTitle,
        ).apply {
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
}
