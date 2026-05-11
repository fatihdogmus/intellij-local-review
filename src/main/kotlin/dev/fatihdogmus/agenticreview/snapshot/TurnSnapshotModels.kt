package dev.fatihdogmus.agenticreview.snapshot

import kotlinx.serialization.Serializable

@Serializable
data class TurnSnapshot(
    val id: String,
    val sessionId: String,
    val stepId: String,
    val projectPath: String,
    val agent: String? = null,
    val model: String? = null,
    val startedAt: String,
    var endedAt: String? = null,
    var status: String = "running",
    val changedPaths: MutableSet<String> = linkedSetOf(),
    val toolCalls: MutableList<TurnToolCall> = mutableListOf(),
)

@Serializable
data class TurnToolCall(
    val callId: String,
    val tool: String,
    val changedPaths: MutableSet<String> = linkedSetOf(),
    val metadataJson: String? = null,
)

@Serializable
data class TurnSnapshotResult(
    val ok: Boolean,
    val turnId: String,
    val error: String? = null,
)

@Serializable
data class TurnSnapshotListResult(
    val turns: List<TurnSnapshotSummary>,
)

@Serializable
data class TurnSnapshotSummary(
    val id: String,
    val sessionId: String,
    val stepId: String,
    val agent: String?,
    val model: String?,
    val startedAt: String,
    val endedAt: String?,
    val status: String,
    val changedFileCount: Int,
)
