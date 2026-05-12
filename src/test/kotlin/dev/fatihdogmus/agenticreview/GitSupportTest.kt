package dev.fatihdogmus.agenticreview

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.dvcs.repo.VcsRepositoryManager
import dev.fatihdogmus.agenticreview.vcs.GitCommandFallback
import dev.fatihdogmus.agenticreview.vcs.GitRepositoryResolver
import git4idea.repo.GitRepositoryManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class GitSupportTest {
    private val project by projectFixture()

    @Test
    fun repositoryResolverFallsBackToProjectBasePathWithoutMappedRepository() {
        assertThat(GitRepositoryResolver(project).resolveRepositoryRoot()).isEqualTo(project.basePath)
    }

    @Test
    fun repositoryResolverUsesMappedGitRepositoryRoot() {
        val repoRoot = Path.of(project.basePath!!)
        runGit(repoRoot, "init")
        runWriteAction {
            ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(
                listOf(VcsDirectoryMapping(repoRoot.toString(), "Git")),
            )
        }
        project.getService(VcsRepositoryManager::class.java).waitForAsyncTaskCompletion()
        GitRepositoryManager.getInstance(project).updateAllRepositories()

        assertThat(GitRepositoryResolver(project).resolveRepositoryRoot()).isEqualTo(repoRoot.toString())
    }

    @Test
    fun gitCommandFallbackReturnsStdoutAndNullOnFailure() {
        val repoRoot = Path.of(project.basePath!!)
        runGit(repoRoot, "init")
        val git = GitCommandFallback(repoRoot.toString())

        assertThat(git.run("rev-parse", "--show-toplevel").trim()).isEqualTo(repoRoot.toString())
        assertThat(git.runOrNull("rev-parse", "missing-ref")).isNull()
        assertThatThrownBy { git.run("rev-parse", "missing-ref") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("missing-ref")
    }

    private fun runGit(root: Path, vararg args: String) {
        val result = ProcessBuilder(listOf("git", *args))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = result.inputStream.bufferedReader().readText()
        val exitCode = result.waitFor()
        check(exitCode == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }
}
