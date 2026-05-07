package dev.agentreview.intellij.ui

import com.intellij.diff.requests.MessageDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import dev.agentreview.intellij.ReviewManagerService
import dev.agentreview.intellij.expandReviewToolWindow
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.diff.DiffRequestBuilder
import dev.agentreview.intellij.diff.ReviewDiffPanel
import dev.agentreview.intellij.export.ExportUiSupport
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.vcs.ChangedFile
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.UIManager

class ReviewToolWindowPanel(
    private val project: Project,
    private val toolWindow: ToolWindow,
) : SimpleToolWindowPanel(true, true), Disposable {
    private val panelBackground = JBColor(Color(0x5B, 0x8F, 0xD9, 0x14), Color(0x5B, 0x8F, 0xD9, 0x10))
    private val cardBorder = JBColor(Color(0xC7, 0xD8, 0xF2), Color(0x43, 0x4A, 0x57))
    private val accent = JBColor(Color(0x3F, 0x7F, 0xD8), Color(0x7F, 0xB1, 0xFF))
    private val manager = ReviewManagerService.getInstance(project)
    private val diffRequestBuilder = DiffRequestBuilder(project)
    private val diffPanel = ReviewDiffPanel(project, this)
    private val changedFilesPanel = ChangedFilesPanel()
    private val contentPanel = JPanel(BorderLayout())
    private val reviewSelector = JComboBox<Review>()
    private val deleteReviewButton = JButton("Delete")
    private var updatingReviewSelector = false
    private val stateListener: () -> Unit = { refreshUi() }
    private val changedFilesByReviewId = mutableMapOf<String, List<ChangedFile>>()
    private val changedFilesLoadSequence = AtomicInteger()
    private var resizingFromFrameEvent = false
    private val frameResizeListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            if (!toolWindow.isVisible || resizingFromFrameEvent) return
            resizingFromFrameEvent = true
            try {
                expandReviewToolWindow(project, toolWindow)
            } finally {
                resizingFromFrameEvent = false
            }
        }
    }

    init {
        manager.addListener(stateListener)
        toolbar = ReviewToolbar(::startUncommittedReview, ::startCommitReview, ::refreshUi, ::copyPrompt).component
        setContent(contentPanel)
        background = panelBackground

        reviewSelector.renderer = ReviewSelectorRenderer()
        reviewSelector.preferredSize = Dimension(360, reviewSelector.preferredSize.height)
        reviewSelector.addActionListener {
            if (!updatingReviewSelector) {
                manager.selectReview((reviewSelector.selectedItem as? Review)?.id)
            }
        }
        deleteReviewButton.addActionListener {
            val review = (reviewSelector.selectedItem as? Review) ?: manager.getCurrentReview() ?: return@addActionListener
            if (manager.confirmDelete(review)) {
                manager.deleteReview(review.id)
            }
        }

        changedFilesPanel.onSelectionChanged = { changedFile ->
            manager.selectFile(changedFile?.filePath)
            refreshDiff()
        }

        frameComponent()?.addComponentListener(frameResizeListener)

        refreshUi()
    }

    val focusTarget: JComponent
        get() = changedFilesPanel.component

    override fun dispose() {
        frameComponent()?.removeComponentListener(frameResizeListener)
        manager.removeListener(stateListener)
    }

    private fun frameComponent(): Component? = WindowManager.getInstance().getFrame(project)

    private fun refreshUi() {
        refreshReviewSelector()
        val review = manager.getCurrentReview()
        contentPanel.removeAll()
        contentPanel.add(createReviewSelectorPanel(), BorderLayout.NORTH)

        if (review == null) {
            revalidate()
            repaint()
            return
        }

        val files = changedFilesByReviewId[review.id].orEmpty()
        changedFilesPanel.setFiles(files, manager.currentFilePath)

        contentPanel.add(createMainContent(), BorderLayout.CENTER)
        loadChangedFilesIfNeeded(review)
        refreshDiff()
        revalidate()
        repaint()
    }

    private fun refreshDiff() {
        val changedFile = changedFilesPanel.selectedFile()
        val reviewId = manager.currentReviewId
        if (reviewId == null) {
            diffPanel.showDiff(MessageDiffRequest("Select review from dropdown above."))
        } else if (changedFilesByReviewId[reviewId].isNullOrEmpty()) {
            diffPanel.showDiff(MessageDiffRequest("Loading changed files..."))
        } else if (changedFile == null) {
            diffPanel.showDiff(MessageDiffRequest("Select changed file to review."))
        } else {
            diffPanel.showDiff(diffRequestBuilder.buildForFile(reviewId, changedFile))
        }
    }

    private fun loadChangedFilesIfNeeded(review: Review) {
        if (changedFilesByReviewId.containsKey(review.id)) return
        val requestId = changedFilesLoadSequence.incrementAndGet()
        object : Task.Backgroundable(project, "Loading review changes", false) {
            private var changedFiles: List<ChangedFile> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                changedFiles = manager.loadChangedFiles(review)
            }

            override fun onSuccess() {
                changedFilesByReviewId[review.id] = changedFiles
                if (requestId == changedFilesLoadSequence.get() && manager.currentReviewId == review.id) {
                    changedFilesPanel.setFiles(changedFiles, manager.currentFilePath)
                    refreshDiff()
                }
            }
        }.queue()
    }

    private fun createMainContent(): JComponent {
        val top = changedFilesPanel.component.apply {
            preferredSize = Dimension(400, 140)
            minimumSize = Dimension(280, 90)
        }
        val center = diffPanel.component.apply {
            minimumSize = Dimension(320, 240)
        }

        return JBSplitter(false, 0.20f).apply {
            firstComponent = top
            secondComponent = center
        }
    }

    private fun createReviewSelectorPanel(): JComponent = createCardPanel().apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(cardBorder, 0, 0, 1, 0),
            JBUI.Borders.empty(8, 12),
        )

        val controls = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(reviewSelector, BorderLayout.CENTER)
            add(deleteReviewButton.applyDestructiveStyle(), BorderLayout.EAST)
        }

        add(JBLabel("Review").apply {
            foreground = accent
        }, BorderLayout.WEST)
        add(controls, BorderLayout.CENTER)
    }

    private fun refreshReviewSelector() {
        val reviews = manager.listReviews().sortedWith(
            compareByDescending<Review> { it.target.type == dev.agentreview.intellij.model.ReviewTargetType.UNCOMMITTED }
                .thenByDescending { it.updatedAt },
        )
        updatingReviewSelector = true
        try {
            reviewSelector.removeAllItems()
            reviews.forEach(reviewSelector::addItem)
            val selected = reviews.firstOrNull { it.id == manager.currentReviewId }
            reviewSelector.selectedItem = selected
            deleteReviewButton.isEnabled = selected != null
        } finally {
            updatingReviewSelector = false
        }
    }

    private fun startUncommittedReview() {
        runReviewCreationTask("Creating uncommitted review") {
            manager.createUncommittedReview()
        }
    }

    private fun startCommitReview() {
        val dialog = NewReviewDialog(project)
        if (!dialog.showAndGet()) return
        val commitHashes = dialog.commitHashes()
        if (commitHashes.isEmpty()) return
        runReviewCreationTask(if (commitHashes.size == 1) "Creating commit review" else "Creating commit reviews") {
            commitHashes.forEach(manager::createCommitReview)
        }
    }

    private fun copyPrompt() {
        val review = manager.getCurrentReview() ?: return
        ExportUiSupport.copyToClipboard(manager.buildAgentPrompt(review.id))
    }

    private fun runReviewCreationTask(title: String, action: () -> Unit) {
        object : Task.Backgroundable(project, title, false) {
            override fun run(indicator: ProgressIndicator) {
                action()
            }

            override fun onSuccess() {
                toolWindow.show()
            }
        }.queue()
    }
}

private class ReviewToolbar(
    startUncommittedReview: () -> Unit,
    startCommitReview: () -> Unit,
    refreshUi: () -> Unit,
    copyPrompt: () -> Unit,
) {
    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        background = JBColor(Color(0xF2, 0xF7, 0xFF), Color(0x22, 0x26, 0x2E))
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(Color(0xC7, 0xD8, 0xF2), Color(0x43, 0x4A, 0x57)), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 12),
        )

        val primaryActions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(JButton("Uncommitted").applyPrimaryStyle().apply { addActionListener { startUncommittedReview() } })
            add(JButton("Select Commit").applySecondaryStyle().apply { addActionListener { startCommitReview() } })
            add(JButton("Refresh").applyGhostStyle().apply { addActionListener { refreshUi() } })
        }
        val utilityActions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            add(JButton("Copy Prompt").applyGhostStyle().apply { addActionListener { copyPrompt() } })
        }

        val center = JPanel(BorderLayout(12, 8)).apply {
            isOpaque = false
            add(primaryActions, BorderLayout.WEST)
            add(utilityActions, BorderLayout.EAST)
        }

        add(center, BorderLayout.CENTER)
    }
}

private class ReviewSelectorRenderer : ListCellRenderer<Review> {
    private val delegate = javax.swing.DefaultListCellRenderer()

    override fun getListCellRendererComponent(
        list: JList<out Review>,
        value: Review?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): java.awt.Component {
        val component = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (component is javax.swing.JLabel) {
            component.text = value?.let {
                val openCount = it.comments.count { comment -> comment.status == CommentStatus.OPEN }
                "${it.title} · $openCount open"
            } ?: ""
        }
        return component
    }
}

private fun createCardPanel(): JPanel = JPanel(BorderLayout(12, 0)).apply {
    background = JBColor(Color(0xF7, 0xFA, 0xFF), Color(0x25, 0x2B, 0x33))
    isOpaque = true
}

private fun JButton.applyPrimaryStyle(): JButton = apply {
    background = JBColor(Color(0x3F, 0x7F, 0xD8), Color(0x2F, 0x64, 0xB4))
    foreground = Color.WHITE
    border = JBUI.Borders.empty(8, 12)
    isOpaque = true
    isFocusPainted = false
}

private fun JButton.applySecondaryStyle(): JButton = apply {
    background = JBColor(Color(0xDF, 0xEC, 0xFF), Color(0x2F, 0x3A, 0x4D))
    foreground = JBColor(Color(0x1F, 0x4E, 0x95), Color(0xB7, 0xD1, 0xFF))
    border = JBUI.Borders.customLine(JBColor(Color(0xB7, 0xCF, 0xF5), Color(0x53, 0x62, 0x7A)), 1)
    isOpaque = true
    isFocusPainted = false
}

private fun JButton.applyGhostStyle(): JButton = apply {
    background = JBColor(Color(0xF5, 0xF8, 0xFD), Color(0x2B, 0x31, 0x3A))
    foreground = UIManager.getColor("Label.foreground")
    border = JBUI.Borders.customLine(JBColor(Color(0xD0, 0xDB, 0xEC), Color(0x46, 0x50, 0x5E)), 1)
    isOpaque = true
    isFocusPainted = false
}

private fun JButton.applyDestructiveStyle(): JButton = apply {
    background = JBColor(Color(0xFB, 0xE9, 0xE7), Color(0x3A, 0x25, 0x25))
    foreground = JBColor(Color(0xB4, 0x23, 0x18), Color(0xFF, 0xA1, 0x9A))
    border = JBUI.Borders.customLine(JBColor(Color(0xEA, 0xC1, 0xBD), Color(0x6A, 0x3F, 0x3F)), 1)
    isOpaque = true
    isFocusPainted = false
}
