package dev.fatihdogmus.agenticreview.vcs

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class UncommittedChangesProviderTest {
    private val project by projectFixture()

    @Test
    fun providerDoesNotThrowWhenConstructed() {
        val provider = UncommittedChangesProvider(project)
        assertThat(provider).isNotNull
    }

    @Test
    fun getChangedFilesReturnsList() {
        val provider = UncommittedChangesProvider(project)
        val files = provider.getChangedFiles()
        assertThat(files).isNotNull
    }
}
