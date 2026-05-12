package dev.fatihdogmus.agenticreview.vcs

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class ChangedFile(
    val filePath: String,
    val status: ChangedFileStatus,
    val beforeContent: ReviewContent?,
    val afterContent: ReviewContent?,
    val previousFilePath: String? = null,
)

@Serializable
enum class ChangedFileStatus {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNKNOWN,
}

@Serializable
data class ReviewContent(
    val text: String,
    val revisionTitle: String,
    val filePath: String,
)

data class CommitMetadata(
    val hash: String,
    val shortHash: String,
    val subject: String,
    val firstParentHash: String?,
    val repositoryRoot: String,
)

fun ChangedFile.seenKey(): String = listOf(
    filePath,
    previousFilePath.orEmpty(),
    status.name,
    beforeContent.contentHash(),
    afterContent.contentHash(),
).joinToString("|")

private fun ReviewContent?.contentHash(): String = when (this) {
    null -> "-"
    else -> text.sha256()
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { "%02x".format(it) }
