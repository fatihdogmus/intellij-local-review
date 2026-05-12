package dev.agentreview.intellij

import dev.fatihdogmus.agenticreview.model.ReviewTarget
import dev.fatihdogmus.agenticreview.model.ReviewTargetType
import dev.fatihdogmus.agenticreview.persistence.SavedReviewArchive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewManagerServiceHelpersTest {
    @Test
    fun reviewTargetHelpersMapCommitRangeAndUncommittedTargets() {
        val commitArchive = archive(ReviewTargetType.COMMIT, "base-1", "head-1")
        val rangeArchive = archive(ReviewTargetType.COMMIT_RANGE, "base-2", "head-2")
        val commitTarget = invokeStatic("toReviewTarget", commitArchive) as ReviewTarget
        val rangeTarget = invokeStatic("toReviewTarget", rangeArchive) as ReviewTarget
        val uncommittedTarget = invokeStatic("toReviewTarget", archive(ReviewTargetType.UNCOMMITTED, null, null)) as ReviewTarget

        assertThat(commitTarget.type).isEqualTo(ReviewTargetType.COMMIT)
        assertThat(commitTarget.parentHash).isEqualTo("base-1")
        assertThat(commitTarget.commitHash).isEqualTo("head-1")

        assertThat(rangeTarget.type).isEqualTo(ReviewTargetType.COMMIT_RANGE)
        assertThat(rangeTarget.baseRef).isEqualTo("base-2")
        assertThat(rangeTarget.headRef).isEqualTo("head-2")

        assertThat(uncommittedTarget.type).isEqualTo(ReviewTargetType.UNCOMMITTED)
    }

    @Test
    fun reviewTargetHelpersReturnBeginAndEndCommitsPerTargetType() {
        val commit = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = "head-1", parentHash = "base-1")
        val range = ReviewTarget(type = ReviewTargetType.COMMIT_RANGE, baseRef = "base-2", headRef = "head-2")
        val uncommitted = ReviewTarget(type = ReviewTargetType.UNCOMMITTED)

        assertThat(invokeStatic("beginCommit", commit)).isEqualTo("base-1")
        assertThat(invokeStatic("endCommit", commit)).isEqualTo("head-1")
        assertThat(invokeStatic("beginCommit", range)).isEqualTo("base-2")
        assertThat(invokeStatic("endCommit", range)).isEqualTo("head-2")
        assertThat(invokeStatic("beginCommit", uncommitted)).isNull()
        assertThat(invokeStatic("endCommit", uncommitted)).isNull()
    }

    private fun archive(type: ReviewTargetType, begin: String?, end: String?) = SavedReviewArchive(
        originalReviewId = "review-1",
        title = "Saved",
        targetType = type,
        beginCommit = begin,
        endCommit = end,
        createdAt = "2026-05-07T14:20:00+03:00",
        updatedAt = "2026-05-07T14:31:00+03:00",
        comments = emptyList(),
    )

    private fun invokeStatic(name: String, vararg args: Any?): Any? {
        val clazz = Class.forName("dev.fatihdogmus.agenticreview.ReviewManagerServiceKt")
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        val method = clazz.declaredMethods.first { it.name == name && it.parameterTypes.size == args.size }
        method.isAccessible = true
        return method.invoke(null, *args)
    }
}
