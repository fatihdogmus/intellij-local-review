package dev.agentreview.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.editor.ReviewPageEditorProvider
import dev.fatihdogmus.agenticreview.editor.ReviewPageFileEditor
import dev.fatihdogmus.agenticreview.editor.ReviewPageVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.Test

@TestApplication
class ReviewPageEditorIntegrationTest {
    private val project by projectFixture()

    @Test
    fun reviewPageVirtualFileIsReadOnlyPlainText() {
        val file = ReviewPageVirtualFile()

        assertThat(file.name).isEqualTo("Agentic Review")
        assertThat(file.isWritable).isFalse()
    }

    @Test
    fun editorProviderAcceptsOnlyReviewPageVirtualFiles() {
        val provider = ReviewPageEditorProvider()
        val file = ReviewPageVirtualFile()

        assertThat(provider.accept(project, file)).isTrue()
        assertThat(provider.acceptRequiresReadAction()).isFalse()
        assertThat(provider.editorTypeId).isEqualTo("agentic-review-page")
        assertThat(provider.policy).isEqualTo(FileEditorPolicy.HIDE_DEFAULT_EDITOR)
        assertThat(provider.readState(Element("state"), project, file)).isEqualTo(FileEditorState.INSTANCE)
    }

    @Test
    fun fileEditorExposesPanelAndPreferredFocus() {
        ApplicationManager.getApplication().invokeAndWait {
            val file = ReviewPageVirtualFile()
            val editor = ReviewPageFileEditor(project, file)
            try {
                assertThat(editor.name).isEqualTo("Agentic Review")
                assertThat(editor.component).isNotNull
                assertThat(editor.preferredFocusedComponent).isNotNull
                assertThat(editor.isModified).isFalse()
                assertThat(editor.isValid).isTrue()
                assertThat(editor.file).isSameAs(file)
            } finally {
                Disposer.dispose(editor)
            }
        }
    }

    @Test
    fun editorProviderCreatesReviewPageFileEditor() {
        ApplicationManager.getApplication().invokeAndWait {
            val provider = ReviewPageEditorProvider()
            val file = ReviewPageVirtualFile()
            val editor = provider.createEditor(project, file)
            try {
                assertThat(editor).isInstanceOf(ReviewPageFileEditor::class.java)
            } finally {
                Disposer.dispose(editor)
            }
        }
    }
}
