package dev.agentreview.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.MainVcsLogUi
import dev.agentreview.intellij.VcsLogUiFactoryHelper
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class NewReviewDialog(project: Project) : DialogWrapper(project) {
    private val contentPanel = JPanel(BorderLayout())
    private var logUi: MainVcsLogUi? = null

    init {
        title = "Select Commit(s) for Review"
        init()
        isOKActionEnabled = false

        if (!VcsProjectLog.isAvailable(project)) {
            contentPanel.add(JBLabel("Git log not available for this project."), BorderLayout.CENTER)
        }

        else {
            contentPanel.add(JBLabel("Loading VCS Log..."), BorderLayout.CENTER)
            VcsProjectLog.runWhenLogIsReady(project) { manager ->
                if (isDisposed) return@runWhenLogIsReady

                val ui = VcsLogUiFactoryHelper.createMainLogUi(manager)
                Disposer.register(disposable, ui)
                ui.table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
                ui.table.selectionModel.addListSelectionListener {
                    if (!it.valueIsAdjusting) {
                        isOKActionEnabled = commitHashes().isNotEmpty()
                    }
                }
                ui.table.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent) {
                        if (e.clickCount == 2 && commitHashes().isNotEmpty()) doOKAction()
                    }
                })

                logUi = ui
                contentPanel.removeAll()
                contentPanel.add(VcsLogUiFactoryHelper.createPanel(manager, ui), BorderLayout.CENTER)
                contentPanel.revalidate()
                contentPanel.repaint()
                isOKActionEnabled = commitHashes().isNotEmpty()
            }
        }
    }

    override fun createCenterPanel(): JComponent = contentPanel.apply {
        preferredSize = Dimension(760, 420)
    }

    fun commitHashes(): List<String> = logUi?.table?.selection?.commits?.map { it.hash.asString() }.orEmpty()
}
