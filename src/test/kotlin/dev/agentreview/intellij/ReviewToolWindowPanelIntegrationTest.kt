package dev.agentreview.intellij

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
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    fun refreshUiShowsTurnsOnlyForUncommittedReview() {
        val manager = ReviewManagerService.getInstance(project)
        manager.openDefaultReview()
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            val changedFilesPanel = field<Any>(panel, "changedFilesPanel")
            val turnCombo = field<JComboBox<*>>(changedFilesPanel, "turnCombo")

            TurnSnapshotService.getInstance(project).beginTurn("session-1", "step-1", project.basePath!!, null, null)
            TurnSnapshotService.getInstance(project).endTurn("session-1", "step-1", "completed", emptyList(), emptyList())
            invoke(panel, "refreshUi")

            assertThat(turnCombo.isVisible).isTrue()

            val commitReview = Review(
                id = "review-commit-ui",
                title = "Commit review",
                target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "abc123"),
                repositoryRoot = project.basePath!!,
                createdAt = "2026-05-07T14:20:00+03:00",
                updatedAt = "2026-05-07T14:20:00+03:00",
            )
            ReviewStateService.getInstance(project).addReview(commitReview)
            field<MutableMap<String, List<ChangedFile>>>(panel, "changedFilesByReviewId")[commitReview.id] = listOf(sampleChangedFile("src/Foo.kt"))
            manager.selectReview(commitReview.id)

            invoke(panel, "refreshUi")

            assertThat(turnCombo.isVisible).isFalse()
            Disposer.dispose(panel)
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
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            val reviewSelector = field<JComboBox<Review>>(panel, "reviewSelector")
            val label = reviewSelector.renderer.getListCellRendererComponent(JList(), review, 0, false, false) as JLabel

            assertThat(label.text).isEqualTo("Renderer review · 1 Open 1 Resolved")
            Disposer.dispose(panel)
        }
    }

    @Test
    fun focusTargetReturnsChangedFilesComponent() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)

            assertThat(panel.focusTarget).isNotNull
            Disposer.dispose(panel)
        }
    }

    @Test
    fun refreshDiffForTurnHandlesMissingDiffsAndMissingSelection() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            val changedFilesPanel = field<Any>(panel, "changedFilesPanel")
            val turnService = TurnSnapshotService.getInstance(project)
            val turn = turnService.beginTurn("session-turn-ui", "step-turn-ui", project.basePath!!, null, null)
            turnService.endTurn("session-turn-ui", "step-turn-ui", "completed", emptyList(), emptyList())

            invokeWithArg(panel, "refreshDiffForTurn", turn)
            invokeWithArgs(changedFilesPanel, "refreshModel", true, true)
            invokeWithArg(panel, "refreshDiffForTurn", turn)

            Disposer.dispose(panel)
        }
    }

    @Test
    fun refreshDiffForTurnHandlesMatchedSelectedFile() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            val changedFilesPanel = field<Any>(panel, "changedFilesPanel")
            val changed = sampleChangedFile("src/Foo.kt")
            invokeSetReviewFiles(changedFilesPanel, listOf(changed), "src/Foo.kt", emptySet<String>())
            val turn = TurnSnapshotService.getInstance(project).beginTurn("session-turn-match", "step-turn-match", project.basePath!!, null, null)
            field<MutableMap<String, List<ChangedFile>>>(changedFilesPanel, "turnFilesById")[turn.id] = listOf(changed)
            setSelectedTurn(changedFilesPanel, turn)

            invokeWithArg(panel, "refreshDiffForTurn", turn)

            Disposer.dispose(panel)
        }
    }

    private fun sampleChangedFile(path: String): ChangedFile = ChangedFile(
        filePath = path,
        status = ChangedFileStatus.MODIFIED,
        beforeContent = ReviewContent("before\n", "before", path),
        afterContent = ReviewContent("after\n", "after", path),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    private fun invoke(target: Any, name: String) {
        val method = target.javaClass.getDeclaredMethod(name)
        method.isAccessible = true
        method.invoke(target)
    }

    private fun invokeWithArg(target: Any, name: String, arg: Any?) {
        val method = target.javaClass.getDeclaredMethods().first { it.name == name && it.parameterCount == 1 }
        method.isAccessible = true
        method.invoke(target, arg)
    }

    private fun invokeWithArgs(target: Any, name: String, vararg args: Any?) {
        val method = target.javaClass.getDeclaredMethods().first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        method.invoke(target, *args)
    }

    private fun invokeSetReviewFiles(target: Any, files: List<ChangedFile>, selectedFilePath: String?, seen: Set<String>) {
        val method = target.javaClass.getDeclaredMethod("setReviewFiles", List::class.java, String::class.java, Set::class.java)
        method.isAccessible = true
        method.invoke(target, files, selectedFilePath, seen)
    }

    private fun setSelectedTurn(target: Any, turn: Any) {
        val combo = field<JComboBox<Any>>(target, "turnCombo")
        val itemClass = Class.forName("dev.fatihdogmus.agenticreview.ui.ChangedFilesPanel\$TurnComboItem")
        val ctor = itemClass.declaredConstructors.single()
        ctor.isAccessible = true
        combo.removeAllItems()
        combo.addItem(ctor.newInstance("Turn", turn) as Any)
        combo.selectedIndex = 0
    }
}
