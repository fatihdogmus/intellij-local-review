package dev.agentreview.intellij

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import dev.agentreview.intellij.persistence.ReviewStateService
import dev.agentreview.intellij.vcs.BranchReviewMetadata
import dev.agentreview.intellij.vcs.ChangedFile
import dev.agentreview.intellij.vcs.ChangedFileStatus
import dev.agentreview.intellij.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

@TestApplication
class ReviewManagerServiceTest {
    private val project by projectFixture()

    @Test
    fun addCommentBuildsMultiLineAnchor() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("multi-line-anchor")
        ReviewStateService.getInstance(project).addReview(review)

        manager.addComment(
            review.id,
            sampleChangedFile("src/Foo.kt"),
            DiffSide.RIGHT,
            2,
            "Need cleanup",
            endLineNumber = 3,
        )

        val comment = manager.findReview(review.id)?.comments?.single()
        assertThat(comment).isNotNull
        assertThat(comment!!.body).isEqualTo("Need cleanup")
        assertThat(comment.anchor.newLine).isEqualTo(2)
        assertThat(comment.anchor.endNewLine).isEqualTo(3)
        assertThat(comment.anchor.selectedText).isEqualTo("two\nthree")
        assertThat(comment.anchor.beforeContext).containsExactly("one")
        assertThat(comment.anchor.afterContext).containsExactly("four")
    }

    @Test
    fun commentsForFileAndDeleteComment() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("delete-comment")
        ReviewStateService.getInstance(project).addReview(review)

        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 1, "foo")
        manager.addComment(review.id, sampleChangedFile("src/Bar.kt"), DiffSide.RIGHT, 2, "bar")

        val fooComments = manager.commentsForFile(review.id, "src/Foo.kt")
        assertThat(fooComments).singleElement().extracting("body").isEqualTo("foo")

        manager.deleteComment(fooComments.single().id)

        assertThat(manager.commentsForFile(review.id, "src/Foo.kt")).isEmpty()
        assertThat(manager.findReview(review.id)?.comments).hasSize(1)
        assertThat(manager.findReview(review.id)?.comments?.single()?.body).isEqualTo("bar")
    }

    @Test
    fun commentsForFileReturnsOnlyOpenComments() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("open-only-comments")
        review.comments += dev.agentreview.intellij.model.ReviewComment(
            id = "open-comment",
            reviewId = review.id,
            filePath = "src/Foo.kt",
            anchor = dev.agentreview.intellij.model.CommentAnchor(newLine = 1),
            body = "open",
            status = CommentStatus.OPEN,
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )
        review.comments += dev.agentreview.intellij.model.ReviewComment(
            id = "resolved-comment",
            reviewId = review.id,
            filePath = "src/Foo.kt",
            anchor = dev.agentreview.intellij.model.CommentAnchor(newLine = 2),
            body = "resolved",
            status = CommentStatus.RESOLVED,
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )
        review.comments += dev.agentreview.intellij.model.ReviewComment(
            id = "addressed-comment",
            reviewId = review.id,
            filePath = "src/Foo.kt",
            anchor = dev.agentreview.intellij.model.CommentAnchor(newLine = 3),
            body = "addressed",
            status = CommentStatus.ADDRESSED,
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )
        ReviewStateService.getInstance(project).addReview(review)

        val comments = manager.commentsForFile(review.id, "src/Foo.kt")

        assertThat(comments).singleElement().extracting("body").isEqualTo("open")
    }

    @Test
    fun renameReviewUpdatesTitle() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededCommitReview("rename-review")
        ReviewStateService.getInstance(project).addReview(review)

        manager.renameReview(review.id, "  Better name  ")

        assertThat(manager.findReview(review.id)?.title).isEqualTo("Better name")
    }

    @Test
    fun prepareSaveReviewUsesKebabCaseNameAndReviewId() {
        val manager = ReviewManagerService.getInstance(project)
        val tempDir = Files.createTempDirectory("local-review-save")
        val review = seededCommitReview("save-plan")
        review.repositoryRoot = tempDir.toString()
        ReviewStateService.getInstance(project).addReview(review)

        val plan = manager.prepareSaveReview(review.id, "My Review Name")

        assertThat(plan).isNotNull
        assertThat(plan!!.title).isEqualTo("My Review Name")
        assertThat(plan.filePath.fileName.toString()).isEqualTo("my-review-name-${review.id}.json")
        assertThat(manager.findReview(review.id)?.title).isEqualTo("My Review Name")
    }

    @Test
    fun renameReviewDoesNotRenameUncommittedReview() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("rename-uncommitted")
        ReviewStateService.getInstance(project).addReview(review)

        manager.renameReview(review.id, "New name")

        assertThat(manager.findReview(review.id)?.title).isEqualTo("Service review")
    }

    @Test
    fun renameReviewIgnoresBlankAndUnchangedTitles() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededCommitReview("rename-noop")
        ReviewStateService.getInstance(project).addReview(review)

        manager.renameReview(review.id, "   ")
        manager.renameReview(review.id, "Commit review")

        assertThat(manager.findReview(review.id)?.title).isEqualTo("Commit review")
    }

    @Test
    fun createUncommittedReviewReusesExistingOne() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { true }
        manager.uncommittedChangesLoader = { listOf(sampleChangedFile("src/Foo.kt")) }
        manager.repositoryRootResolver = { "/tmp/repo" }
        manager.currentHeadHashSupplier = { "head-1" }

        val first = manager.createUncommittedReview()
        val second = manager.createUncommittedReview()

        assertThat(first).isNotNull
        assertThat(second?.id).isEqualTo(first?.id)
        assertThat(manager.listReviews().count { it.target.type == ReviewTargetType.UNCOMMITTED }).isEqualTo(1)
        assertThat(first?.target?.commitHash).isEqualTo("head-1")
    }

    @Test
    fun syncUncommittedReviewStateKeepsReviewWhenChangesGone() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        manager.repositoryRootResolver = { "/tmp/repo" }
        manager.currentHeadHashSupplier = { "head-1" }
        val review = manager.createUncommittedReview()!!
        manager.selectReview(review.id)

        val changed = manager.syncUncommittedReviewState()

        assertThat(changed).isFalse()
        assertThat(manager.findReview(review.id)).isNotNull
        assertThat(manager.getCurrentReview()?.id).isEqualTo(review.id)
    }

    @Test
    fun syncUncommittedReviewStateClearsCommentsWhenChangesGone() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        manager.repositoryRootResolver = { "/tmp/repo" }
        manager.currentHeadHashSupplier = { "head-1" }
        val review = manager.createUncommittedReview()!!
        ReviewStateService.getInstance(project).findReview(review.id)!!.comments += dev.agentreview.intellij.model.ReviewComment(
            id = "comment-1",
            reviewId = review.id,
            filePath = "src/Foo.kt",
            anchor = dev.agentreview.intellij.model.CommentAnchor(newLine = 1),
            body = "stale",
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )

        val changed = manager.syncUncommittedReviewState()

        assertThat(changed).isTrue()
        assertThat(manager.findReview(review.id)?.comments).isEmpty()
    }

    @Test
    fun syncUncommittedReviewStateClearsCommentsWhenHeadChanges() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { true }
        manager.uncommittedChangesLoader = { listOf(sampleChangedFile("src/Foo.kt")) }
        manager.repositoryRootResolver = { "/tmp/repo" }
        manager.currentHeadHashSupplier = { "head-1" }
        val review = manager.createUncommittedReview()!!
        ReviewStateService.getInstance(project).findReview(review.id)!!.comments += dev.agentreview.intellij.model.ReviewComment(
            id = "comment-1",
            reviewId = review.id,
            filePath = "src/Foo.kt",
            anchor = dev.agentreview.intellij.model.CommentAnchor(newLine = 1),
            body = "stale after commit",
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )
        manager.currentHeadHashSupplier = { "head-2" }

        val changed = manager.syncUncommittedReviewState()

        assertThat(changed).isTrue()
        assertThat(manager.findReview(review.id)?.comments).isEmpty()
        assertThat(manager.findReview(review.id)?.target?.commitHash).isEqualTo("head-2")
    }

    @Test
    fun initCreatesPersistentUncommittedReview() {
        val manager = ReviewManagerService.getInstance(project)

        val uncommittedReviews = manager.listReviews().filter { it.target.type == ReviewTargetType.UNCOMMITTED }

        assertThat(uncommittedReviews).hasSize(1)
        assertThat(manager.getCurrentReview()?.id).isEqualTo(uncommittedReviews.single().id)
    }

    @Test
    fun canCreateBranchReviewReflectsMetadataAvailability() {
        val manager = ReviewManagerService.getInstance(project)
        manager.canCreateBranchReviewSupplier = { false }

        assertThat(manager.canCreateBranchReview()).isFalse()

        manager.canCreateBranchReviewSupplier = { true }
        manager.branchReviewMetadataProvider = { sampleBranchReviewMetadata() }

        assertThat(manager.canCreateBranchReview()).isTrue()
    }

    @Test
    fun createBranchReviewCreatesCommitRangeReview() {
        val manager = ReviewManagerService.getInstance(project)
        manager.branchReviewMetadataProvider = { sampleBranchReviewMetadata() }

        val review = manager.createBranchReview()

        assertThat(review.target.type).isEqualTo(ReviewTargetType.COMMIT_RANGE)
        assertThat(review.target.baseRef).isEqualTo("merge-base-123")
        assertThat(review.target.headRef).isEqualTo("head-456")
        assertThat(review.target.subject).isEqualTo("feature/test vs main")
        assertThat(review.title).isEqualTo("feature/test vs main")
    }

    private fun seededReview(suffix: String): Review = Review(
        id = "review-service-$suffix",
        title = "Service review",
        target = ReviewTarget(type = ReviewTargetType.UNCOMMITTED),
        repositoryRoot = "/tmp/repo",
        createdAt = "2026-05-07T14:20:00+03:00",
        updatedAt = "2026-05-07T14:20:00+03:00",
    )

    private fun seededCommitReview(suffix: String): Review = Review(
        id = "review-commit-$suffix",
        title = "Commit review",
        target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "abc123"),
        repositoryRoot = "/tmp/repo",
        createdAt = "2026-05-07T14:20:00+03:00",
        updatedAt = "2026-05-07T14:20:00+03:00",
    )

    private fun sampleChangedFile(path: String): ChangedFile = ChangedFile(
        filePath = path,
        status = ChangedFileStatus.MODIFIED,
        beforeContent = ReviewContent(
            text = "zero\none\nold-three\nfour",
            revisionTitle = "before",
            filePath = path,
        ),
        afterContent = ReviewContent(
            text = "one\ntwo\nthree\nfour",
            revisionTitle = "after",
            filePath = path,
        ),
    )

    private fun sampleBranchReviewMetadata(): BranchReviewMetadata = BranchReviewMetadata(
        repositoryRoot = "/tmp/repo",
        currentBranch = "feature/test",
        baseBranch = "main",
        mergeBase = "merge-base-123",
        headHash = "head-456",
        title = "feature/test vs main",
    )
}
