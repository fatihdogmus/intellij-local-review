package dev.fatihdogmus.agenticreview.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ReviewPageManagerTest {
    private val project by projectFixture()

    @Test
    fun getInstanceReturnsNonNullService() {
        val manager = ReviewPageManager.getInstance(project)
        assertThat(manager).isNotNull
    }

    @Test
    fun getInstanceReturnsSameInstance() {
        val a = ReviewPageManager.getInstance(project)
        val b = ReviewPageManager.getInstance(project)
        assertThat(a).isSameAs(b)
    }

    @Test
    fun openDoesNotThrow() {
        ApplicationManager.getApplication().invokeAndWait {
            val manager = ReviewPageManager.getInstance(project)
            manager.open()
        }
    }

    @Test
    fun disposeDoesNotThrow() {
        ApplicationManager.getApplication().invokeAndWait {
            val manager = ReviewPageManager.getInstance(project)
            manager.dispose()
        }
    }
}
