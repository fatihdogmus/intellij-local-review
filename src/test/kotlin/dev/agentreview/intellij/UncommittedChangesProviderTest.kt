package dev.agentreview.intellij

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.UncommittedChangesProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class UncommittedChangesProviderTest {
    private val project by projectFixture()

    @Test
    fun toChangedFileBuildsModifiedFile() {
        val provider = UncommittedChangesProvider(project)
        val change = Change(
            revision("/repo/src/Foo.kt", "before", "r1"),
            revision("/repo/src/Foo.kt", "after", "r2"),
            FileStatus.MODIFIED,
        )

        val changedFile = invoke<Any?>(provider, "toChangedFile", arrayOf(String::class.java), "/repo") { change }

        val file = changedFile as dev.fatihdogmus.agenticreview.vcs.ChangedFile
        assertThat(file.filePath).isEqualTo("src/Foo.kt")
        assertThat(file.status).isEqualTo(ChangedFileStatus.MODIFIED)
        assertThat(file.beforeContent?.text).isEqualTo("before")
        assertThat(file.afterContent?.text).isEqualTo("after")
    }

    @Test
    fun toChangedFileBuildsRenamedFileForMovedAndPathChangeModification() {
        val provider = UncommittedChangesProvider(project)
        val moved = typedChange(
            revision("/repo/src/Old.kt", "before", "r1"),
            revision("/repo/src/New.kt", "after", "r2"),
            Change.Type.MOVED,
        )
        val pathChangedModification = typedChange(
            revision("/repo/src/Old2.kt", "before2", "r1"),
            revision("/repo/src/New2.kt", "after2", "r2"),
            Change.Type.MODIFICATION,
        )

        val movedFile = invoke<Any?>(provider, "toChangedFile", arrayOf(String::class.java), "/repo") { moved } as dev.fatihdogmus.agenticreview.vcs.ChangedFile
        val renamedFile = invoke<Any?>(provider, "toChangedFile", arrayOf(String::class.java), "/repo") { pathChangedModification } as dev.fatihdogmus.agenticreview.vcs.ChangedFile

        assertThat(movedFile.status).isEqualTo(ChangedFileStatus.RENAMED)
        assertThat(movedFile.previousFilePath).isEqualTo("src/Old.kt")
        assertThat(renamedFile.status).isEqualTo(ChangedFileStatus.RENAMED)
        assertThat(renamedFile.previousFilePath).isEqualTo("src/Old2.kt")
    }

    @Test
    fun toChangedFileBuildsAddedAndDeletedFiles() {
        val provider = UncommittedChangesProvider(project)
        val added = typedChange(null, revision("/repo/src/New.kt", "after", "r2"), Change.Type.NEW)
        val deleted = typedChange(revision("/repo/src/Old.kt", "before", "r1"), null, Change.Type.DELETED)

        val addedFile = invoke<Any?>(provider, "toChangedFile", arrayOf(String::class.java), "/repo") { added } as dev.fatihdogmus.agenticreview.vcs.ChangedFile
        val deletedFile = invoke<Any?>(provider, "toChangedFile", arrayOf(String::class.java), "/repo") { deleted } as dev.fatihdogmus.agenticreview.vcs.ChangedFile

        assertThat(addedFile.status).isEqualTo(ChangedFileStatus.ADDED)
        assertThat(addedFile.beforeContent).isNull()
        assertThat(addedFile.afterContent?.text).isEqualTo("after")

        assertThat(deletedFile.status).isEqualTo(ChangedFileStatus.DELETED)
        assertThat(deletedFile.beforeContent?.text).isEqualTo("before")
        assertThat(deletedFile.afterContent).isNull()
    }

    @Test
    fun toReviewContentHandlesRevisionExceptionAndNullContent() {
        val provider = UncommittedChangesProvider(project)
        val throwing = object : ContentRevision {
            override fun getFile(): FilePath = LocalFilePath("/repo/src/Foo.kt", false)
            override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber("r1")
            override fun getContent(): String? = throw VcsException("boom")
        }
        val nullContent = object : ContentRevision {
            override fun getFile(): FilePath = LocalFilePath("/repo/src/Foo.kt", false)
            override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber("r1")
            override fun getContent(): String? = null
        }
        val ok = revision("/repo/src/Foo.kt", "text", "r2")

        val throwingResult = invokeContentRevisionExtension<Any?>(provider, "toReviewContent", "src/Foo.kt", throwing)
        val nullResult = invokeContentRevisionExtension<Any?>(provider, "toReviewContent", "src/Foo.kt", nullContent)
        val okResult = invokeContentRevisionExtension<Any?>(provider, "toReviewContent", null, ok) as dev.fatihdogmus.agenticreview.vcs.ReviewContent

        assertThat(throwingResult).isNull()
        assertThat(nullResult).isNull()
        assertThat(okResult.text).isEqualTo("text")
        assertThat(okResult.filePath).isEqualTo("Foo.kt")
    }

    @Test
    fun toRelativePathFallsBackToNormalizedAbsolutePathOnRelativizeFailure() {
        val provider = UncommittedChangesProvider(project)

        val path = invokeStringExtension<String>(provider, "toRelativePath", "/repo") {
            "C:\\repo\\src\\Foo.kt"
        }

        assertThat(path).isEqualTo("C:/repo/src/Foo.kt")
    }

    private fun revision(path: String, content: String?, revision: String): ContentRevision = object : ContentRevision {
        override fun getFile(): FilePath = LocalFilePath(path, false)
        override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber(revision)
        override fun getContent(): String? = content
    }

    private fun typedChange(before: ContentRevision?, after: ContentRevision?, type: Change.Type): Change = object : Change(before, after) {
        override fun getType(): Type = type
    }

    private fun revisionNumber(value: String): VcsRevisionNumber = object : VcsRevisionNumber {
        override fun asString(): String = value
        override fun compareTo(other: VcsRevisionNumber): Int = asString().compareTo(other.asString())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> invokeContentRevisionExtension(target: Any, name: String, arg: String?, receiver: ContentRevision): R {
        val method = target.javaClass.getDeclaredMethod(name, ContentRevision::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(target, receiver, arg) as R
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> invokeStringExtension(target: Any, name: String, arg: String, receiver: () -> String): R {
        val method = target.javaClass.getDeclaredMethod(name, String::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(target, receiver(), arg) as R
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> invoke(target: Any, name: String, parameterTypes: Array<Class<*>>, arg: Any, receiver: () -> Any): R {
        val method = target.javaClass.getDeclaredMethod(name, Change::class.java, *parameterTypes)
        method.isAccessible = true
        return method.invoke(target, receiver(), arg) as R
    }
}
