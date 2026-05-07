package dev.agentreview.intellij

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
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
    fun createUncommittedReviewReusesExistingOne() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { true }
        manager.uncommittedChangesLoader = { listOf(sampleChangedFile("src/Foo.kt")) }
        manager.repositoryRootResolver = { "/tmp/repo" }

        val first = manager.createUncommittedReview()
        val second = manager.createUncommittedReview()

        assertThat(first).isNotNull
        assertThat(second?.id).isEqualTo(first?.id)
        assertThat(manager.listReviews().count { it.target.type == ReviewTargetType.UNCOMMITTED }).isEqualTo(1)
    }

    @Test
    fun syncUncommittedReviewStateKeepsReviewWhenChangesGone() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        manager.repositoryRootResolver = { "/tmp/repo" }
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
