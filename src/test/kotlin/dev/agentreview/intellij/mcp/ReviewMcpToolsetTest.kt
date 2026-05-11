package dev.agentreview.intellij.mcp

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.mcp.CommentListResult
import dev.fatihdogmus.agenticreview.mcp.MutationResult
import dev.fatihdogmus.agenticreview.mcp.ReviewMcpToolset
import dev.fatihdogmus.agenticreview.mcp.ReviewResult
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotListResult
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotResult
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.model.CommentStatus
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.model.Review
import dev.fatihdogmus.agenticreview.model.ReviewTarget
import dev.fatihdogmus.agenticreview.model.ReviewTargetType
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
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
    suspend fun reviewMarkCommentResolvedStoresAgentMetadata() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("resolved")
        ReviewStateService.getInstance(project).addReview(review)

        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "avoid bang bang")
        val comment = manager.findReview(review.id)?.comments?.single() ?: error("comment missing")

        val result = json.decodeFromString<MutationResult>(
            ReviewMcpToolset().reviewMarkCommentResolved(
                commentId = comment.id,
                message = "Replaced with explicit null branch",
                agentName = "codex",
                runId = "run-42",
            ),
        )

        val updated = manager.findReview(review.id)?.comments?.single() ?: error("updated comment missing")
        assertThat(result.ok).isTrue()
        assertThat(result.newStatus).isEqualTo("RESOLVED")
        assertThat(updated.status).isEqualTo(CommentStatus.RESOLVED)
        assertThat(updated.agentMetadata?.addressedBy).isEqualTo("codex")
        assertThat(updated.agentMetadata?.message).isEqualTo("Replaced with explicit null branch")
        assertThat(updated.agentMetadata?.runId).isEqualTo("run-42")
    }

    @Test
    suspend fun reviewGetReviewReportsOpenAndResolvedCommentCounts() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededReview("counts")
        ReviewStateService.getInstance(project).addReview(review)

        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "open comment")
        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 3, "resolved comment")
        val resolved = manager.findReview(review.id)?.comments?.last() ?: error("comment missing")
        manager.markCommentResolved(resolved.id)

        val result = json.decodeFromString<ReviewResult>(
            ReviewMcpToolset().reviewGetReview(reviewId = review.id, includeComments = true, includeResolved = true),
        )

        assertThat(result.review.openCommentCount).isEqualTo(1)
        assertThat(result.review.resolvedCommentCount).isEqualTo(1)
        assertThat(result.review.comments).hasSize(2)
    }

    @Test
    suspend fun reviewTurnSnapshotBeginAndEndLifecycle() {
        val sessionId = "session-1"
        val stepId = "step-1"

        val beginResult = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotBegin(
                sessionId = sessionId,
                stepId = stepId,
                projectPath = project.basePath!!,
                agent = "primary",
                model = "claude-4",
            ),
        )

        assertThat(beginResult.ok).isTrue()
        assertThat(beginResult.turnId).isNotBlank()

        val endResult = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotEnd(
                sessionId = sessionId,
                stepId = stepId,
                status = "completed",
            ),
        )

        assertThat(endResult.ok).isTrue()
        assertThat(endResult.turnId).isEqualTo(beginResult.turnId)

        val listResult = json.decodeFromString<TurnSnapshotListResult>(
            ReviewMcpToolset().reviewListTurnSnapshots(),
        )

        assertThat(listResult.turns).hasSize(1)
        assertThat(listResult.turns.single().sessionId).isEqualTo(sessionId)
        assertThat(listResult.turns.single().status).isEqualTo("completed")
        assertThat(listResult.turns.single().agent).isEqualTo("primary")
    }

    @Test
    suspend fun reviewTurnSnapshotEndWithoutActiveTurnReturnsError() {
        val result = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotEnd(
                sessionId = "nonexistent",
                stepId = "step-1",
            ),
        )

        assertThat(result.ok).isFalse()
        assertThat(result.turnId).isBlank()
    }

    @Test
    suspend fun reviewTurnSnapshotBeginOverlapsExisting() {
        val session1 = "session-1"
        val session2 = "session-2"

        val begin1 = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotBegin(
                sessionId = session1,
                stepId = "step-1",
                projectPath = project.basePath!!,
            ),
        )
        assertThat(begin1.ok).isTrue()

        val begin2 = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotBegin(
                sessionId = session2,
                stepId = "step-2",
                projectPath = project.basePath!!,
            ),
        )
        assertThat(begin2.ok).isTrue()

        val listResult = json.decodeFromString<TurnSnapshotListResult>(
            ReviewMcpToolset().reviewListTurnSnapshots(),
        )

        assertThat(listResult.turns).hasSize(1)
        assertThat(listResult.turns.single().sessionId).isEqualTo(session1)
        assertThat(listResult.turns.single().status).isEqualTo("overlapped")
    }

    @Test
    suspend fun reviewTurnSnapshotBeginSucceedsWithoutOptionalFields() {
        val result = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotBegin(
                sessionId = "session-min",
                stepId = "step-min",
                projectPath = project.basePath!!,
            ),
        )

        assertThat(result.ok).isTrue()

        val listResult = json.decodeFromString<TurnSnapshotListResult>(
            ReviewMcpToolset().reviewListTurnSnapshots(),
        )

        assertThat(listResult.turns).hasSize(0)

        ReviewMcpToolset().reviewTurnSnapshotEnd(
            sessionId = "session-min",
            stepId = "step-min",
        )
    }

    @Test
    suspend fun reviewTurnSnapshotEndWithChangedPaths() {
        val sessionId = "session-paths"

        ReviewMcpToolset().reviewTurnSnapshotBegin(
            sessionId = sessionId,
            stepId = "step-paths",
            projectPath = project.basePath!!,
        )

        val changedPaths = """["src/main/Foo.kt", "src/test/Bar.kt"]"""
        val toolCalls = """[{"callId":"c1","tool":"edit","changedPaths":["src/main/Foo.kt"],"metadataJson":null}]"""

        val endResult = json.decodeFromString<TurnSnapshotResult>(
            ReviewMcpToolset().reviewTurnSnapshotEnd(
                sessionId = sessionId,
                stepId = "step-paths",
                status = "completed",
                changedPathsJson = changedPaths,
                toolCallsJson = toolCalls,
            ),
        )

        assertThat(endResult.ok).isTrue()

        val listResult = json.decodeFromString<TurnSnapshotListResult>(
            ReviewMcpToolset().reviewListTurnSnapshots(),
        )

        assertThat(listResult.turns).hasSize(1)
        assertThat(listResult.turns.single().changedFileCount).isEqualTo(2)
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
