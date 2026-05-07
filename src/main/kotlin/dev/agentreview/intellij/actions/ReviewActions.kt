package dev.agentreview.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import dev.agentreview.intellij.ReviewManagerService
import dev.agentreview.intellij.diff.REVIEW_DIFF_EDITOR_KEY
import dev.agentreview.intellij.export.ExportUiSupport
import dev.agentreview.intellij.ui.NewReviewDialog
import dev.agentreview.intellij.ui.showInlineCommentForm

class StartUncommittedReviewAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ReviewManagerService.getInstance(project).openDefaultReview()
    }
}

class StartReviewFromCommitHashAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = NewReviewDialog(project)
        if (!dialog.showAndGet()) return
        val commitHashes = dialog.commitHashes()
        if (commitHashes.isEmpty()) return
        backgroundReviewTask(project, if (commitHashes.size == 1) "Creating commit review" else "Creating combined commit review") {
            val manager = ReviewManagerService.getInstance(project)
            manager.createCommitRangeReview(commitHashes)
        }
    }
}

class StartReviewFromGitLogAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
        backgroundReviewTask(project, "Creating review from Git Log") {
            val manager = ReviewManagerService.getInstance(project)
            manager.createCommitRangeReview(selectedCommitHashes(selection))
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        val selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        val hasCommits = selection != null && selection.commits.isNotEmpty()
        event.presentation.isEnabledAndVisible = project != null && hasCommits
    }
}

class AddReviewCommentAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) as? EditorEx
        val requestData = editor?.getUserData(REVIEW_DIFF_EDITOR_KEY)
        if (editor != null && requestData != null) {
            val line = editor.caretModel.logicalPosition.line
            showInlineCommentForm(project, editor, line, requestData)
        } else {
            val manager = ReviewManagerService.getInstance(project)
            val review = manager.getCurrentReview()
            val file = manager.currentFilePath
            if (review != null && file != null) {
                val changedFiles = manager.loadChangedFiles(review)
                val changedFile = changedFiles.find { it.filePath == file }
                if (changedFile != null) {
                    val dialog = dev.agentreview.intellij.ui.AddCommentDialog(project, changedFile)
                    if (dialog.showAndGet()) {
                        manager.addComment(
                            review.id,
                            changedFile,
                            dialog.side(),
                            dialog.lineNumber(),
                            dialog.commentBody(),
                        )
                    }
                }
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) as? EditorEx
        val hasEditorContext = editor?.getUserData(REVIEW_DIFF_EDITOR_KEY) != null
        val manager = ReviewManagerService.getInstance(project)
        val hasReviewContext = manager.getCurrentReview() != null && manager.currentFilePath != null
        event.presentation.isEnabled = hasEditorContext || hasReviewContext
    }
}

class CopyAgentPromptAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val manager = ReviewManagerService.getInstance(project)
        val review = manager.getCurrentReview() ?: return
        manager.buildAgentPrompt(review.id)?.let { ExportUiSupport.copyToClipboard(project, it) }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabled = project != null && ReviewManagerService.getInstance(project).getCurrentReview() != null
    }
}

class OpenReviewDialogAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ReviewManagerService.getInstance(project).openDefaultReview()
    }
}

private fun selectedCommitHashes(selection: VcsLogCommitSelection): List<String> =
    ContainerUtil.map(selection.commits) { it.hash.asString() }

private fun backgroundReviewTask(project: Project, title: String, action: () -> Unit) {
    object : Task.Backgroundable(project, title, false) {
        override fun run(indicator: ProgressIndicator) {
            action()
        }
    }.queue()
}
