package dev.fatihdogmus.agenticreview.vcs

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.testutil.configureGitMapping
import dev.fatihdogmus.agenticreview.testutil.gitHead
import dev.fatihdogmus.agenticreview.testutil.runGit
import dev.fatihdogmus.agenticreview.testutil.write
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class CommitChangesProviderAdditionalTest {
    private val project by projectFixture()
    private lateinit var repoRoot: Path

    @BeforeEach
    fun setUpRepo() {
        repoRoot = Path.of(project.basePath!!)
        runGit(repoRoot, "init")
        runGit(repoRoot, "config", "user.email", "test@example.com")
        runGit(repoRoot, "config", "user.name", "Test User")
        runGit(repoRoot, "branch", "-M", "main")
    }

    @Test
    fun currentBranchReviewMetadataUsesFeatureBranchAndMainBase() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")
        val initial = gitHead(repoRoot)

        runGit(repoRoot, "checkout", "-b", "feature/test")
        write(repoRoot.resolve("src/Foo.kt"), "two\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "feature work")
        val featureHead = gitHead(repoRoot)

        configureGitMapping(project, repoRoot)

        val provider = CommitChangesProvider(project)
        val metadata = provider.getCurrentBranchReviewMetadataOrNull()

        assertThat(provider.canCreateCurrentBranchReview()).isTrue()
        assertThat(metadata).isNotNull
        assertThat(metadata!!.repositoryRoot).isEqualTo(repoRoot.toString())
        assertThat(metadata.currentBranch).isEqualTo("feature/test")
        assertThat(metadata.baseBranch).isEqualTo("main")
        assertThat(metadata.mergeBase).isEqualTo(initial)
        assertThat(metadata.headHash).isEqualTo(featureHead)
        assertThat(metadata.title).isEqualTo("feature/test vs main")
    }

    @Test
    fun currentBranchReviewMetadataReturnsNullOnMainBranch() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")

        configureGitMapping(project, repoRoot)

        val provider = CommitChangesProvider(project)
        assertThat(provider.canCreateCurrentBranchReview()).isFalse()
        assertThat(provider.getCurrentBranchReviewMetadataOrNull()).isNull()
    }

    @Test
    fun currentBranchReviewMetadataReturnsNullWhenBaseBranchMissing() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")
        runGit(repoRoot, "branch", "-M", "trunk")
        runGit(repoRoot, "checkout", "-b", "feature/test")

        configureGitMapping(project, repoRoot)

        val provider = CommitChangesProvider(project)
        assertThat(provider.canCreateCurrentBranchReview()).isFalse()
        assertThat(provider.getCurrentBranchReviewMetadataOrNull()).isNull()
    }

    @Test
    fun combinedCommitMetadataUsesEmptyTreeAndSingleCommitTitleForInitialCommit() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial commit")
        val initial = gitHead(repoRoot)

        val metadata = CommitChangesProvider(project).getCombinedCommitMetadata(listOf(initial, initial))

        assertThat(metadata.baseHash).isEqualTo("4b825dc642cb6eb9a060e54bf8d69288fbee4904")
        assertThat(metadata.headHash).isEqualTo(initial)
        assertThat(metadata.title).isEqualTo("${initial.take(7)} initial commit")
    }

    @Test
    fun combinedCommitMetadataRejectsEmptyCommitList() {
        assertThatThrownBy { CommitChangesProvider(project).getCombinedCommitMetadata(emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("commitHashes must not be empty")
    }

    @Test
    fun getChangedFilesTreatsInitialCommitAsAddedAgainstEmptyTree() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")

        val files = CommitChangesProvider(project).getChangedFiles(gitHead(repoRoot))

        val file = files.single()
        assertThat(file.filePath).isEqualTo("src/Foo.kt")
        assertThat(file.status).isEqualTo(ChangedFileStatus.ADDED)
        assertThat(file.beforeContent).isNull()
        assertThat(file.afterContent?.text).isEqualTo("one\n")
    }

    @Test
    fun getChangedFilesForRangeHandlesDeletedAndNewCopyFile() {
        write(repoRoot.resolve("src/Foo.kt"), "before\n")
        write(repoRoot.resolve("src/Copy.kt"), "copy\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")
        val base = gitHead(repoRoot)

        Files.delete(repoRoot.resolve("src/Foo.kt"))
        write(repoRoot.resolve("src/CopyClone.kt"), "copy\n")
        runGit(repoRoot, "add", "-A")
        runGit(repoRoot, "commit", "-m", "delete and copy")
        val head = gitHead(repoRoot)

        val files = CommitChangesProvider(project).getChangedFilesForRange(base, head)
        val deleted = files.first { it.filePath == "src/Foo.kt" }
        val copied = files.first { it.filePath == "src/CopyClone.kt" }

        assertThat(deleted.status).isEqualTo(ChangedFileStatus.DELETED)
        assertThat(deleted.beforeContent?.text).isEqualTo("before\n")
        assertThat(deleted.afterContent).isNull()

        assertThat(copied.status).isEqualTo(ChangedFileStatus.ADDED)
        assertThat(copied.previousFilePath).isNull()
        assertThat(copied.afterContent?.text).isEqualTo("copy\n")
    }


}
