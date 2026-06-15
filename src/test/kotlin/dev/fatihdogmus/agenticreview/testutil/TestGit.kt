package dev.fatihdogmus.agenticreview.testutil

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import git4idea.repo.GitRepositoryManager
import java.nio.file.Files
import java.nio.file.Path

fun write(path: Path, content: String) {
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
}

fun runGit(root: Path, vararg args: String, env: Map<String, String> = emptyMap()): String {
    val process = ProcessBuilder(listOf("git", *args))
        .directory(root.toFile())
        .redirectErrorStream(true)
        .apply {
            if (env.isNotEmpty()) environment().putAll(env)
        }
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "git ${args.joinToString(" ")} failed: $output" }
    return output
}

fun initGitRepo(root: Path) {
    runGit(root, "init")
    runGit(root, "config", "user.email", "test@example.com")
    runGit(root, "config", "user.name", "Test User")
    runGit(root, "branch", "-M", "main")
}

fun gitHead(root: Path): String = runGit(root, "rev-parse", "HEAD").trim()

fun commitWithTimestamp(root: Path, message: String, timestamp: String) {
    runGit(
        root,
        "commit",
        "-m",
        message,
        env = mapOf("GIT_AUTHOR_DATE" to timestamp, "GIT_COMMITTER_DATE" to timestamp)
    )
}

fun configureGitMapping(project: Project, repoRoot: Path) {
    runWriteAction {
        ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(
            listOf(VcsDirectoryMapping(repoRoot.toString(), "Git")),
        )
    }
    project.getService(VcsRepositoryManager::class.java).waitForAsyncTaskCompletion()
    GitRepositoryManager.getInstance(project).updateAllRepositories()
}

fun initGitRepoWithCommit(root: Path): String {
    initGitRepo(root)
    write(root.resolve("src/Foo.kt"), "class Foo\n")
    runGit(root, "add", ".")
    runGit(root, "commit", "-m", "initial")
    return gitHead(root)
}
