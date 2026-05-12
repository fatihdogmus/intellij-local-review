package dev.agentreview.intellij

import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import dev.fatihdogmus.agenticreview.vcs.seenKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChangedFileModelsUnitTest {
    @Test
    fun seenKeyIncludesPreviousPathAndContentHashes() {
        val base = ChangedFile(
            filePath = "src/New.kt",
            status = ChangedFileStatus.RENAMED,
            beforeContent = ReviewContent("before", "HEAD", "src/Old.kt"),
            afterContent = ReviewContent("after", "WORKTREE", "src/New.kt"),
            previousFilePath = "src/Old.kt",
        )
        val same = base.copy()
        val changed = base.copy(afterContent = ReviewContent("after!", "WORKTREE", "src/New.kt"))
        val noContent = ChangedFile(
            filePath = "src/Added.kt",
            status = ChangedFileStatus.ADDED,
            beforeContent = null,
            afterContent = null,
        )

        assertThat(base.seenKey()).isEqualTo(same.seenKey())
        assertThat(base.seenKey()).isNotEqualTo(changed.seenKey())
        assertThat(noContent.seenKey()).contains("src/Added.kt||ADDED|-|-")
    }
}
