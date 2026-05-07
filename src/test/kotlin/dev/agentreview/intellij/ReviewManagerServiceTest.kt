package dev.agentreview.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.agentreview.intellij.model.CommentSeverity
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import dev.agentreview.intellij.persistence.ReviewStateService
import dev.agentreview.intellij.vcs.ChangedFile
import dev.agentreview.intellij.vcs.ChangedFileStatus
import dev.agentreview.intellij.vcs.ReviewContent

class ReviewManagerServiceTest : BasePlatformTestCase() {
    fun testAddCommentUsesDefaultSeverityAndMultiLineAnchor() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("default-severity")
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
        assertNotNull(comment)
        assertEquals(CommentSeverity.NOTE, comment?.severity)
        assertEquals(2, comment?.anchor?.newLine)
        assertEquals(3, comment?.anchor?.endNewLine)
        assertEquals("two\nthree", comment?.anchor?.selectedText)
        assertEquals(listOf("one"), comment?.anchor?.beforeContext)
        assertEquals(listOf("four"), comment?.anchor?.afterContext)
    }

    fun testCommentsForFileAndDeleteComment() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("delete-comment")
        ReviewStateService.getInstance(project).addReview(review)

        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 1, "foo")
        manager.addComment(review.id, sampleChangedFile("src/Bar.kt"), DiffSide.RIGHT, 2, "bar")

        val fooComments = manager.commentsForFile(review.id, "src/Foo.kt")
        assertEquals(1, fooComments.size)
        assertEquals("foo", fooComments.single().body)

        manager.deleteComment(fooComments.single().id)

        assertEmpty(manager.commentsForFile(review.id, "src/Foo.kt"))
        assertEquals(1, manager.findReview(review.id)?.comments?.size)
        assertEquals("bar", manager.findReview(review.id)?.comments?.single()?.body)
    }

    fun testCreateUncommittedReviewReusesExistingOne() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { true }
        manager.uncommittedChangesLoader = { listOf(sampleChangedFile("src/Foo.kt")) }
        manager.repositoryRootResolver = { "/tmp/repo" }

        val first = manager.createUncommittedReview()
        val second = manager.createUncommittedReview()

        assertNotNull(first)
        assertEquals(first?.id, second?.id)
        assertEquals(1, manager.listReviews().count { it.target.type == ReviewTargetType.UNCOMMITTED })
    }

    fun testSyncUncommittedReviewStateRemovesReviewWhenChangesGone() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        val review = seededReview("stale-uncommitted")
        ReviewStateService.getInstance(project).addReview(review)
        manager.selectReview(review.id)

        val changed = manager.syncUncommittedReviewState()

        assertTrue(changed)
        assertNull(manager.findReview(review.id))
        assertNull(manager.getCurrentReview())
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
}
