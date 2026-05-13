package dev.fatihdogmus.agenticreview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AddCommentDialogTest {
    private val project by projectFixture()

    @Test
    fun dialogHasCorrectTitle() {
        ApplicationManager.getApplication().invokeAndWait {
            val dialog = AddCommentDialog(project, sampleFile())
            assertThat(dialog.title).isEqualTo("Add Review Comment")
        }
    }

    @Test
    fun accessorsReturnDefaultsAfterConstruction() {
        ApplicationManager.getApplication().invokeAndWait {
            val dialog = AddCommentDialog(project, sampleFile())
            assertThat(dialog.side()).isEqualTo(DiffSide.LEFT)
            assertThat(dialog.lineNumber()).isEqualTo(1)
            assertThat(dialog.commentBody()).isEmpty()
        }
    }

    @Test
    fun lineNumberDefaultIsOneForEmptyContent() {
        ApplicationManager.getApplication().invokeAndWait {
            val file = ChangedFile(
                filePath = "src/Empty.kt",
                status = ChangedFileStatus.ADDED,
                beforeContent = null,
                afterContent = null,
            )
            val dialog = AddCommentDialog(project, file)
            assertThat(dialog.lineNumber()).isEqualTo(1)
        }
    }

    private fun sampleFile() = ChangedFile(
        filePath = "src/Foo.kt",
        status = ChangedFileStatus.MODIFIED,
        beforeContent = ReviewContent("before\n", "HEAD", "src/Foo.kt"),
        afterContent = ReviewContent("after\n", "WORKTREE", "src/Foo.kt"),
    )
}
