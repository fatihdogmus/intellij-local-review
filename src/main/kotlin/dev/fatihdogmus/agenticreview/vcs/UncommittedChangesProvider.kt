package dev.fatihdogmus.agenticreview.vcs

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

class UncommittedChangesProvider(private val project: Project) {
    fun getChangedFiles(): List<ChangedFile> {
        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        val git = GitCommandFallback(repositoryRoot)
        val tracked = git.run("diff", "--name-status", "HEAD")
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { parseTrackedLine(repositoryRoot, git, it) }
            .toMutableList()

        val trackedPaths = tracked.map { it.filePath }.toMutableSet()
        val untracked = git.runOrNull("ls-files", "--others", "--exclude-standard")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .filter { it !in trackedPaths }
            .map { relativePath ->
                ChangedFile(
                    filePath = relativePath,
                    status = ChangedFileStatus.ADDED,
                    beforeContent = null,
                    afterContent = loadWorkingTreeContent(repositoryRoot, relativePath, "WORKTREE"),
                )
            }

        tracked.addAll(untracked)
        return tracked.sortedBy { it.filePath }
    }

    private fun parseTrackedLine(repositoryRoot: String, git: GitCommandFallback, line: String): ChangedFile? {
        val parts = line.split('\t')
        if (parts.isEmpty()) return null
        val statusToken = parts.first()
        val status = mapStatus(statusToken)
        return when {
            statusToken.startsWith("R") || statusToken.startsWith("C") -> {
                val beforePath = parts.getOrNull(1) ?: return null
                val afterPath = parts.getOrNull(2) ?: return null
                ChangedFile(
                    filePath = afterPath,
                    status = status,
                    beforeContent = loadGitContent(git, "HEAD", beforePath),
                    afterContent = loadWorkingTreeContent(repositoryRoot, afterPath, "WORKTREE"),
                    previousFilePath = beforePath,
                )
            }

            else -> {
                val path = parts.getOrNull(1) ?: return null
                ChangedFile(
                    filePath = path,
                    status = status,
                    beforeContent = if (status == ChangedFileStatus.ADDED) null else loadGitContent(git, "HEAD", path),
                    afterContent = if (status == ChangedFileStatus.DELETED) null else loadWorkingTreeContent(repositoryRoot, path, "WORKTREE"),
                )
            }
        }
    }

    private fun loadWorkingTreeContent(repositoryRoot: String, relativePath: String, title: String): ReviewContent? {
        val path = Path.of(repositoryRoot, relativePath)
        if (!Files.exists(path)) return null
        return ReviewContent(Files.readString(path), title, relativePath)
    }

    private fun loadGitContent(git: GitCommandFallback, revision: String, relativePath: String): ReviewContent? {
        val text = git.runOrNull("show", "$revision:$relativePath") ?: return null
        return ReviewContent(text, revision, relativePath)
    }

    private fun mapStatus(token: String): ChangedFileStatus = when {
        token.startsWith("A") -> ChangedFileStatus.ADDED
        token.startsWith("M") -> ChangedFileStatus.MODIFIED
        token.startsWith("D") -> ChangedFileStatus.DELETED
        token.startsWith("R") -> ChangedFileStatus.RENAMED
        token.startsWith("C") -> ChangedFileStatus.COPIED
        else -> ChangedFileStatus.UNKNOWN
    }
}
