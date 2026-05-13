package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import java.nio.file.Path

class DiffRequestBuilder(private val project: Project) {
    fun buildForFile(reviewId: String, changedFile: ChangedFile, repositoryRoot: String): DiffRequest {
        val factory = DiffContentFactory.getInstance()
        val beforeText = changedFile.beforeContent?.text.orEmpty()
        val afterText = changedFile.afterContent?.text.orEmpty()
        val beforeTitle = changedFile.beforeContent?.let { "${it.revisionTitle}  ${it.filePath}" } ?: "Missing  ${changedFile.filePath}"
        val afterTitle = changedFile.afterContent?.let { "${it.revisionTitle}  ${it.filePath}" } ?: "Missing  ${changedFile.filePath}"

        val beforePath = changedFile.beforeContent?.filePath ?: changedFile.previousFilePath ?: changedFile.filePath
        val afterPath = changedFile.filePath
        val beforeFilePath = LocalFilePath(Path.of(repositoryRoot, beforePath).toString(), false)
        val afterFilePath = LocalFilePath(Path.of(repositoryRoot, afterPath).toString(), false)

        val beforeContent = if (beforeText.isEmpty()) factory.createEmpty() else factory.create(project, beforeText, beforeFilePath)
        val afterContent = if (afterText.isEmpty()) factory.createEmpty() else factory.create(project, afterText, afterFilePath)
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
