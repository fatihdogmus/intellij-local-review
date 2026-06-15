package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import java.nio.file.Path

class DiffRequestBuilder(private val project: Project) {
    fun buildForFile(reviewId: String, changedFile: ChangedFile, repositoryRoot: String): DiffRequest {
        val change = Change(
            createRevision(
                repositoryRoot,
                changedFile.beforeContent,
                changedFile.previousFilePath ?: changedFile.filePath
            ),
            createRevision(repositoryRoot, changedFile.afterContent, changedFile.filePath),
            changedFile.status.toFileStatus(),
        )
        val producer = ChangeDiffRequestProducer.create(project, change)
        val request = producer?.process(UserDataHolderBase(), EmptyProgressIndicator())
            ?: fallbackRequest(changedFile, repositoryRoot)
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

    private fun createRevision(
        repositoryRoot: String,
        content: ReviewContent?,
        fallbackPath: String
    ): ContentRevision? {
        if (content == null) return null
        val filePath =
            LocalFilePath(Path.of(repositoryRoot, content.filePath.ifBlank { fallbackPath }).toString(), false)
        if (content.isWorktreeRevision() && filePath.virtualFile != null) {
            return CurrentContentRevision.create(filePath)
        }
        return SimpleContentRevision(content.text, filePath, content.revisionTitle)
    }

    private fun ReviewContent.isWorktreeRevision(): Boolean = revisionTitle.isBlank() || revisionTitle == "WORKTREE"

    private fun ChangedFileStatus.toFileStatus(): FileStatus = when (this) {
        ChangedFileStatus.ADDED -> FileStatus.ADDED
        ChangedFileStatus.DELETED -> FileStatus.DELETED
        ChangedFileStatus.MODIFIED,
        ChangedFileStatus.RENAMED,
        ChangedFileStatus.COPIED,
        ChangedFileStatus.UNKNOWN,
            -> FileStatus.MODIFIED
    }

    private fun fallbackRequest(changedFile: ChangedFile, repositoryRoot: String): DiffRequest {
        val factory = DiffContentFactory.getInstance()
        val beforeText = changedFile.beforeContent?.text.orEmpty()
        val afterText = changedFile.afterContent?.text.orEmpty()
        val beforeTitle = changedFile.beforeContent?.let { "${it.revisionTitle}  ${it.filePath}" }
            ?: "Missing  ${changedFile.filePath}"
        val afterTitle = changedFile.afterContent?.let { "${it.revisionTitle}  ${it.filePath}" }
            ?: "Missing  ${changedFile.filePath}"

        val beforePath = changedFile.beforeContent?.filePath ?: changedFile.previousFilePath ?: changedFile.filePath
        val afterPath = changedFile.afterContent?.filePath ?: changedFile.filePath
        val beforeFilePath = LocalFilePath(Path.of(repositoryRoot, beforePath).toString(), false)
        val afterFilePath = LocalFilePath(Path.of(repositoryRoot, afterPath).toString(), false)

        val beforeContent =
            if (beforeText.isEmpty()) factory.createEmpty() else factory.create(project, beforeText, beforeFilePath)
        val afterContent =
            if (afterText.isEmpty()) factory.createEmpty() else factory.create(project, afterText, afterFilePath)
        return SimpleDiffRequest(changedFile.filePath, beforeContent, afterContent, beforeTitle, afterTitle)
    }
}
