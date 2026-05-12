package dev.agentreview.intellij

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.dvcs.repo.VcsRepositoryManager
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.CommitChangesProvider
import git4idea.repo.GitRepositoryManager
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
        val initial = head()

        runGit(repoRoot, "checkout", "-b", "feature/test")
        write(repoRoot.resolve("src/Foo.kt"), "two\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "feature work")
        val featureHead = head()

        configureGitMapping()

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

        configureGitMapping()

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

        configureGitMapping()

        val provider = CommitChangesProvider(project)
        assertThat(provider.canCreateCurrentBranchReview()).isFalse()
        assertThat(provider.getCurrentBranchReviewMetadataOrNull()).isNull()
    }

    @Test
    fun combinedCommitMetadataUsesEmptyTreeAndSingleCommitTitleForInitialCommit() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial commit")
        val initial = head()

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

        val files = CommitChangesProvider(project).getChangedFiles(head())

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
        val base = head()

        Files.delete(repoRoot.resolve("src/Foo.kt"))
        write(repoRoot.resolve("src/CopyClone.kt"), "copy\n")
        runGit(repoRoot, "add", "-A")
        runGit(repoRoot, "commit", "-m", "delete and copy")
        val head = this.head()

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

    private fun configureGitMapping() {
        runWriteAction {
            ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(
                listOf(VcsDirectoryMapping(repoRoot.toString(), "Git")),
            )
        }
        project.getService(VcsRepositoryManager::class.java).waitForAsyncTaskCompletion()
        GitRepositoryManager.getInstance(project).updateAllRepositories()
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun head(): String = runGit(repoRoot, "rev-parse", "HEAD").trim()

    private fun runGit(root: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }
}
