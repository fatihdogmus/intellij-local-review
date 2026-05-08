package dev.fatihdogmus.agenticreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.SpinnerNumberModel

class AddCommentDialog(project: Project, changedFile: ChangedFile) : DialogWrapper(project) {
    private val sideCombo = JComboBox(DiffSide.entries.toTypedArray())
    private val lineSpinner = JSpinner(SpinnerNumberModel(1, 1, maxLine(changedFile), 1))
    private val bodyArea = JTextArea(8, 40)

    init {
        title = "Add Review Comment"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Side") { cell(sideCombo) }
        row("Line") { cell(lineSpinner) }
        row("Comment") { cell(JBScrollPane(bodyArea)) }
    }

    fun side(): DiffSide = sideCombo.selectedItem as DiffSide

    fun lineNumber(): Int = lineSpinner.value as Int

    fun commentBody(): String = bodyArea.text.trim()

    companion object {
        private fun maxLine(changedFile: ChangedFile): Int {
            val left = changedFile.beforeContent?.text?.lines()?.size ?: 1
            val right = changedFile.afterContent?.text?.lines()?.size ?: 1
            return maxOf(left, right, 1)
        }
    }
}
