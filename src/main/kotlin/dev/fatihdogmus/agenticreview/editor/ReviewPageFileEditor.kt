package dev.fatihdogmus.agenticreview.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import dev.fatihdogmus.agenticreview.ui.ReviewToolWindowPanel
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class ReviewPageFileEditor(
    project: Project,
    private val file: ReviewPageVirtualFile,
) : UserDataHolderBase(), FileEditorWithTextEditors {
    private val panel = ReviewToolWindowPanel(project)

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel.focusTarget

    override fun getName(): String = "Agentic Review"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): ReviewPageVirtualFile = file

    override fun getEmbeddedEditors(): List<Editor> = panel.embeddedEditors

    override fun dispose() {
        panel.dispose()
    }
}
