package dev.fatihdogmus.agenticreview

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.diff.DiffRequestBuilder
import dev.fatihdogmus.agenticreview.diff.REVIEW_DIFF_REQUEST_DATA_KEY
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class DiffRequestBuilderIntegrationTest {
    private val project by projectFixture()

    @Test
    fun buildForFileStoresTitlesAndRequestMetadata() {
        val changedFile = ChangedFile(
            filePath = "src/Foo.kt",
            status = ChangedFileStatus.MODIFIED,
            beforeContent = ReviewContent("before\n", "HEAD", "src/Foo.kt"),
            afterContent = ReviewContent("after\n", "WORKTREE", "src/Foo.kt"),
        )

        val request = DiffRequestBuilder(project).buildForFile("review-1", changedFile)
        val data = request.getUserData(REVIEW_DIFF_REQUEST_DATA_KEY)

        assertThat(request.title).isEqualTo("src/Foo.kt")
        assertThat(data).isNotNull
        assertThat(data!!.reviewId).isEqualTo("review-1")
        assertThat(data.changedFile.filePath).isEqualTo("src/Foo.kt")
    }

    @Test
    fun buildForFileUsesMissingTitleWhenOneSideAbsent() {
        val changedFile = ChangedFile(
            filePath = "src/NewFile.kt",
            status = ChangedFileStatus.ADDED,
            beforeContent = null,
            afterContent = ReviewContent("after\n", "WORKTREE", "src/NewFile.kt"),
        )

        val request = DiffRequestBuilder(project).buildForFile("review-2", changedFile)
        val data = request.getUserData(REVIEW_DIFF_REQUEST_DATA_KEY)

        assertThat(request.title).isEqualTo("src/NewFile.kt")
        assertThat(data).isNotNull
        assertThat(data!!.commentSide).isEqualTo(DiffSide.RIGHT)
    }
}
