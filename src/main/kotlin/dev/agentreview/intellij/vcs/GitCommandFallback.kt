package dev.agentreview.intellij.vcs

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import java.nio.charset.StandardCharsets

class GitCommandFallback(private val repositoryRoot: String) {
    fun run(vararg args: String): String {
        val result = execute(*args)
        if (result.exitCode != 0) {
            error(result.stderr.ifBlank { "git ${args.joinToString(" ")} failed" })
        }
        return result.stdout
    }

    fun runOrNull(vararg args: String): String? {
        val result = execute(*args)
        return if (result.exitCode == 0) result.stdout else null
    }

    private fun execute(vararg args: String): GitCommandResult {
        val commandLine = GeneralCommandLine("git", *args)
            .withWorkDirectory(repositoryRoot)
            .withCharset(StandardCharsets.UTF_8)
        val output = CapturingProcessHandler(commandLine).runProcess(15_000)
        return GitCommandResult(output.exitCode, output.stdout, output.stderr)
    }
}

private data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
