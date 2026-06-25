package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.DiffManager
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

class ReviewDiffPanel(
    project: Project,
    parentDisposable: Disposable,
) {
    private val requestPanel = DiffManager.getInstance().createRequestPanel(project, parentDisposable, null)

    init {
        requestPanel.putContextHints(
            TextDiffSettings.KEY,
            TextDiffSettings().apply {
                isExpandByDefault = false
            },
        )
    }

    val component: JComponent
        get() = requestPanel.component

    private val currentEditors = CopyOnWriteArrayList<Editor>()

    val embeddedEditors: List<Editor>
        get() = currentEditors.toList()

    fun setEmbeddedEditors(editors: List<Editor>) {
        currentEditors.clear()
        currentEditors.addAll(editors)
    }

    fun showDiff(request: DiffRequest) {
        WriteIntentReadAction.run {
            requestPanel.setRequest(request)
        }
    }
}
