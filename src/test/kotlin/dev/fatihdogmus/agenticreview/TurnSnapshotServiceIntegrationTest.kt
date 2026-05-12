package dev.fatihdogmus.agenticreview

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@TestApplication
class TurnSnapshotServiceIntegrationTest {
    private val project by projectFixture()
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        TurnSnapshotService.getInstance(project).clearAll(notify = false)
        ReviewStateService.getInstance(project).setTurnSnapshotsJson("")
        ReviewStateService.getInstance(project).setTurnDiffsJson("")
    }

    @Test
    fun endTurnPersistsSnapshotState() {
        val service = TurnSnapshotService.getInstance(project)

        val turn = service.beginTurn(
            sessionId = "session-persist",
            stepId = "step-persist",
            projectPath = project.basePath!!,
            agent = "primary",
            model = "gpt-5.4",
        )

        service.endTurn(
            sessionId = "session-persist",
            stepId = "step-persist",
            status = "completed",
            changedPaths = emptyList(),
            toolCalls = emptyList(),
        )

        val snapshotsJson = ReviewStateService.getInstance(project).turnSnapshotsJson()
        val root = json.parseToJsonElement(snapshotsJson).jsonObject
        val completedTurns = root.getValue("completedTurns").jsonArray
        val persistedTurn = completedTurns.single().jsonObject

        assertThat(persistedTurn.getValue("id").jsonPrimitive.content).isEqualTo(turn.id)
        assertThat(persistedTurn.getValue("sessionId").jsonPrimitive.content).isEqualTo("session-persist")
        assertThat(persistedTurn.getValue("status").jsonPrimitive.content).isEqualTo("completed")
    }

    @Test
    fun endTurnBuildsDiffsFromAbsolutePathsInsideRepository() {
        val service = TurnSnapshotService.getInstance(project)
        val projectRoot = Path.of(project.basePath!!)
        val file = projectRoot.resolve("src/main/kotlin/sample/Foo.kt")

        Files.createDirectories(file.parent)
        runGit(projectRoot, "init")
        runGit(projectRoot, "config", "user.email", "test@example.com")
        runGit(projectRoot, "config", "user.name", "Test User")
        Files.writeString(file, "before\n")
        runGit(projectRoot, "add", ".")
        runGit(projectRoot, "commit", "-m", "initial")
        Files.writeString(file, "after\n")

        val turn = service.beginTurn(
            sessionId = "session-absolute",
            stepId = "step-absolute",
            projectPath = project.basePath!!,
            agent = null,
            model = null,
        )

        service.endTurn(
            sessionId = "session-absolute",
            stepId = "step-absolute",
            status = "completed",
            changedPaths = listOf(file.toString()),
            toolCalls = emptyList(),
        )

        val diffs = service.getTurnDiffs(turn.id)

        assertThat(diffs).hasSize(1)
        assertThat(diffs.single().filePath).isEqualTo("src/main/kotlin/sample/Foo.kt")
        assertThat(diffs.single().beforeContent?.text).isEqualTo("before\n")
        assertThat(diffs.single().afterContent?.text).isEqualTo("after\n")
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
