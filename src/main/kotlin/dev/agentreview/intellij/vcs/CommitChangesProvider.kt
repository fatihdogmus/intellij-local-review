package dev.agentreview.intellij.vcs

import com.intellij.openapi.project.Project

class CommitChangesProvider(private val project: Project) {
    fun getCommitMetadata(commitHash: String): CommitMetadata {
        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        val git = GitCommandFallback(repositoryRoot)
        val fullHash = git.run("rev-parse", commitHash).trim()
        val subject = git.run("show", "-s", "--format=%s", fullHash).trim()
        val parents = git.run("rev-list", "--parents", "-n", "1", fullHash).trim().split(' ')
        val firstParent = parents.getOrNull(1)
        return CommitMetadata(
            hash = fullHash,
            shortHash = fullHash.take(7),
            subject = subject,
            firstParentHash = firstParent,
            repositoryRoot = repositoryRoot,
        )
    }

    fun getChangedFiles(commitHash: String): List<ChangedFile> {
        val metadata = getCommitMetadata(commitHash)
        val git = GitCommandFallback(metadata.repositoryRoot)
        val parent = metadata.firstParentHash ?: EMPTY_TREE
        return git.run("diff", "--name-status", parent, metadata.hash)
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { parseDiffLine(git, parent, metadata.hash, it) }
            .sortedBy { it.filePath }
            .toList()
    }

    private fun parseDiffLine(git: GitCommandFallback, parent: String, commitHash: String, line: String): ChangedFile? {
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
                    beforeContent = loadRevisionContent(git, parent, beforePath),
                    afterContent = loadRevisionContent(git, commitHash, afterPath),
                    previousFilePath = beforePath,
                )
            }

            else -> {
                val path = parts.getOrNull(1) ?: return null
                ChangedFile(
                    filePath = path,
                    status = status,
                    beforeContent = if (status == ChangedFileStatus.ADDED) null else loadRevisionContent(git, parent, path),
                    afterContent = if (status == ChangedFileStatus.DELETED) null else loadRevisionContent(git, commitHash, path),
                )
            }
        }
    }

    private fun loadRevisionContent(git: GitCommandFallback, revision: String, relativePath: String): ReviewContent? {
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

    companion object {
        private const val EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
    }
}
