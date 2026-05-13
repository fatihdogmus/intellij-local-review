package dev.fatihdogmus.agenticreview.ui

import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@Tag("integrationTest")
class InlineCommentUiTest {

    @Test
    fun `plugin toolwindow opens`() {
        val projectDir = createTempDirectory("agentic-review-ui-test")
        try {
            initGitRepo(projectDir)

            val pathToPlugin = System.getProperty("path.to.build.plugin")
                ?: throw IllegalStateException("path.to.build.plugin not set — run via ./gradlew integrationTest")

            Starter.newContext(
                testName = "pluginToolwindowOpens",
                TestCase(
                    IdeProductProvider.IC,
                    projectInfo = LocalProjectInfo(projectDir),
                ),
            ).apply {
                PluginConfigurator(this).installPluginFromDir(Path.of(pathToPlugin))
            }.runIdeWithDriver().useDriverAndCloseIde {
                waitForIndicators(2.minutes)
            }
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    private fun initGitRepo(root: Path) {
        Files.createDirectories(root.resolve("src"))
        root.resolve("src/Foo.kt").writeText("class Foo {}")
        runGit(root, "init")
        runGit(root, "config", "user.email", "test@example.com")
        runGit(root, "config", "user.name", "Test User")
        runGit(root, "branch", "-M", "main")
        runGit(root, "add", ".")
        runGit(root, "commit", "-m", "initial")
    }

    private fun runGit(root: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }
}
