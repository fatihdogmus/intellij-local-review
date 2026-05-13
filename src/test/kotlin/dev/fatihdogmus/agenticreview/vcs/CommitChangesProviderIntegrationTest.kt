package dev.fatihdogmus.agenticreview.vcs

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class CommitChangesProviderIntegrationTest {
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
    fun getCommitMetadataReturnsResolvedHashSubjectAndParent() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial commit")
        val first = head()

        write(repoRoot.resolve("src/Foo.kt"), "one\ntwo\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "update foo")
        val second = head()

        val metadata = CommitChangesProvider(project).getCommitMetadata(second.take(7))

        assertThat(metadata.hash).isEqualTo(second)
        assertThat(metadata.shortHash).isEqualTo(second.take(7))
        assertThat(metadata.subject).isEqualTo("update foo")
        assertThat(metadata.firstParentHash).isEqualTo(first)
        assertThat(metadata.repositoryRoot).isEqualTo(repoRoot.toString())
    }

    @Test
    fun getChangedFilesCapturesModifiedAddedAndRenamedFiles() {
        write(repoRoot.resolve("src/Foo.kt"), "before\n")
        write(repoRoot.resolve("src/Bar.kt"), "bar\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")

        write(repoRoot.resolve("src/Foo.kt"), "after\n")
        write(repoRoot.resolve("src/NewFile.kt"), "new\n")
        runGit(repoRoot, "mv", "src/Bar.kt", "src/Baz.kt")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "complex change")

        val files = CommitChangesProvider(project).getChangedFiles(head())

        assertThat(files).hasSize(3)
        val modified = files.first { it.filePath == "src/Foo.kt" }
        val added = files.first { it.filePath == "src/NewFile.kt" }
        val renamed = files.first { it.filePath == "src/Baz.kt" }

        assertThat(modified.status).isEqualTo(ChangedFileStatus.MODIFIED)
        assertThat(modified.beforeContent?.text).isEqualTo("before\n")
        assertThat(modified.afterContent?.text).isEqualTo("after\n")

        assertThat(added.status).isEqualTo(ChangedFileStatus.ADDED)
        assertThat(added.beforeContent).isNull()
        assertThat(added.afterContent?.text).isEqualTo("new\n")

        assertThat(renamed.status).isEqualTo(ChangedFileStatus.RENAMED)
        assertThat(renamed.previousFilePath).isEqualTo("src/Bar.kt")
        assertThat(renamed.beforeContent?.text).isEqualTo("bar\n")
        assertThat(renamed.afterContent?.text).isEqualTo("bar\n")
    }

    @Test
    fun combinedCommitMetadataSortsByTimestampAndUsesOldestParent() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        commitWithTimestamp("initial", "2026-05-12T10:00:00+0000")
        val first = head()

        write(repoRoot.resolve("src/Foo.kt"), "two\n")
        runGit(repoRoot, "add", ".")
        commitWithTimestamp("second", "2026-05-12T10:10:00+0000")
        val second = head()

        write(repoRoot.resolve("src/Foo.kt"), "three\n")
        runGit(repoRoot, "add", ".")
        commitWithTimestamp("third", "2026-05-12T10:20:00+0000")
        val third = head()

        val metadata = CommitChangesProvider(project).getCombinedCommitMetadata(listOf(third, second))

        assertThat(metadata.repositoryRoot).isEqualTo(repoRoot.toString())
        assertThat(metadata.baseHash).isEqualTo(first)
        assertThat(metadata.headHash).isEqualTo(third)
        assertThat(metadata.title).isEqualTo("${second.take(7)}..${third.take(7)} 2 commits")
    }

    @Test
    fun getChangedFilesForRangeReturnsNetDiffAcrossRange() {
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        write(repoRoot.resolve("src/Bar.kt"), "bar\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")
        val base = head()

        write(repoRoot.resolve("src/Foo.kt"), "two\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "modify foo")

        runGit(repoRoot, "mv", "src/Bar.kt", "src/Baz.kt")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "rename bar")
        val head = head()

        val files = CommitChangesProvider(project).getChangedFilesForRange(base, head)

        assertThat(files.map { it.filePath }).containsExactlyInAnyOrder("src/Baz.kt", "src/Foo.kt")
        assertThat(files.first { it.filePath == "src/Baz.kt" }.status).isEqualTo(ChangedFileStatus.RENAMED)
        assertThat(files.first { it.filePath == "src/Foo.kt" }.afterContent?.text).isEqualTo("two\n")
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun commitWithTimestamp(message: String, timestamp: String) {
        runGit(repoRoot, "commit", "-m", message, env = mapOf("GIT_AUTHOR_DATE" to timestamp, "GIT_COMMITTER_DATE" to timestamp))
    }

    private fun head(): String = runGit(repoRoot, "rev-parse", "HEAD").trim()

    private fun runGit(root: Path, vararg args: String, env: Map<String, String> = emptyMap()): String {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }
}
