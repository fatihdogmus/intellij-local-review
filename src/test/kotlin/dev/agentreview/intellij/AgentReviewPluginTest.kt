package dev.agentreview.intellij

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.localreview.export.AgentPromptBuilder
import dev.fatihdogmus.localreview.model.CommentAnchor
import dev.fatihdogmus.localreview.model.CommentStatus
import dev.fatihdogmus.localreview.model.DiffSide
import dev.fatihdogmus.localreview.model.Review
import dev.fatihdogmus.localreview.model.ReviewComment
import dev.fatihdogmus.localreview.model.ReviewStatus
import dev.fatihdogmus.localreview.model.ReviewTarget
import dev.fatihdogmus.localreview.model.ReviewTargetType
import dev.fatihdogmus.localreview.persistence.ReviewStateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentReviewPluginTest {
    private val project by projectFixture()

    @Test
    fun stateServicePersistsReviewObjects() {
        val stateService = ReviewStateService.getInstance(project)
        val review = sampleReview()

        stateService.addReview(review)

        val stored = stateService.findReview(review.id)
        assertThat(stored).isNotNull
        assertThat(stored!!.id).isEqualTo("review-1")
        assertThat(stored.comments).hasSize(1)
        assertThat(stored.comments.first().status).isEqualTo(CommentStatus.OPEN)
        assertThat(stored.comments.first().anchor.endNewLine).isEqualTo(49)
    }

    @Test
    fun promptIncludesCommitHashAndComment() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertThat(exported)
            .contains("- Commit Hash: `abc123def456`")
            .contains("Avoid !! here.")
            .contains("- Comment:")
    }

    @Test
    fun promptIncludesReadableTopLevelSections() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertThat(exported)
            .contains("# Local Review")
            .contains("## Instructions")
            .contains("## Review")
            .contains("## Open Comments")
    }

    @Test
    fun promptIncludesMultiLineAnchorDetails() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertThat(exported)
            .contains("- Lines: 47-49")
            .doesNotContain("**Selected Text**")
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
                    beforeContext = listOf("fun findUser(id: UserId): User {"),
                    afterContext = listOf("}"),
                    commitHash = "abc123def456",
                ),
                body = "Avoid !! here.",
                status = CommentStatus.OPEN,
                createdAt = "2026-05-07T14:20:00+03:00",
                updatedAt = "2026-05-07T14:20:00+03:00",
            ),
        ),
    )
}
