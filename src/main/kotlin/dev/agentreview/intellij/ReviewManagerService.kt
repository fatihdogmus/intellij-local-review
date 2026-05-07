package dev.agentreview.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.wm.ToolWindowManager
import dev.agentreview.intellij.diff.DiffContextExtractor
import dev.agentreview.intellij.export.AgentPromptBuilder
import dev.agentreview.intellij.model.AgentMetadata
import dev.agentreview.intellij.model.CommentSeverity
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewComment
import dev.agentreview.intellij.model.ReviewStatus
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import dev.agentreview.intellij.model.commitHashIfAny
import dev.agentreview.intellij.persistence.ReviewStateService
import dev.agentreview.intellij.util.nowIso
import dev.agentreview.intellij.vcs.ChangedFile
import dev.agentreview.intellij.vcs.CommitChangesProvider
import dev.agentreview.intellij.vcs.GitRepositoryResolver
import dev.agentreview.intellij.vcs.UncommittedChangesProvider
import java.util.UUID

@Service(Service.Level.PROJECT)
class ReviewManagerService(private val project: Project) {
    private val stateService = ReviewStateService.getInstance(project)
    private val diffContextExtractor = DiffContextExtractor()
    private val listeners = mutableSetOf<() -> Unit>()
    internal var uncommittedChangesLoader: () -> List<ChangedFile> = { UncommittedChangesProvider(project).getChangedFiles() }
    internal var repositoryRootResolver: () -> String = { GitRepositoryResolver(project).resolveRepositoryRoot() }
    internal var hasUncommittedChangesSupplier: () -> Boolean = {
        val changeListManager = ChangeListManager.getInstance(project)
        changeListManager.getAllChanges().isNotEmpty() || changeListManager.unversionedFilesPaths.isNotEmpty()
    }

    var currentReviewId: String? = null
        private set

    var currentFilePath: String? = null
        private set

    init {
        project.messageBus.connect().subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() {
                syncUncommittedReviewState()
            }
        })
        syncUncommittedReviewState(notify = false)
    }

    fun listReviews(status: ReviewStatus? = null): List<Review> = stateService.reviews()
        .asSequence()
        .filter { status == null || it.status == status }
        .sortedByDescending { it.updatedAt }
        .toList()

    fun getCurrentReview(): Review? = currentReviewId?.let { stateService.findReview(it) }

    fun findReview(reviewId: String): Review? = stateService.findReview(reviewId)

    fun selectReview(reviewId: String?) {
        if (currentReviewId == reviewId) return
        currentReviewId = reviewId
        if (reviewId == null) currentFilePath = null
        notifyChanged()
    }

    fun selectFile(filePath: String?) {
        if (currentFilePath == filePath) return
        currentFilePath = filePath
        notifyChanged()
    }

    fun createUncommittedReview(): Review? {
        syncUncommittedReviewState()
        findUncommittedReview()?.let {
            openReview(it.id)
            return it
        }
        if (currentUncommittedChanges().isEmpty()) return null

        val repositoryRoot = repositoryRootResolver()
        val review = Review(
            id = UUID.randomUUID().toString(),
            title = "Uncommitted changes",
            target = ReviewTarget(type = ReviewTargetType.UNCOMMITTED, baseRef = "HEAD", headRef = "WORKTREE"),
            repositoryRoot = repositoryRoot,
            createdAt = nowIso(),
            updatedAt = nowIso(),
        )
        stateService.addReview(review)
        openReview(review.id)
        return review
    }

    fun createCommitReview(commitHash: String): Review {
        val metadata = CommitChangesProvider(project).getCommitMetadata(commitHash)
        val review = Review(
            id = UUID.randomUUID().toString(),
            title = "${metadata.shortHash} ${metadata.subject}",
            target = ReviewTarget(
                type = ReviewTargetType.COMMIT,
                commitHash = metadata.hash,
                parentHash = metadata.firstParentHash,
                subject = metadata.subject,
            ),
            repositoryRoot = metadata.repositoryRoot,
            createdAt = nowIso(),
            updatedAt = nowIso(),
        )
        stateService.addReview(review)
        openReview(review.id)
        return review
    }

    fun deleteReview(reviewId: String) {
        stateService.removeReview(reviewId)
        if (currentReviewId == reviewId) {
            currentReviewId = null
            currentFilePath = null
        }
        notifyChanged()
    }

    fun loadChangedFiles(review: Review): List<ChangedFile> = when (review.target.type) {
        ReviewTargetType.UNCOMMITTED -> {
            val changedFiles = currentUncommittedChanges()
            if (changedFiles.isEmpty()) syncUncommittedReviewState()
            changedFiles
        }
        ReviewTargetType.COMMIT -> CommitChangesProvider(project).getChangedFiles(review.target.commitHash ?: error("Commit hash missing"))
        ReviewTargetType.COMMIT_RANGE -> emptyList()
    }

    fun addComment(
        reviewId: String,
        changedFile: ChangedFile,
        side: DiffSide,
        lineNumber: Int,
        body: String,
        severity: CommentSeverity = CommentSeverity.NOTE,
        endLineNumber: Int? = null,
    ) {
        val review = findReview(reviewId) ?: return
        val comment = ReviewComment(
            id = UUID.randomUUID().toString(),
            reviewId = reviewId,
            filePath = changedFile.filePath,
            anchor = diffContextExtractor.buildAnchor(changedFile, side, lineNumber, review.target.commitHashIfAny(), endLineNumber),
            body = body,
            severity = severity,
            status = CommentStatus.OPEN,
            createdAt = nowIso(),
            updatedAt = nowIso(),
        )
        review.comments.add(comment)
        touch(review)
    }

    fun updateComment(commentId: String, body: String, severity: CommentSeverity, status: CommentStatus) {
        val (review, comment) = findComment(commentId) ?: return
        comment.body = body
        comment.severity = severity
        comment.status = status
        comment.updatedAt = nowIso()
        touch(review)
    }

    fun deleteComment(commentId: String) {
        val (review, comment) = findComment(commentId) ?: return
        if (review.comments.remove(comment)) {
            touch(review)
        }
    }

    fun markCommentAddressed(commentId: String, message: String? = null, agentName: String = "user", runId: String? = null) {
        val (review, comment) = findComment(commentId) ?: return
        comment.status = CommentStatus.ADDRESSED
        comment.agentMetadata = AgentMetadata(
            addressedBy = agentName,
            addressedAt = nowIso(),
            message = message,
            runId = runId,
        )
        comment.updatedAt = nowIso()
        touch(review)
    }

    fun markCommentResolved(commentId: String) = setCommentStatus(commentId, CommentStatus.RESOLVED)

    fun reopenComment(commentId: String) = setCommentStatus(commentId, CommentStatus.OPEN)

    fun markCommentWontFix(commentId: String) = setCommentStatus(commentId, CommentStatus.WONT_FIX)

    fun commentsForCurrentSelection(): List<ReviewComment> {
        val review = getCurrentReview() ?: return emptyList()
        val selectedFile = currentFilePath
        return review.comments
            .asSequence()
            .filter { selectedFile == null || it.filePath == selectedFile }
            .sortedWith(compareBy({ it.filePath }, { it.anchor.newLine ?: it.anchor.oldLine ?: Int.MAX_VALUE }, { it.createdAt }))
            .toList()
    }

    fun commentsForFile(reviewId: String, filePath: String): List<ReviewComment> =
        findReview(reviewId)
            ?.comments
            ?.asSequence()
            ?.filter { it.filePath == filePath }
            ?.sortedWith(compareBy({ it.anchor.newLine ?: it.anchor.oldLine ?: Int.MAX_VALUE }, { it.createdAt }))
            ?.toList()
            .orEmpty()

    fun buildAgentPrompt(reviewId: String): String = AgentPromptBuilder().build(findReview(reviewId) ?: error("Review not found"))

    fun confirmDelete(review: Review): Boolean = Messages.showYesNoDialog(
        project,
        "Delete review '${review.title}'? Comments will be removed too.",
        "Delete Review",
        Messages.getQuestionIcon(),
    ) == Messages.YES

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun openReview(reviewId: String) {
        currentReviewId = reviewId
        currentFilePath = null
        ToolWindowManager.getInstance(project).invokeLater {
            ToolWindowManager.getInstance(project).getToolWindow("Review")?.let { toolWindow ->
                toolWindow.show {
                    expandReviewToolWindow(project, toolWindow)
                }
            }
            notifyChanged()
        }
    }

    private fun setCommentStatus(commentId: String, status: CommentStatus) {
        val (review, comment) = findComment(commentId) ?: return
        comment.status = status
        comment.updatedAt = nowIso()
        touch(review)
    }

    internal fun syncUncommittedReviewState(notify: Boolean = true): Boolean {
        val uncommittedReviews = stateService.reviews().filter { it.target.type == ReviewTargetType.UNCOMMITTED }
        if (uncommittedReviews.isEmpty()) return false

        val keepReview = if (hasUncommittedChanges()) uncommittedReviews.maxByOrNull { it.updatedAt } else null
        val removedIds = uncommittedReviews.filter { it.id != keepReview?.id }.map { it.id }
        if (removedIds.isEmpty() && (keepReview == null || currentReviewId != null && currentReviewId !in removedIds)) return false

        removedIds.forEach(stateService::removeReview)

        if (keepReview == null && currentReviewId in uncommittedReviews.map { it.id }) {
            currentReviewId = null
            currentFilePath = null
        } else if (keepReview != null && currentReviewId in removedIds) {
            currentReviewId = keepReview.id
            currentFilePath = null
        }

        if (notify) notifyChanged()
        return true
    }

    private fun findUncommittedReview(): Review? = stateService.reviews().firstOrNull { it.target.type == ReviewTargetType.UNCOMMITTED }

    private fun hasUncommittedChanges(): Boolean = hasUncommittedChangesSupplier()

    private fun currentUncommittedChanges(): List<ChangedFile> = runCatching { uncommittedChangesLoader() }.getOrElse { emptyList() }

    private fun findComment(commentId: String): Pair<Review, ReviewComment>? {
        stateService.reviews().forEach { review ->
            review.comments.firstOrNull { it.id == commentId }?.let { return review to it }
        }
        return null
    }

    private fun touch(review: Review) {
        review.updatedAt = nowIso()
        notifyChanged()
    }

    private fun notifyChanged() {
        val notifyListeners = {
            listeners.toList().forEach { it() }
        }
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            notifyListeners()
        } else {
            application.invokeLater {
                if (!project.isDisposed) {
                    notifyListeners()
                }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): ReviewManagerService = project.getService(ReviewManagerService::class.java)
    }
}
