package dev.fatihdogmus.agenticreview.vcs

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitRepositoryResolverTest {
    private val project by projectFixture()

    @Test
    fun resolveRepositoryRootFallsBackToBasePath() {
        val root = GitRepositoryResolver(project).resolveRepositoryRoot()
        assertThat(root).isEqualTo(project.basePath)
    }
}
