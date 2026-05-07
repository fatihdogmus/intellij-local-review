package dev.agentreview.intellij.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import dev.agentreview.intellij.ui.ReviewToolWindowPanel
import com.intellij.openapi.project.Project
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class ReviewPageFileEditor(
    project: Project,
    private val file: ReviewPageVirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val panel = ReviewToolWindowPanel(project)

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel.focusTarget

    override fun getName(): String = "Review"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): ReviewPageVirtualFile = file

    override fun dispose() {
        panel.dispose()
    }
}
