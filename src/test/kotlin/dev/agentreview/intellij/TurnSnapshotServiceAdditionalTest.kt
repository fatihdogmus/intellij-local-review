package dev.agentreview.intellij

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.snapshot.TurnToolCall
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class TurnSnapshotServiceAdditionalTest {
    private val project by projectFixture()

    @BeforeEach
    fun setUp() {
        TurnSnapshotService.getInstance(project).clearAll(notify = false)
        ReviewStateService.getInstance(project).setTurnSnapshotsJson("")
        ReviewStateService.getInstance(project).setTurnDiffsJson("")
    }

    @Test
    fun beginTurnOverlapsExistingTurnAndMarksItCompleted() {
        val service = TurnSnapshotService.getInstance(project)

        val first = service.beginTurn("session", "step-1", project.basePath!!, null, null)
        val second = service.beginTurn("session", "step-2", project.basePath!!, "agent", "model")

        assertThat(service.getActiveTurn()!!.id).isEqualTo(second.id)
        val overlapped = service.getCompletedTurns().single()
        assertThat(overlapped.id).isEqualTo(first.id)
        assertThat(overlapped.status).isEqualTo("overlapped")
        assertThat(overlapped.endedAt).isNotNull()
    }

    @Test
    fun endTurnReturnsNullForMismatchedTurnAndTracksStateTransitions() {
        val service = TurnSnapshotService.getInstance(project)
        service.beginTurn("session", "step-1", project.basePath!!, null, null)

        assertThat(service.hasStoredTurns()).isTrue()
        assertThat(service.endTurn("session", "wrong-step", "completed", emptyList(), emptyList())).isNull()
        assertThat(service.getTurnDiffs("missing-turn")).isEmpty()
        assertThat(service.findTurn("missing-turn")).isNull()
    }

    @Test
    fun endTurnBuildsAddedDeletedAndSkipsUnchangedAndBlankPaths() {
        val service = TurnSnapshotService.getInstance(project)
        val projectRoot = Path.of(project.basePath!!)
        val deleted = projectRoot.resolve("src/main/kotlin/sample/Deleted.kt")
        val unchanged = projectRoot.resolve("src/main/kotlin/sample/Unchanged.kt")
        val added = projectRoot.resolve("src/main/kotlin/sample/Added.kt")

        Files.createDirectories(deleted.parent)
        runGit(projectRoot, "init")
        runGit(projectRoot, "config", "user.email", "test@example.com")
        runGit(projectRoot, "config", "user.name", "Test User")
        Files.writeString(deleted, "before\n")
        Files.writeString(unchanged, "same\n")
        runGit(projectRoot, "add", ".")
        runGit(projectRoot, "commit", "-m", "initial")
        Files.delete(deleted)
        Files.writeString(added, "new\n")

        val turn = service.beginTurn("session", "step", project.basePath!!, null, null)
        val toolCalls = listOf(TurnToolCall(callId = "1", tool = "edit_file"))
        service.endTurn(
            sessionId = "session",
            stepId = "step",
            status = "completed",
            changedPaths = listOf(deleted.toString(), unchanged.toString(), added.toString(), "   "),
            toolCalls = toolCalls,
        )

        val diffs = service.getTurnDiffs(turn.id)
        assertThat(diffs.map { it.filePath }).containsExactlyInAnyOrder(
            "src/main/kotlin/sample/Added.kt",
            "src/main/kotlin/sample/Deleted.kt",
        )
        assertThat(diffs.first { it.filePath.endsWith("Added.kt") }.status.name).isEqualTo("ADDED")
        assertThat(diffs.first { it.filePath.endsWith("Deleted.kt") }.status.name).isEqualTo("DELETED")
        assertThat(service.findTurn(turn.id)!!.toolCalls).hasSize(1)
        assertThat(service.findTurn(turn.id)!!.status).isEqualTo("completed")
    }

    @Test
    fun clearAllCanSkipNotificationsAndLoadStateIgnoresMalformedJson() {
        val service = TurnSnapshotService.getInstance(project)
        var notifications = 0
        val listener = { notifications += 1 }
        service.addListener(listener)

        service.beginTurn("session", "step", project.basePath!!, null, null)
        val notificationsBeforeClear = notifications
        service.clearAll(notify = false)
        assertThat(notifications).isEqualTo(notificationsBeforeClear)

        ReviewStateService.getInstance(project).setTurnSnapshotsJson("{")
        ReviewStateService.getInstance(project).setTurnDiffsJson("{")
        invokeLoadState(service)

        assertThat(service.getActiveTurn()).isNull()
        assertThat(service.getCompletedTurns()).isEmpty()
        assertThat(service.hasStoredTurns()).isFalse()

        service.removeListener(listener)
    }

    private fun invokeLoadState(service: TurnSnapshotService) {
        val method = TurnSnapshotService::class.java.getDeclaredMethod("loadState")
        method.isAccessible = true
        method.invoke(service)
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
