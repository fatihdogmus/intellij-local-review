package dev.fatihdogmus.agenticreview

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ReviewStripeToolWindowFactoryTest {
    private val project by projectFixture()

    @Test
    fun iconIsNotNull() {
        val factory = ReviewStripeToolWindowFactory()
        assertThat(factory.icon).isNotNull
    }

    @Test
    fun factoryIsDumbAware() {
        val factory = ReviewStripeToolWindowFactory()
        assertThat(factory).isInstanceOf(com.intellij.openapi.project.DumbAware::class.java)
    }

    @Test
    fun factoryImplementsToolWindowFactory() {
        val factory = ReviewStripeToolWindowFactory()
        assertThat(factory).isInstanceOf(com.intellij.openapi.wm.ToolWindowFactory::class.java)
    }
}
