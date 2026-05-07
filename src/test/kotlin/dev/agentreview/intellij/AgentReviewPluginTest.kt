package dev.agentreview.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.agentreview.intellij.export.AgentPromptBuilder
import dev.agentreview.intellij.model.CommentAnchor
import dev.agentreview.intellij.model.CommentSeverity
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewComment
import dev.agentreview.intellij.model.ReviewStatus
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import dev.agentreview.intellij.persistence.ReviewStateService

class AgentReviewPluginTest : BasePlatformTestCase() {
    fun testStateServicePersistsReviewObjects() {
        val stateService = ReviewStateService.getInstance(project)
        val review = sampleReview()

        stateService.addReview(review)

        val stored = stateService.findReview(review.id)
        assertNotNull(stored)
        assertEquals("review-1", stored?.id)
        assertEquals(1, stored?.comments?.size)
        assertEquals(CommentStatus.OPEN, stored?.comments?.first()?.status)
        assertEquals(49, stored?.comments?.first()?.anchor?.endNewLine)
    }

    fun testPromptIncludesCommitHashAndComment() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertTrue(exported.contains("- Commit Hash: `abc123def456`"))
        assertTrue(exported.contains("Avoid !! here."))
        assertTrue(exported.contains("- Status: OPEN"))
    }

    fun testPromptIncludesReadableTopLevelSections() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertTrue(exported.contains("# Local Review"))
        assertTrue(exported.contains("## Instructions"))
        assertTrue(exported.contains("## Review"))
        assertTrue(exported.contains("## Open Comments"))
    }

    fun testPromptIncludesMultiLineAnchorDetails() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertTrue(exported.contains("- Lines: 47-49"))
        assertTrue(exported.contains("**Selected Text**"))
        assertTrue(exported.contains("repo.find(id)!!\nreturn user\n}"))
    }

    private fun sampleReview(): Review = Review(
        id = "review-1",
        title = "Review abc123",
        target = ReviewTarget(
            type = ReviewTargetType.COMMIT,
            commitHash = "abc123def456",
            parentHash = "def456abc123",
            subject = "Fix user lookup",
        ),
        repositoryRoot = "/tmp/repo",
        createdAt = "2026-05-07T14:20:00+03:00",
        updatedAt = "2026-05-07T14:31:00+03:00",
        status = ReviewStatus.OPEN,
        comments = mutableListOf(
            ReviewComment(
                id = "comment-1",
                reviewId = "review-1",
                filePath = "src/main/kotlin/Foo.kt",
                anchor = CommentAnchor(
                    side = DiffSide.RIGHT,
                    newLine = 47,
                    endNewLine = 49,
                    selectedText = "repo.find(id)!!\nreturn user\n}",
                    beforeContext = listOf("fun findUser(id: UserId): User {") ,
                    afterContext = listOf("}"),
                    commitHash = "abc123def456",
                ),
                body = "Avoid !! here.",
                severity = CommentSeverity.MUST_FIX,
                status = CommentStatus.OPEN,
                createdAt = "2026-05-07T14:20:00+03:00",
                updatedAt = "2026-05-07T14:20:00+03:00",
            ),
        ),
    )
}
