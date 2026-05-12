package dev.fatihdogmus.agenticreview.vcs

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

class CommitChangesProvider(private val project: Project) {
    fun canCreateCurrentBranchReview(): Boolean = currentBranchReviewBaseOrNull() != null

    fun getCurrentBranchReviewMetadataOrNull(): BranchReviewMetadata? {
        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        val (currentBranch, baseBranch) = currentBranchReviewBaseOrNull() ?: return null

        val git = GitCommandFallback(repositoryRoot)
        val mergeBase = git.run("merge-base", baseBranch, "HEAD").trim()
        val headHash = git.run("rev-parse", "HEAD").trim()
        return BranchReviewMetadata(
            repositoryRoot = repositoryRoot,
            currentBranch = currentBranch,
            baseBranch = baseBranch,
            mergeBase = mergeBase,
            headHash = headHash,
            title = "$currentBranch vs $baseBranch",
        )
    }

    private fun currentBranchReviewBaseOrNull(): Pair<String, String>? {
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
        val repositoryRoot = repository?.root?.path ?: GitRepositoryResolver(project).resolveRepositoryRoot()
        val git = GitCommandFallback(repositoryRoot)
        val currentBranch = repository?.currentBranchName
            ?: git.runOrNull("rev-parse", "--abbrev-ref", "HEAD")?.trim()?.takeUnless { it.isBlank() || it == "HEAD" }
            ?: return null
        if (currentBranch == "main" || currentBranch == "master") return null
        val branches = repository?.branches?.localBranches
            ?.map { it.name }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: git.run("for-each-ref", "--format=%(refname:short)", "refs/heads")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        val baseBranch = when {
            "main" in branches -> "main"
            "master" in branches -> "master"
            else -> return null
        }
        return currentBranch to baseBranch
    }

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
        return getChangedFilesBetween(
            repositoryRoot = metadata.repositoryRoot,
            baseRevision = metadata.firstParentHash ?: EMPTY_TREE,
            headRevision = metadata.hash,
        )
    }

    fun getCombinedCommitMetadata(commitHashes: List<String>): CombinedCommitMetadata {
        require(commitHashes.isNotEmpty()) { "commitHashes must not be empty" }

        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        val git = GitCommandFallback(repositoryRoot)
        val orderedHashes = commitHashes
            .distinct()
            .map { hash ->
                val fullHash = git.run("rev-parse", hash).trim()
                val timestamp = git.run("show", "-s", "--format=%ct", fullHash).trim().toLong()
                val subject = git.run("show", "-s", "--format=%s", fullHash).trim()
                CommitPoint(fullHash, timestamp, subject)
            }
            .sortedBy { it.timestamp }

        val oldest = orderedHashes.first()
        val newest = orderedHashes.last()
        val parentLine = git.run("rev-list", "--parents", "-n", "1", oldest.hash).trim().split(' ')
        val baseHash = parentLine.getOrNull(1) ?: EMPTY_TREE

        return CombinedCommitMetadata(
            repositoryRoot = repositoryRoot,
            baseHash = baseHash,
            headHash = newest.hash,
            title = if (orderedHashes.size == 1) {
                "${newest.hash.take(7)} ${newest.subject}"
            } else {
                "${oldest.hash.take(7)}..${newest.hash.take(7)} ${orderedHashes.size} commits"
            },
        )
    }

    fun getChangedFilesForRange(baseRevision: String, headRevision: String): List<ChangedFile> {
        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        return getChangedFilesBetween(repositoryRoot, baseRevision, headRevision)
    }

    private fun getChangedFilesBetween(repositoryRoot: String, baseRevision: String, headRevision: String): List<ChangedFile> {
        val git = GitCommandFallback(repositoryRoot)
        return git.run("diff", "--name-status", baseRevision, headRevision)
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { parseDiffLine(git, baseRevision, headRevision, it) }
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

data class CombinedCommitMetadata(
    val repositoryRoot: String,
    val baseHash: String,
    val headHash: String,
    val title: String,
)

data class BranchReviewMetadata(
    val repositoryRoot: String,
    val currentBranch: String,
    val baseBranch: String,
    val mergeBase: String,
    val headHash: String,
    val title: String,
)

private data class CommitPoint(
    val hash: String,
    val timestamp: Long,
    val subject: String,
)
