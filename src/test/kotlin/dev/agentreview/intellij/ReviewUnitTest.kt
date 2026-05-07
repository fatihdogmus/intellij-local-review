package dev.agentreview.intellij

import dev.agentreview.intellij.diff.DiffContextExtractor
import dev.agentreview.intellij.export.AgentPromptBuilder
import dev.agentreview.intellij.model.CommentAnchor
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.model.ReviewComment
import dev.agentreview.intellij.model.ReviewStatus
import dev.agentreview.intellij.model.ReviewTarget
import dev.agentreview.intellij.model.ReviewTargetType
import dev.agentreview.intellij.vcs.ChangedFile
import dev.agentreview.intellij.vcs.ChangedFileStatus
import dev.agentreview.intellij.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewUnitTest {
    @Test
    fun promptBuilderIncludesDefaultsAndCommentPayload() {
        val exported = AgentPromptBuilder().build(sampleReview())

        assertThat(exported)
            .contains("### Comment 1")
            .contains("- Lines: 47")
            .contains("- Comment:")
    }

    @Test
    fun promptBuilderIncludesEscapedTextAndMetadata() {
        val review = sampleReview().copy(comments = mutableListOf(sampleReview().comments.first().copy(body = "Use <safe> & clear")))

        val exported = AgentPromptBuilder().build(review)

        assertThat(exported)
            .contains("Use <safe> & clear")
            .contains("- Repository Root: `/tmp/repo`")
    }

    @Test
    fun diffContextExtractorBuildsMultiLineAnchorWithoutSelectedLinesInAfterContext() {
        val anchor = DiffContextExtractor().buildAnchor(
            changedFile = ChangedFile(
                filePath = "src/Foo.kt",
                status = ChangedFileStatus.MODIFIED,
                beforeContent = null,
                afterContent = ReviewContent(
                    text = "one\ntwo\nthree\nfour\nfive",
                    revisionTitle = "after",
                    filePath = "src/Foo.kt",
                ),
            ),
            side = DiffSide.RIGHT,
            lineNumber = 2,
            commitHash = "abc123",
            endLineNumber = 4,
        )

        assertThat(anchor.newLine).isEqualTo(2)
        assertThat(anchor.endNewLine).isEqualTo(4)
        assertThat(anchor.selectedText).isEqualTo("two\nthree\nfour")
        assertThat(anchor.beforeContext).containsExactly("one")
        assertThat(anchor.afterContext).containsExactly("five")
    }

    private fun sampleReview(): Review = Review(
        id = "review-unit-1",
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
                reviewId = "review-unit-1",
                filePath = "src/main/kotlin/Foo.kt",
                anchor = CommentAnchor(
                    side = DiffSide.RIGHT,
                    newLine = 47,
                    selectedText = "repo.find(id)!!",
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
