package dev.fatihdogmus.agenticreview.snapshot

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import dev.fatihdogmus.agenticreview.util.nowIso
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.GitCommandFallback
import dev.fatihdogmus.agenticreview.vcs.GitRepositoryResolver
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import java.util.UUID

@Service(Service.Level.PROJECT)
class TurnSnapshotService(private val project: Project) : Disposable {

    private var activeTurn: TurnSnapshot? = null
    private val completedTurns = mutableListOf<TurnSnapshot>()
    private val completedTurnDiffs = mutableMapOf<String, List<ChangedFile>>()

    fun beginTurn(
        sessionId: String,
        stepId: String,
        projectPath: String,
        agent: String?,
        model: String?,
    ): TurnSnapshot {
        val existing = activeTurn
        if (existing != null) {
            existing.status = "overlapped"
            existing.endedAt = nowIso()
            completedTurns.add(existing)
        }

        val turnId = UUID.randomUUID().toString()
        val turn = TurnSnapshot(
            id = turnId,
            sessionId = sessionId,
            stepId = stepId,
            projectPath = projectPath,
            agent = agent,
            model = model,
            startedAt = nowIso(),
        )
        activeTurn = turn
        return turn
    }

    fun endTurn(
        sessionId: String,
        stepId: String,
        status: String,
        changedPaths: List<String>?,
        toolCalls: List<TurnToolCall>?,
    ): TurnSnapshot? {
        val turn = activeTurn
        if (turn == null || turn.sessionId != sessionId) return null

        turn.endedAt = nowIso()
        turn.status = status
        changedPaths?.forEach { turn.changedPaths.add(it) }
        toolCalls?.forEach { turn.toolCalls.add(it) }

        val diffs = buildTurnDiffs(turn)
        completedTurnDiffs[turn.id] = diffs

        activeTurn = null
        completedTurns.add(turn)
        return turn
    }

    fun getCompletedTurns(): List<TurnSnapshot> = completedTurns.toList()

    fun getActiveTurn(): TurnSnapshot? = activeTurn

    fun getTurnDiffs(turnId: String): List<ChangedFile> = completedTurnDiffs[turnId] ?: emptyList()

    fun findTurn(turnId: String): TurnSnapshot? = completedTurns.firstOrNull { it.id == turnId }

    private fun buildTurnDiffs(turn: TurnSnapshot): List<ChangedFile> {
        val repositoryRoot = GitRepositoryResolver(project).resolveRepositoryRoot()
        val git = GitCommandFallback(repositoryRoot)

        return turn.changedPaths
            .mapNotNull { path -> buildChangedFile(git, repositoryRoot, path) }
            .sortedBy { it.filePath }
    }

    private fun buildChangedFile(
        git: GitCommandFallback,
        repositoryRoot: String,
        relativePath: String,
    ): ChangedFile? {
        val absolutePath = "$repositoryRoot/$relativePath"
        val vf = LocalFileSystem.getInstance().findFileByPath(absolutePath)

        val beforeText = git.runOrNull("show", "HEAD:$relativePath")
        val afterText: String? = if (vf != null && vf.exists()) {
            try {
                String(vf.contentsToByteArray())
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val status = when {
            beforeText == null && afterText != null -> ChangedFileStatus.ADDED
            beforeText != null && afterText == null -> ChangedFileStatus.DELETED
            beforeText != null && afterText != null && beforeText != afterText -> ChangedFileStatus.MODIFIED
            beforeText != null && afterText != null -> return null
            else -> return null
        }

        return ChangedFile(
            filePath = relativePath,
            status = status,
            beforeContent = beforeText?.let { ReviewContent(it, "HEAD", relativePath) },
            afterContent = afterText?.let { ReviewContent(it, "WORKTREE", relativePath) },
        )
    }

    override fun dispose() {
        activeTurn = null
        completedTurns.clear()
        completedTurnDiffs.clear()
    }

    companion object {
        fun getInstance(project: Project): TurnSnapshotService = project.service()
    }
}
