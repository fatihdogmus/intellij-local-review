package dev.fatihdogmus.agenticreview.vcs

import com.intellij.openapi.vcs.changes.Change
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

@TestApplication
class UncommittedChangesProviderUnitTest {
    private val project by projectFixture()
    private val provider by lazy { UncommittedChangesProvider(project) }

    @ParameterizedTest
    @EnumSource(Change.Type::class)
    fun mapStatusAllTypes(type: Change.Type) {
        assertThat(provider.mapStatus(type, null, null)).isNotNull
    }

    @Test
    fun mapStatusNew() {
        assertThat(provider.mapStatus(Change.Type.NEW, null, null)).isEqualTo(ChangedFileStatus.ADDED)
    }

    @Test
    fun mapStatusModification() {
        assertThat(provider.mapStatus(Change.Type.MODIFICATION, "same/path", "same/path")).isEqualTo(ChangedFileStatus.MODIFIED)
    }

    @Test
    fun mapStatusRename() {
        assertThat(provider.mapStatus(Change.Type.MODIFICATION, "old/path", "new/path")).isEqualTo(ChangedFileStatus.RENAMED)
    }

    @Test
    fun mapStatusRenameWithNewFileOnly() {
        assertThat(provider.mapStatus(Change.Type.MODIFICATION, null, "new/path")).isEqualTo(ChangedFileStatus.MODIFIED)
    }

    @Test
    fun mapStatusDeleted() {
        assertThat(provider.mapStatus(Change.Type.DELETED, null, null)).isEqualTo(ChangedFileStatus.DELETED)
    }

    @Test
    fun mapStatusMoved() {
        assertThat(provider.mapStatus(Change.Type.MOVED, null, null)).isEqualTo(ChangedFileStatus.RENAMED)
    }

    @ParameterizedTest
    @CsvSource(
        "/repo/base, /repo/base/src/Foo.kt, src/Foo.kt",
        "/repo/base, /repo/base/src/bar/Baz.kt, src/bar/Baz.kt",
    )
    fun toRelativePathInsideRepo(repoRoot: String, absolutePath: String, expected: String) {
        with(provider) {
            assertThat(absolutePath.toRelativePath(repoRoot)).isEqualTo(expected)
        }
    }

    @Test
    fun toRelativePathOutsideRepo() {
        with(provider) {
            assertThat("/other/path/Foo.kt".toRelativePath("/repo/base")).isEqualTo("../../other/path/Foo.kt")
        }
    }
}
