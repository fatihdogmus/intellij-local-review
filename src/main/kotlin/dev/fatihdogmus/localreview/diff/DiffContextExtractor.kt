package dev.fatihdogmus.localreview.diff

import dev.fatihdogmus.localreview.model.CommentAnchor
import dev.fatihdogmus.localreview.model.DiffSide
import dev.fatihdogmus.localreview.vcs.ChangedFile

class DiffContextExtractor {
    fun buildAnchor(
        changedFile: ChangedFile,
        side: DiffSide,
        lineNumber: Int,
        commitHash: String?,
        endLineNumber: Int? = null,
    ): CommentAnchor {
        val content = if (side == DiffSide.LEFT) changedFile.beforeContent?.text else changedFile.afterContent?.text
        val lines = content?.lines().orEmpty()
        val index = (lineNumber - 1).coerceAtLeast(0)
        val endIndex = endLineNumber?.let { (it - 1).coerceAtMost(lines.size - 1) }
        val selectedText = if (endIndex != null && endIndex > index) {
            lines.subList(index, endIndex + 1).joinToString("\n")
        } else {
            lines.getOrNull(index)
        }
        val contextStart = (index - 2).coerceAtLeast(0)
        val contextEnd = ((endIndex ?: index) + 3).coerceAtMost(lines.size)
        val before = if (index <= 0 || lines.isEmpty()) emptyList() else lines.subList(contextStart, index.coerceAtMost(lines.size))
        val afterStart = ((endIndex ?: index) + 1).coerceAtMost(lines.size)
        val after = if (lines.isEmpty() || afterStart >= lines.size) emptyList() else lines.subList(afterStart, contextEnd)
        return CommentAnchor(
            side = side,
            oldLine = if (side == DiffSide.LEFT) lineNumber else null,
            newLine = if (side == DiffSide.RIGHT) lineNumber else null,
            endOldLine = if (side == DiffSide.LEFT) endLineNumber else null,
            endNewLine = if (side == DiffSide.RIGHT) endLineNumber else null,
            selectedText = selectedText,
            beforeContext = before,
            afterContext = after,
            commitHash = commitHash,
        )
    }
}
