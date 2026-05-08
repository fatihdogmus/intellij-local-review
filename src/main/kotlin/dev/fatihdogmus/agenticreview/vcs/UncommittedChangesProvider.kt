package dev.fatihdogmus.agenticreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import java.nio.file.Files
import java.nio.file.Path

class UncommittedChangesProvider(private val project: Project) {
    fun getChangedFiles(): List<ChangedFile> {
        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        val changeListManager = ChangeListManager.getInstance(project)
        val tracked = changeListManager
            .defaultChangeList
            .changes
            .mapNotNull { change -> change.toChangedFile(repositoryRoot) }

        val trackedPaths = tracked.map { it.filePath }.toSet()
        val unversioned = changeListManager.unversionedFilesPaths
            .asSequence()
            .map { path -> path.path.toRelativePath(repositoryRoot) }
            .filter { it !in trackedPaths }
            .mapNotNull { relativePath ->
                val contentPath = Path.of(repositoryRoot, relativePath)
                if (!Files.exists(contentPath)) return@mapNotNull null
                ChangedFile(
                    filePath = relativePath,
                    status = ChangedFileStatus.ADDED,
                    beforeContent = null,
                    afterContent = ReviewContent(Files.readString(contentPath), "WORKTREE", relativePath),
                )
            }

        return (tracked + unversioned).sortedBy { it.filePath }
    }

    private fun Change.toChangedFile(repositoryRoot: String): ChangedFile? {
        val beforePath = beforeRevision?.file?.path?.toRelativePath(repositoryRoot)
        val afterPath = afterRevision?.file?.path?.toRelativePath(repositoryRoot)
        val resolvedPath = afterPath ?: beforePath ?: return null

        return ChangedFile(
            filePath = resolvedPath,
            status = mapStatus(type, beforePath, afterPath),
            beforeContent = beforeRevision?.toReviewContent(beforePath),
            afterContent = afterRevision?.toReviewContent(afterPath),
            previousFilePath = beforePath?.takeIf { it != resolvedPath },
        )
    }

    private fun com.intellij.openapi.vcs.changes.ContentRevision.toReviewContent(relativePath: String?): ReviewContent? {
        val path = relativePath ?: file.path.substringAfterLast('/').substringAfterLast('\\')
        val content = try {
            content
        } catch (_: VcsException) {
            null
        } ?: return null
        return ReviewContent(content, revisionNumber.asString(), path)
    }

    private fun String.toRelativePath(repositoryRoot: String): String = runCatching {
        Path.of(repositoryRoot).relativize(Path.of(this)).toString().replace('\\', '/')
    }.getOrDefault(replace('\\', '/'))

    private fun mapStatus(type: Change.Type, beforePath: String?, afterPath: String?): ChangedFileStatus = when (type) {
        Change.Type.NEW -> ChangedFileStatus.ADDED
        Change.Type.MODIFICATION -> if (beforePath != null && afterPath != null && beforePath != afterPath) ChangedFileStatus.RENAMED else ChangedFileStatus.MODIFIED
        Change.Type.DELETED -> ChangedFileStatus.DELETED
        Change.Type.MOVED -> ChangedFileStatus.RENAMED
    }
}
