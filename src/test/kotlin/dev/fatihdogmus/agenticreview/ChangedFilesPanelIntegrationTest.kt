package dev.fatihdogmus.agenticreview

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.ui.ChangedFilesPanel
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import dev.fatihdogmus.agenticreview.vcs.seenKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

@TestApplication
class ChangedFilesPanelIntegrationTest {
    private val project by projectFixture()

    @BeforeEach
    fun resetTurns() {
        TurnSnapshotService.getInstance(project).clearAll(notify = false)
    }

    @Test
    fun setReviewFilesSelectsFirstFileAndTracksCurrentFiles() {
        val panel = ChangedFilesPanel()
        val files = listOf(sampleChangedFile("src/Foo.kt"), sampleChangedFile("src/Bar.kt"))

        panel.setReviewFiles(files, selectedFilePath = null, seenFileKeys = emptySet())

        assertThat(panel.currentFiles()).containsExactlyElementsOf(files)
        assertThat(panel.selectedFile()?.filePath).isEqualTo("src/Foo.kt")
    }

    @Test
    fun rendererMarksUnseenFilesWithAsteriskAndBoldTitle() {
        val panel = ChangedFilesPanel()
        val unseen = sampleChangedFile("src/Foo.kt")
        val seen = sampleChangedFile("src/Bar.kt")
        panel.setReviewFiles(listOf(unseen, seen), selectedFilePath = null, seenFileKeys = setOf(seen.seenKey()))

        val list = panelList(panel)
        val renderer = list.cellRenderer

        val unseenComponent = renderer.getListCellRendererComponent(list, unseen, 0, false, false) as JComponent
        val unseenLabel = findLabel(unseenComponent, "* Foo.kt")
        assertThat(unseenLabel).isNotNull
        assertThat(unseenLabel!!.font.isBold).isTrue()

        val seenComponent = renderer.getListCellRendererComponent(list, seen, 1, false, false) as JComponent
        val seenLabel = findLabel(seenComponent, "Bar.kt")
        assertThat(seenLabel).isNotNull
        assertThat(seenLabel!!.font.isBold).isFalse()
    }

    @Test
    fun rendererShowsRenameSubtitleStatusAndLineStats() {
        val panel = ChangedFilesPanel()
        val list = panelList(panel)
        val renamed = ChangedFile(
            filePath = "src/new/Foo.kt",
            status = ChangedFileStatus.RENAMED,
            beforeContent = ReviewContent("one\ntwo", "before", "src/old/Foo.kt"),
            afterContent = ReviewContent("one\nthree\nfour", "after", "src/new/Foo.kt"),
            previousFilePath = "src/old/Foo.kt",
        )
        panel.setReviewFiles(listOf(renamed), selectedFilePath = null, seenFileKeys = emptySet())

        val component = list.cellRenderer.getListCellRendererComponent(list, renamed, 0, false, false) as JComponent

        assertThat(findLabel(component, "* Foo.kt")).isNotNull
        assertThat(findLabel(component, "src/old/Foo.kt -> src/new/Foo.kt")).isNotNull
        assertThat(findLabel(component, "RENAMED")).isNotNull
        assertThat(labels(component).any { it.text.contains("+2") && it.text.contains("-1") }).isTrue()
    }

    @Test
    fun turnDropdownIsHiddenWhenTurnsDisabledAndVisibleWhenEnabled() {
        val panel = ChangedFilesPanel()
        val turnService = TurnSnapshotService.getInstance(project)
        val repoRoot = Path.of(project.basePath!!)
        val file = repoRoot.resolve("src/Foo.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "after\n")

        turnService.beginTurn("session-ui", "step-ui", project.basePath!!, null, null)
        turnService.endTurn("session-ui", "step-ui", "completed", listOf(file.toString()), emptyList())

        panel.setTurnsEnabled(false)
        panel.refreshTurns(turnService)
        assertThat(turnCombo(panel).isVisible).isFalse()

        panel.setTurnsEnabled(true)
        panel.refreshTurns(turnService)
        assertThat(turnCombo(panel).isVisible).isTrue()
        assertThat(turnCombo(panel).itemCount).isEqualTo(2)
    }

    @Test
    fun selectingTurnSwitchesToTurnChangedFilesMode() {
        val panel = ChangedFilesPanel()
        val turnService = TurnSnapshotService.getInstance(project)
        val repoRoot = Path.of(project.basePath!!)
        val file = repoRoot.resolve("src/Foo.kt")

        runGit(repoRoot, "init")
        runGit(repoRoot, "config", "user.email", "test@example.com")
        runGit(repoRoot, "config", "user.name", "Test User")
        Files.createDirectories(file.parent)
        Files.writeString(file, "before\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "initial")
        Files.writeString(file, "after\n")

        panel.setTurnsEnabled(true)
        turnService.beginTurn("session-mode", "step-mode", project.basePath!!, null, null)
        turnService.endTurn("session-mode", "step-mode", "completed", listOf(file.toString()), emptyList())
        panel.refreshTurns(turnService)

        turnCombo(panel).selectedIndex = 1

        assertThat(panel.currentFiles()).hasSize(1)
        assertThat(panel.currentFiles().single().filePath).isEqualTo("src/Foo.kt")
        assertThat(titleLabel(panel).text).isEqualTo("Turn Changed Files")
    }

    @Suppress("UNCHECKED_CAST")
    private fun panelList(panel: ChangedFilesPanel): JList<ChangedFile> =
        findComponents(panel.component).filterIsInstance<JList<*>>().single() as JList<ChangedFile>

    private fun turnCombo(panel: ChangedFilesPanel): JComboBox<*> =
        findComponents(panel.component)
            .filterIsInstance<JComboBox<*>>()
            .single { combo -> combo.itemCount > 0 && combo.getItemAt(0).toString() == "Review Changes" }

    private fun titleLabel(panel: ChangedFilesPanel): JLabel =
        findComponents(panel.component)
            .filterIsInstance<JLabel>()
            .first { it.text == "Changed Files" || it.text == "Turn Changed Files" }

    private fun findLabel(component: Component, text: String): JLabel? {
        if (component is JLabel && component.text == text) return component
        if (component is JComponent) {
            component.components.forEach { child ->
                findLabel(child, text)?.let { return it }
            }
        }
        return null
    }

    private fun sampleChangedFile(path: String): ChangedFile = ChangedFile(
        filePath = path,
        status = ChangedFileStatus.MODIFIED,
        beforeContent = ReviewContent("before\n", "before", path),
        afterContent = ReviewContent("after\n", "after", path),
    )

    private fun runGit(root: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "git ${args.joinToString(" ")} failed: $output" }
    }

    private fun labels(component: Component): List<JLabel> = findComponents(component).filterIsInstance<JLabel>()

    private fun findComponents(root: Component): List<Component> = buildList {
        add(root)
        if (root is Container) {
            root.components.forEach { child -> addAll(findComponents(child)) }
        }
    }
}
