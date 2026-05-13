package dev.fatihdogmus.agenticreview.vcs

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class UncommittedChangesProviderBranchTest {
    private val project by projectFixture()
    private val provider by lazy { UncommittedChangesProvider(project) }
    private val repoRoot = "/repo"

    @Test
    fun toChangedFileModification() {
        val beforeRev = SimpleContentRevision("old", LocalFilePath("/repo/src/Foo.kt", false), "1")
        val afterRev = SimpleContentRevision("new", LocalFilePath("/repo/src/Foo.kt", false), "2")
        val change = Change(beforeRev, afterRev)
        val result = provider.run { change.toChangedFile(repoRoot) }
        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(ChangedFileStatus.MODIFIED)
    }

    @Test
    fun toChangedFileNew() {
        val afterRev = SimpleContentRevision("content", LocalFilePath("/repo/src/New.kt", false), "1")
        val change = Change(null, afterRev)
        val result = provider.run { change.toChangedFile(repoRoot) }
        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(ChangedFileStatus.ADDED)
    }

    @Test
    fun toChangedFileDeleted() {
        val beforeRev = SimpleContentRevision("content", LocalFilePath("/repo/src/Deleted.kt", false), "1")
        val change = Change(beforeRev, null)
        val result = provider.run { change.toChangedFile(repoRoot) }
        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(ChangedFileStatus.DELETED)
    }

    @Test
    fun toChangedFileMoved() {
        val beforeRev = SimpleContentRevision("content", LocalFilePath("/repo/src/old.kt", false), "1")
        val afterRev = SimpleContentRevision("content", LocalFilePath("/repo/src/new.kt", false), "2")
        val change = Change(beforeRev, afterRev)
        assertThat(change.type).isEqualTo(Change.Type.MOVED)
        val result = provider.run { change.toChangedFile(repoRoot) }
        assertThat(result).isNotNull
        assertThat(result!!.status).isEqualTo(ChangedFileStatus.RENAMED)
        assertThat(result.previousFilePath).isEqualTo("src/old.kt")
    }

    @Test
    fun toReviewContentWithContent() {
        val rev = SimpleContentRevision("hello", LocalFilePath("/repo/src/Foo.kt", false), "42")
        val result = provider.run { rev.toReviewContent("src/Foo.kt") }
        assertThat(result).isNotNull
        assertThat(result!!.text).isEqualTo("hello")
        assertThat(result.revisionTitle).isEqualTo("42")
    }

    @Test
    fun toReviewContentWithNullPath() {
        val rev = SimpleContentRevision("hello", LocalFilePath("/repo/src/Foo.kt", false), "1")
        val result = provider.run { rev.toReviewContent(null) }
        assertThat(result).isNotNull
        assertThat(result!!.filePath).isEqualTo("Foo.kt")
    }

    @Test
    fun toReviewContentReturnsNullOnVcsException() {
        val rev = object : SimpleContentRevision("", LocalFilePath("/repo/src/Foo.kt", false), "1") {
            override fun getContent(): String? = throw VcsException("fail")
        }
        val result = provider.run { rev.toReviewContent("src/Foo.kt") }
        assertThat(result).isNull()
    }

    @Test
    fun mapStatusModificationBeforePathNotNullAfterPathNull() {
        assertThat(provider.mapStatus(Change.Type.MODIFICATION, "old/path", null)).isEqualTo(ChangedFileStatus.MODIFIED)
    }

    @Test
    fun toRelativePathWithRelativeInput() {
        val result = provider.run { "relative/path".toRelativePath("/repo/base") }
        assertThat(result).isEqualTo("relative/path")
    }
}
