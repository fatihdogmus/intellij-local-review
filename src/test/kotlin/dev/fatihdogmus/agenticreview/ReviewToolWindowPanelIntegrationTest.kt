package dev.fatihdogmus.agenticreview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.model.CommentAnchor
import dev.fatihdogmus.agenticreview.model.CommentStatus
import dev.fatihdogmus.agenticreview.model.Review
import dev.fatihdogmus.agenticreview.model.ReviewComment
import dev.fatihdogmus.agenticreview.model.ReviewTarget
import dev.fatihdogmus.agenticreview.model.ReviewTargetType
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.ui.ReviewToolWindowPanel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList

@TestApplication
class ReviewToolWindowPanelIntegrationTest {
    private val project by projectFixture()

    @BeforeEach
    fun setUp() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        manager.repositoryRootResolver = { project.basePath!! }
        manager.currentHeadHashSupplier = { "head-1" }
        TurnSnapshotService.getInstance(project).clearAll(notify = false)
    }

    @Test
    fun turnDropdownIsShownForUncommittedReviewAndHiddenForCommitReview() {
        val manager = ReviewManagerService.getInstance(project)
        manager.openDefaultReview()

        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            try {
                assertThat(turnCombo(panel).isVisible).isTrue()

                val commitHash = initGitRepoWithCommit()

                val commitReview = Review(
                    id = "review-commit-ui",
                    title = "Commit review",
                    target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = commitHash),
                    repositoryRoot = project.basePath!!,
                    createdAt = "2026-05-07T14:20:00+03:00",
                    updatedAt = "2026-05-07T14:20:00+03:00",
                )
                ReviewStateService.getInstance(project).addReview(commitReview)
                manager.selectReview(commitReview.id)

                assertThat(turnCombo(panel).isVisible).isFalse()
            } finally {
                Disposer.dispose(panel)
            }
        }
    }

    @Test
    fun completedTurnAppearsInTurnSelector() {
        val manager = ReviewManagerService.getInstance(project)
        manager.openDefaultReview()

        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            try {
                val turnService = TurnSnapshotService.getInstance(project)
                turnService.beginTurn("session-1", "step-1", project.basePath!!, "agent", "model")
                turnService.endTurn("session-1", "step-1", "completed", emptyList(), emptyList())

                val combo = turnCombo(panel)
                assertThat(combo.itemCount).isEqualTo(2)
                assertThat(combo.getItemAt(1).toString()).contains("agent")
            } finally {
                Disposer.dispose(panel)
            }
        }
    }

    @Test
    fun reviewSelectorRendererShowsOpenAndResolvedCounts() {
        val review = Review(
            id = "review-renderer-ui",
            title = "Renderer review",
            target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "abc123"),
            repositoryRoot = project.basePath!!,
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
            comments = mutableListOf(
                ReviewComment(
                    id = "c1",
                    reviewId = "review-renderer-ui",
                    filePath = "src/Foo.kt",
                    anchor = CommentAnchor(newLine = 1),
                    body = "open",
                    status = CommentStatus.OPEN,
                    createdAt = "2026-05-07T14:20:00+03:00",
                    updatedAt = "2026-05-07T14:20:00+03:00",
                ),
                ReviewComment(
                    id = "c2",
                    reviewId = "review-renderer-ui",
                    filePath = "src/Foo.kt",
                    anchor = CommentAnchor(newLine = 2),
                    body = "resolved",
                    status = CommentStatus.RESOLVED,
                    createdAt = "2026-05-07T14:20:00+03:00",
                    updatedAt = "2026-05-07T14:20:00+03:00",
                ),
            ),
        )
        ReviewStateService.getInstance(project).addReview(review)

        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            try {
                val reviewSelector = reviewSelector(panel)
                val label = reviewSelector.renderer.getListCellRendererComponent(JList(), review, 0, false, false) as JLabel

                assertThat(label.text).isEqualTo("Renderer review · 1 Open 1 Resolved")
            } finally {
                Disposer.dispose(panel)
            }
        }
    }

    @Test
    fun focusTargetReturnsChangedFilesComponent() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            try {
                assertThat(panel.focusTarget).isNotNull
            } finally {
                Disposer.dispose(panel)
            }
        }
    }

    private fun turnCombo(panel: ReviewToolWindowPanel): JComboBox<*> =
        findComponents(panel)
            .filterIsInstance<JComboBox<*>>()
            .single { combo -> combo.itemCount > 0 && combo.getItemAt(0).toString() == "Review Changes" }

    @Suppress("UNCHECKED_CAST")
    private fun reviewSelector(panel: ReviewToolWindowPanel): JComboBox<Review> =
        findComponents(panel)
            .filterIsInstance<JComboBox<*>>()
            .first { combo -> combo.itemCount > 0 && combo.getItemAt(0).toString() != "Review Changes" } as JComboBox<Review>

    private fun findComponents(root: Component): List<Component> = buildList {
        add(root)
        if (root is Container) {
            root.components.forEach { child -> addAll(findComponents(child)) }
        }
    }

    private fun initGitRepoWithCommit(): String {
        val root = Path.of(project.basePath!!)
        runGit(root, "init")
        runGit(root, "config", "user.email", "test@example.com")
        runGit(root, "config", "user.name", "Test User")
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/Foo.kt"), "class Foo\n")
        runGit(root, "add", ".")
        runGit(root, "commit", "-m", "initial")
        return runGit(root, "rev-parse", "HEAD").trim()
    }

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
