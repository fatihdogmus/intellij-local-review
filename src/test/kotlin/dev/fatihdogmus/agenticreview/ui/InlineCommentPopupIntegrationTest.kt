package dev.fatihdogmus.agenticreview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.model.Review
import dev.fatihdogmus.agenticreview.model.ReviewTarget
import dev.fatihdogmus.agenticreview.model.ReviewTargetType
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.diff.ReviewDiffRequestData
import dev.fatihdogmus.agenticreview.testutil.findComponents
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

@TestApplication
class InlineCommentPopupIntegrationTest {
    private val project by projectFixture()

    @Test
    fun submittingInlineCommentReplacesFormWithVisibleCommentInlay() {
        val review = seededReview("inline-add")
        val changedFile = sampleChangedFile("src/Foo.kt")
        ReviewStateService.getInstance(project).addReview(review)

        ApplicationManager.getApplication().invokeAndWait {
            val editor = createEditor()
            try {
                showInlineCommentForm(project, editor, 0, ReviewDiffRequestData(review.id, changedFile, DiffSide.RIGHT))
                assertThat(editor.commentInlayComponents()).hasSize(1)

                val form = editor.commentInlayComponents().single()
                findComponents(form).filterIsInstance<JTextArea>().single().text = "Needs fix"
                findComponents(form).filterIsInstance<JButton>().single { it.text == "Comment" }.doClick()

                assertThat(ReviewManagerService.getInstance(project).commentsForFile(review.id, changedFile.filePath))
                    .singleElement()
                    .extracting("body")
                    .isEqualTo("Needs fix")
                assertThat(editor.commentInlayComponents()).hasSize(1)
                assertThat(findComponents(editor.commentInlayComponents().single()).filterIsInstance<JTextArea>().map { it.text })
                    .contains("Needs fix")
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }

    @Test
    fun resolveCommentAndDismissOnlyDismissesAfterStateChanges() {
        val review = seededReview("resolve-dismiss")
        val changedFile = sampleChangedFile("src/Foo.kt")
        ReviewStateService.getInstance(project).addReview(review)
        val comment = ReviewManagerService.getInstance(project).addComment(review.id, changedFile, DiffSide.RIGHT, 1, "todo")!!
        var dismissCount = 0

        assertThat(resolveCommentAndDismiss(project, comment.id) { dismissCount++ }).isTrue()
        assertThat(dismissCount).isEqualTo(1)
        assertThat(ReviewManagerService.getInstance(project).commentsForFile(review.id, changedFile.filePath)).isEmpty()

        assertThat(resolveCommentAndDismiss(project, "missing") { dismissCount++ }).isFalse()
        assertThat(dismissCount).isEqualTo(1)
    }

    @Test
    fun deleteCommentAndDismissOnlyDismissesAfterStateChanges() {
        val review = seededReview("delete-dismiss")
        val changedFile = sampleChangedFile("src/Foo.kt")
        ReviewStateService.getInstance(project).addReview(review)
        val comment = ReviewManagerService.getInstance(project).addComment(review.id, changedFile, DiffSide.RIGHT, 1, "todo")!!
        var dismissCount = 0

        assertThat(deleteCommentAndDismiss(project, comment.id) { dismissCount++ }).isTrue()
        assertThat(dismissCount).isEqualTo(1)
        assertThat(ReviewManagerService.getInstance(project).commentsForFile(review.id, changedFile.filePath)).isEmpty()

        assertThat(deleteCommentAndDismiss(project, "missing") { dismissCount++ }).isFalse()
        assertThat(dismissCount).isEqualTo(1)
    }

    private fun createEditor(): EditorEx = EditorFactory.getInstance()
        .createViewer(EditorFactory.getInstance().createDocument("one\ntwo\nthree\n"), project) as EditorEx

    private fun EditorEx.commentInlayComponents(): List<JPanel> = inlayModel
        .getBlockElementsInRange(0, document.textLength, ComponentInlayRenderer::class.java)
        .mapNotNull { it.renderer.component as? JPanel }

    private fun seededReview(suffix: String): Review = Review(
        id = "review-$suffix",
        title = "Review $suffix",
        target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "abc123"),
        repositoryRoot = project.basePath ?: "",
        createdAt = "2026-05-07T14:20:00+03:00",
        updatedAt = "2026-05-07T14:20:00+03:00",
    )

    private fun sampleChangedFile(path: String): ChangedFile = ChangedFile(
        filePath = path,
        status = ChangedFileStatus.MODIFIED,
        beforeContent = ReviewContent("one\ntwo\nthree\n", "HEAD", path),
        afterContent = ReviewContent("one\ntwo\nthree\n", "WORKTREE", path),
    )
}
