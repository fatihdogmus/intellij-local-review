package dev.fatihdogmus.agenticreview.diff

import com.intellij.diff.DiffManager
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
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

    fun showDiff(request: DiffRequest) {
        requestPanel.setRequest(request)
    }
}
