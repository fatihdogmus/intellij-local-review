package dev.fatihdogmus.agenticreview.vcs

data class ChangedFile(
    val filePath: String,
    val status: ChangedFileStatus,
    val beforeContent: ReviewContent?,
    val afterContent: ReviewContent?,
    val previousFilePath: String? = null,
)

enum class ChangedFileStatus {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNKNOWN,
}

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
