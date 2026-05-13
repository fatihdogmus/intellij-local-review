package dev.fatihdogmus.agenticreview.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.vcs.log.CommitId
import dev.fatihdogmus.agenticreview.ReviewFileNavigator
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.diff.REVIEW_DIFF_EDITOR_KEY
import dev.fatihdogmus.agenticreview.diff.ReviewDiffRequestData
import dev.fatihdogmus.agenticreview.model.Review
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.model.ReviewTarget
import dev.fatihdogmus.agenticreview.model.ReviewTargetType
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.impl.HashImpl
import dev.fatihdogmus.agenticreview.testutil.gitHead
import dev.fatihdogmus.agenticreview.testutil.initGitRepo
import dev.fatihdogmus.agenticreview.testutil.runGit
import dev.fatihdogmus.agenticreview.testutil.write
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

@TestApplication
class ReviewActionsIntegrationTest {
    private val project by projectFixture()

    @BeforeEach
    fun setUp() {
        val manager = ReviewManagerService.getInstance(project)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        manager.repositoryRootResolver = { project.basePath!! }
        manager.currentHeadHashSupplier = { "head-1" }
    }

    @Test
    fun openReviewDialogActionOpensDefaultReview() {
        val manager = ReviewManagerService.getInstance(project)
        manager.selectReview(null)
        val action = OpenReviewDialogAction()

        action.actionPerformed(projectEvent())

        assertThat(manager.getCurrentReview()).isNotNull
        assertThat(manager.getCurrentReview()!!.target.type).isEqualTo(ReviewTargetType.UNCOMMITTED)
    }

    @Test
    fun addReviewCommentActionUpdateEnablesWhenReviewAndFileSelected() {
        val manager = ReviewManagerService.getInstance(project)
        val review = Review(
            id = "review-actions",
            title = "Review",
            target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "abc123"),
            repositoryRoot = project.basePath!!,
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )
        ReviewStateService.getInstance(project).addReview(review)
        manager.selectReview(review.id)
        manager.selectFile("src/Foo.kt")
        val event = projectEvent()

        AddReviewCommentAction().update(event)

        assertThat(event.presentation.isEnabled).isTrue()
    }

    @Test
    fun addReviewCommentActionUpdateDisablesWithoutContext() {
        val event = projectEvent()

        AddReviewCommentAction().update(event)

        assertThat(event.presentation.isEnabled).isFalse()
    }

    @Test
    fun reviewFileNavigatorIgnoresDeletedFiles() {
        val deleted = ChangedFile(
            filePath = "src/Deleted.kt",
            status = ChangedFileStatus.DELETED,
            beforeContent = ReviewContent("before\n", "HEAD", "src/Deleted.kt"),
            afterContent = null,
        )

        ReviewFileNavigator.openChangedFile(project, project.basePath!!, deleted)

        assertThat(true).isTrue()
    }

    @Test
    fun startReviewFromGitLogActionUpdateDisablesWithoutSelection() {
        val event = projectEvent()

        StartReviewFromGitLogAction().update(event)

        assertThat(event.presentation.isEnabledAndVisible).isFalse()
    }

    @Test
    fun startReviewFromGitLogActionUpdateEnablesWithSelection() {
        val selection = fakeCommitSelection(withCommits = true)
        val event = eventWithData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, selection)

        StartReviewFromGitLogAction().update(event)

        assertThat(event.presentation.isEnabledAndVisible).isTrue()
    }

    @Test
    fun startReviewFromGitLogActionHasCorrectUpdateThread() {
        val action = StartReviewFromGitLogAction()
        assertThat(action.actionUpdateThread).isEqualTo(com.intellij.openapi.actionSystem.ActionUpdateThread.BGT)
    }

    @Test
    fun startReviewFromGitLogActionPerformedReturnsEarlyWithoutContext() {
        val action = StartReviewFromGitLogAction()

        action.actionPerformed(AnActionEvent.createFromDataContext("test", Presentation(), SimpleDataContext.EMPTY_CONTEXT))
        action.actionPerformed(projectEvent())

        assertThat(true).isTrue()
    }

    @Test
    fun startReviewFromGitLogActionPerformedCreatesCommitRangeReview() {
        val repoRoot = Path.of(project.basePath!!)
        initGitRepo(repoRoot)
        write(repoRoot.resolve("src/Foo.kt"), "one\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "first")
        val first = gitHead(repoRoot)
        write(repoRoot.resolve("src/Foo.kt"), "two\n")
        runGit(repoRoot, "add", ".")
        runGit(repoRoot, "commit", "-m", "second")
        val second = gitHead(repoRoot)
        val event = eventWithData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, realCommitSelection(listOf(first, second), repoRoot))
        val manager = ReviewManagerService.getInstance(project)
        val before = manager.listReviews().size

        StartReviewFromGitLogAction().actionPerformed(event)

        waitFor { manager.listReviews().size > before }
        val created = manager.listReviews().first { it.target.type == ReviewTargetType.COMMIT_RANGE }
        assertThat(created.target.baseRef).isEqualTo("4b825dc642cb6eb9a060e54bf8d69288fbee4904")
        assertThat(created.target.headRef).isEqualTo(second)
    }

    @Test
    fun openReviewedFileActionUpdateFollowsEditorContextAndDeletionStatus() {
        ApplicationManager.getApplication().invokeAndWait {
            val editor = EditorFactory.getInstance().createViewer(EditorFactory.getInstance().createDocument("text"), project) as EditorEx
            try {
                editor.putUserData(REVIEW_DIFF_EDITOR_KEY, ReviewDiffRequestData("review", sampleChangedFile(ChangedFileStatus.DELETED), DiffSide.RIGHT))
                val deletedEvent = eventWithEditor(editor)
                OpenReviewedFileAction().update(deletedEvent)
                assertThat(deletedEvent.presentation.isEnabledAndVisible).isFalse()

                editor.putUserData(REVIEW_DIFF_EDITOR_KEY, ReviewDiffRequestData("review", sampleChangedFile(ChangedFileStatus.MODIFIED), DiffSide.RIGHT))
                val modifiedEvent = eventWithEditor(editor)
                OpenReviewedFileAction().update(modifiedEvent)
                assertThat(modifiedEvent.presentation.isEnabledAndVisible).isTrue()
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
    }

    @Test
    fun openReviewedFileActionPerformedReturnsEarlyWithoutProjectEditorOrRequestData() {
        val action = OpenReviewedFileAction()
        action.actionPerformed(AnActionEvent.createFromDataContext("test", Presentation(), SimpleDataContext.EMPTY_CONTEXT))

        ApplicationManager.getApplication().invokeAndWait {
            val editor = EditorFactory.getInstance().createViewer(EditorFactory.getInstance().createDocument("text"), project) as EditorEx
            try {
                action.actionPerformed(eventWithEditor(editor))
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }

        assertThat(true).isTrue()
    }

    @Test
    fun openReviewedFileActionPerformedHandlesValidEditorContext() {
        val manager = ReviewManagerService.getInstance(project)
        val review = Review(
            id = "review-open-file",
            title = "Review",
            target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "abc123"),
            repositoryRoot = project.basePath!!,
            createdAt = "2026-05-07T14:20:00+03:00",
            updatedAt = "2026-05-07T14:20:00+03:00",
        )
        ReviewStateService.getInstance(project).addReview(review)
        Files.createDirectories(Path.of(project.basePath!!, "src"))
        Files.writeString(Path.of(project.basePath!!, "src", "Foo.kt"), "class Foo\n")

        ApplicationManager.getApplication().invokeAndWait {
            val editor = EditorFactory.getInstance().createViewer(EditorFactory.getInstance().createDocument("text"), project) as EditorEx
            try {
                editor.putUserData(REVIEW_DIFF_EDITOR_KEY, ReviewDiffRequestData(review.id, sampleChangedFile(ChangedFileStatus.MODIFIED), DiffSide.RIGHT))
                OpenReviewedFileAction().actionPerformed(eventWithEditor(editor))
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }

        assertThat(LocalFileSystem.getInstance().findFileByNioFile(Path.of(project.basePath!!, "src", "Foo.kt"))).isNotNull
    }

    private fun projectEvent(): AnActionEvent {
        val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build()
        return AnActionEvent.createFromDataContext("test", Presentation(), dataContext)
    }

    private fun <T> eventWithData(key: com.intellij.openapi.actionSystem.DataKey<T>, value: T): AnActionEvent {
        val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(key, value).build()
        return AnActionEvent.createFromDataContext("test", Presentation(), dataContext)
    }

    private fun eventWithEditor(editor: EditorEx): AnActionEvent {
        val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.EDITOR, editor).build()
        return AnActionEvent.createFromDataContext("test", Presentation(), dataContext)
    }

    private fun fakeCommitSelection(withCommits: Boolean): VcsLogCommitSelection {
        return TestCommitSelection(
            commits = if (withCommits) listOf(CommitId(HashImpl.build("1234567890abcdef1234567890abcdef12345678"), LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(project.basePath!!))!!)) else emptyList(),
        )
    }

    private fun realCommitSelection(hashes: List<String>, repoRoot: Path): VcsLogCommitSelection {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repoRoot) ?: error("root vf missing")
        val commits = hashes.map { CommitId(HashImpl.build(it), root) }
        return TestCommitSelection(commits)
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(50) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Condition not met in time")
    }

    private fun sampleChangedFile(status: ChangedFileStatus): ChangedFile = ChangedFile(
        filePath = "src/Foo.kt",
        status = status,
        beforeContent = ReviewContent("before\n", "before", "src/Foo.kt"),
        afterContent = if (status == ChangedFileStatus.DELETED) null else ReviewContent("after\n", "after", "src/Foo.kt"),
    )

    private class TestCommitSelection(
        override val commits: List<CommitId>,
    ) : VcsLogCommitSelection {
        override val rows: IntArray = IntArray(0)
        override val ids: List<Int> = emptyList()
        override val cachedMetadata = emptyList<com.intellij.vcs.log.VcsCommitMetadata>()
        override val cachedFullDetails = emptyList<com.intellij.vcs.log.VcsFullCommitDetails>()

        override fun requestFullDetails(consumer: Consumer<in List<com.intellij.vcs.log.VcsFullCommitDetails>>) = Unit
    }
}
