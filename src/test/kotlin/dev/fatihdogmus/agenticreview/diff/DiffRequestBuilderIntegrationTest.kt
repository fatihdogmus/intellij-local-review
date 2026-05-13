package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

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

        val request = DiffRequestBuilder(project).buildForFile("review-1", changedFile, "/tmp/repo")
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

        val request = DiffRequestBuilder(project).buildForFile("review-2", changedFile, "/tmp/repo")
        val data = request.getUserData(REVIEW_DIFF_REQUEST_DATA_KEY)

        assertThat(request.title).isEqualTo("src/NewFile.kt")
        assertThat(data).isNotNull
        assertThat(data!!.commentSide).isEqualTo(DiffSide.RIGHT)
    }

    @Test
    fun diffContentHasCorrectFileTypeForExistingFile(@TempDir tempDir: Path) {
        val repoDir = tempDir.resolve("repo")
        val srcDir = repoDir.resolve("src")
        Files.createDirectories(srcDir)
        val sourceFile = Files.writeString(srcDir.resolve("Foo.java"), "class Foo {}")
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourceFile)

        val changedFile = ChangedFile(
            filePath = "src/Foo.java",
            status = ChangedFileStatus.MODIFIED,
            beforeContent = ReviewContent("before\n", "HEAD", "src/Foo.java"),
            afterContent = ReviewContent("after\n", "WORKTREE", "src/Foo.java"),
        )

        val request = DiffRequestBuilder(project).buildForFile("review-1", changedFile, repoDir.toString()) as ContentDiffRequest
        val afterContent = request.contents[1] as? DocumentContent

        assertThat(afterContent).isNotNull
        assertThat(afterContent!!.contentType).isNotNull
    }

    @Test
    fun diffContentTypeIsCorrectForNonExistentFile() {
        val changedFile = ChangedFile(
            filePath = "src/Foo.java",
            status = ChangedFileStatus.MODIFIED,
            beforeContent = ReviewContent("before\n", "HEAD", "src/Foo.java"),
            afterContent = ReviewContent("after\n", "WORKTREE", "src/Foo.java"),
        )

        val request = DiffRequestBuilder(project).buildForFile("review-1", changedFile, "/tmp/nonexistent-repo") as ContentDiffRequest
        val afterContent = request.contents[1] as? DocumentContent

        assertThat(afterContent).isNotNull
        assertThat(afterContent!!.contentType).isNotNull
    }
}
