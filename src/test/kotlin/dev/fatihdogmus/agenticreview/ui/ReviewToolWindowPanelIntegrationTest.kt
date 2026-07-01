package dev.fatihdogmus.agenticreview.ui

import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.model.*
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.testutil.reviewSelector
import dev.fatihdogmus.agenticreview.testutil.turnCombo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
                @Suppress("UNCHECKED_CAST")
                val selector = reviewSelector(panel) as JComboBox<Review>
                val label =
                    selector.renderer.getListCellRendererComponent(JList<Review>(), review, 0, false, false) as JLabel

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

    @Test
    fun embeddedEditorContextsAreSeededBeforeDaemonNeedsThem() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            val virtualFile = LightVirtualFile("added-file.txt", PlainTextFileType.INSTANCE, "hello\n")
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            val document = psiFile?.let { PsiDocumentManager.getInstance(project).getDocument(it) }
            requireNotNull(psiFile)
            requireNotNull(document)

            val editor = EditorFactory.getInstance().createEditor(document, project, virtualFile, true)
            try {
                val contextManager = EditorContextManager.getInstance(project)
                assertThat(contextManager.getCachedEditorContexts(editor)).isNull()

                val method = ReviewToolWindowPanel::class.java.getDeclaredMethod("seedEmbeddedEditorContexts", List::class.java)
                method.isAccessible = true
                method.invoke(panel, listOf(editor))

                val cachedContexts = contextManager.getCachedEditorContexts(editor)
                assertThat(cachedContexts).isNotNull
                assertThat(cachedContexts!!.mainContext).isEqualTo(psiFile.codeInsightContext)
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
                Disposer.dispose(panel)
            }
        }
    }

    @Test
    fun embeddedEditorContextsAreNotSeededForLocalFileBackedEditors() {
        ApplicationManager.getApplication().invokeAndWait {
            val panel = ReviewToolWindowPanel(project)
            val path = Path.of(project.basePath!!, "src", "LiveFile.txt")
            Files.createDirectories(path.parent)
            Files.writeString(path, "hello\n")

            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            val document = virtualFile?.let { FileDocumentManager.getInstance().getDocument(it) }
            val psiFile = virtualFile?.let { PsiManager.getInstance(project).findFile(it) }
            requireNotNull(virtualFile)
            requireNotNull(document)
            requireNotNull(psiFile)

            val editor = EditorFactory.getInstance().createEditor(document, project, virtualFile, false)
            try {
                val contextManager = EditorContextManager.getInstance(project)
                assertThat(contextManager.getCachedEditorContexts(editor)).isNull()

                val method = ReviewToolWindowPanel::class.java.getDeclaredMethod("seedEmbeddedEditorContexts", List::class.java)
                method.isAccessible = true
                method.invoke(panel, listOf(editor))

                assertThat(contextManager.getCachedEditorContexts(editor)).isNull()
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
                Disposer.dispose(panel)
            }
        }
    }

    private fun initGitRepoWithCommit(): String =
        dev.fatihdogmus.agenticreview.testutil.initGitRepoWithCommit(Path.of(project.basePath!!))
}
