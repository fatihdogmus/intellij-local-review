package dev.agentreview.intellij.mcp

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.agentreview.intellij.ReviewManagerService
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import dev.agentreview.intellij.persistence.ReviewStateService
import dev.agentreview.intellij.vcs.ChangedFile
import dev.agentreview.intellij.vcs.ChangedFileStatus
import dev.agentreview.intellij.vcs.ReviewContent
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ReviewMcpToolsetTest {
    private val project by projectFixture()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    suspend fun reviewListUnresolvedCommentsUsesCurrentReviewByDefault() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("current-review")
        ReviewStateService.getInstance(project).addReview(review)
        manager.selectReview(review.id)

        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "fix null handling")
        val resolved = manager.findReview(review.id)?.comments?.single() ?: error("comment missing")
        manager.markCommentResolved(resolved.id)
        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 3, "rename variable")

        val result = json.decodeFromString<CommentListResult>(ReviewMcpToolset().reviewListUnresolvedComments())

        assertThat(result.comments).hasSize(1)
        assertThat(result.comments.single().body).isEqualTo("rename variable")
    }

    @Test
    suspend fun reviewGetReviewSupportsCommitSelector() {
        val review = seededReview(
            suffix = "commit-selector",
            target = ReviewTarget(
                type = ReviewTargetType.COMMIT,
                commitHash = "mcp987654321",
                parentHash = "0000000",
                subject = "Fix issue",
            ),
        )
        ReviewStateService.getInstance(project).addReview(review)

        val result = json.decodeFromString<ReviewResult>(
            ReviewMcpToolset().reviewGetReview(selector = "commit:mcp987", includeComments = false),
        )

        assertThat(result.review.id).isEqualTo(review.id)
        assertThat(result.review.target.type).isEqualTo(ReviewTargetType.COMMIT)
        assertThat(result.review.comments).isEmpty()
    }

    @Test
    suspend fun reviewMarkCommentAddressedStoresAgentMetadata() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("addressed")
        ReviewStateService.getInstance(project).addReview(review)

        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "avoid bang bang")
        val comment = manager.findReview(review.id)?.comments?.single() ?: error("comment missing")

        val result = json.decodeFromString<MutationResult>(
            ReviewMcpToolset().reviewMarkCommentAddressed(
                commentId = comment.id,
                message = "Replaced with explicit null branch",
                agentName = "codex",
                runId = "run-42",
            ),
        )

        val updated = manager.findReview(review.id)?.comments?.single() ?: error("updated comment missing")
        assertThat(result.ok).isTrue()
        assertThat(result.newStatus).isEqualTo("ADDRESSED")
        assertThat(updated.status).isEqualTo(CommentStatus.ADDRESSED)
        assertThat(updated.agentMetadata?.addressedBy).isEqualTo("codex")
        assertThat(updated.agentMetadata?.message).isEqualTo("Replaced with explicit null branch")
        assertThat(updated.agentMetadata?.runId).isEqualTo("run-42")
    }

    private fun seededReview(suffix: String, target: ReviewTarget = ReviewTarget(type = ReviewTargetType.UNCOMMITTED)): Review = Review(
        id = "review-mcp-$suffix",
        title = "MCP review",
        target = target,
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
