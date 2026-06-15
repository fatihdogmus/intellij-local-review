package dev.fatihdogmus.agenticreview.snapshot

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.util.nowIso
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.GitCommandFallback
import dev.fatihdogmus.agenticreview.vcs.GitRepositoryResolver
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.UUID

@Service(Service.Level.PROJECT)
class TurnSnapshotService(private val project: Project) : Disposable {
    private val stateService = ReviewStateService.getInstance(project)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listeners = mutableSetOf<() -> Unit>()

    private var activeTurn: TurnSnapshot? = null
    private val completedTurns = mutableListOf<TurnSnapshot>()
    private val completedTurnDiffs = mutableMapOf<String, List<ChangedFile>>()

    init {
        loadState()
    }

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
        saveState()
        notifyChanged()
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
        if (turn == null || turn.sessionId != sessionId || turn.stepId != stepId) return null

        turn.endedAt = nowIso()
        turn.status = status
        changedPaths?.forEach { turn.changedPaths.add(it) }
        toolCalls?.forEach { turn.toolCalls.add(it) }

        val diffs = buildTurnDiffs(turn)
        completedTurnDiffs[turn.id] = diffs

        activeTurn = null
        completedTurns.add(turn)
        saveState()
        notifyChanged()
        return turn
    }

    fun getCompletedTurns(): List<TurnSnapshot> = completedTurns.toList()

    fun getActiveTurn(): TurnSnapshot? = activeTurn

    fun getTurnDiffs(turnId: String): List<ChangedFile> = completedTurnDiffs[turnId] ?: emptyList()

    fun findTurn(turnId: String): TurnSnapshot? = completedTurns.firstOrNull { it.id == turnId }

    fun clearAll(notify: Boolean = true) {
        activeTurn = null
        completedTurns.clear()
        completedTurnDiffs.clear()
        saveState()
        if (notify) notifyChanged()
    }

    fun hasStoredTurns(): Boolean = activeTurn != null || completedTurns.isNotEmpty() || completedTurnDiffs.isNotEmpty()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

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
        rawPath: String,
    ): ChangedFile? {
        val relativePath = normalizePath(repositoryRoot, rawPath) ?: return null
        val absolutePath = Path.of(repositoryRoot, relativePath).toString()
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

    private fun normalizePath(repositoryRoot: String, rawPath: String): String? {
        val path = rawPath.trim()
        if (path.isBlank()) return null

        val repoRootPath = Path.of(repositoryRoot).normalize()
        val candidatePath = Path.of(path).normalize()

        return if (candidatePath.isAbsolute) {
            if (!candidatePath.startsWith(repoRootPath)) return null
            repoRootPath.relativize(candidatePath).toString().replace('\\', '/')
        } else {
            path.replace('\\', '/')
        }
    }

    private fun loadState() {
        runCatching {
            val snapshotState = stateService.turnSnapshotsJson()
                .takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<TurnSnapshotState>(it) }
                ?: TurnSnapshotState()
            val diffState = stateService.turnDiffsJson()
                .takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<TurnDiffState>(it) }
                ?: TurnDiffState()

            activeTurn = snapshotState.activeTurn
            completedTurns.clear()
            completedTurns.addAll(snapshotState.completedTurns)
            completedTurnDiffs.clear()
            completedTurnDiffs.putAll(diffState.turnDiffs.associate { it.turnId to it.files })
        }
    }

    private fun saveState() {
        stateService.setTurnSnapshotsJson(
            json.encodeToString(
                TurnSnapshotState(
                    activeTurn = activeTurn,
                    completedTurns = completedTurns.toList(),
                ),
            ),
        )
        stateService.setTurnDiffsJson(
            json.encodeToString(
                TurnDiffState(
                    turnDiffs = completedTurnDiffs.entries.map { (turnId, files) -> TurnDiffEntry(turnId, files) },
                ),
            ),
        )
    }

    private fun notifyChanged() {
        val notifyListeners = {
            listeners.toList().forEach { it() }
        }
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            notifyListeners()
        } else {
            application.invokeLater {
                if (!project.isDisposed) {
                    notifyListeners()
                }
            }
        }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        fun getInstance(project: Project): TurnSnapshotService = project.service()
    }
}

@Serializable
private data class TurnSnapshotState(
    val activeTurn: TurnSnapshot? = null,
    val completedTurns: List<TurnSnapshot> = emptyList(),
)

@Serializable
private data class TurnDiffState(
    val turnDiffs: List<TurnDiffEntry> = emptyList(),
)

@Serializable
private data class TurnDiffEntry(
    val turnId: String,
    val files: List<ChangedFile> = emptyList(),
)
