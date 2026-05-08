package dev.fatihdogmus.agenticreview.ui

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.seenKey
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.UIManager

class ChangedFilesPanel {
    private val model = DefaultListModel<ChangedFile>()
    private val list = JBList(model)
    private var updatingModel = false
    private var files: List<ChangedFile> = emptyList()
    private var selectedFilePath: String? = null
    private var commentCountsByPath: Map<String, FileCommentCounts> = emptyMap()
    private var seenFileKeys: Set<String> = emptySet()
    private val titleLabel = JBLabel("Changed Files")
    private val filterCombo = JComboBox(SeenFilter.entries.toTypedArray())
    private val sortCombo = JComboBox(SeenSort.entries.toTypedArray())
    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout())

    var onSelectionChanged: ((ChangedFile?) -> Unit)? = null
    var onOpenRequested: ((ChangedFile) -> Unit)? = null
    var onDeleteRequested: ((ChangedFile) -> Unit)? = null

    init {
        list.cellRenderer = ChangedFileCellRenderer(
            commentCountsProvider = { changedFile -> commentCountsByPath[changedFile.filePath] ?: FileCommentCounts() },
            seenProvider = { changedFile -> changedFile.seenKey() in seenFileKeys },
        )
        list.background = UIManager.getColor("List.background") ?: UIUtil.getListBackground()
        list.selectionBackground = UIManager.getColor("List.selectionBackground") ?: UIUtil.getListSelectionBackground(true)
        list.selectionForeground = UIManager.getColor("List.selectionForeground") ?: UIUtil.getListForeground(true, true)
        list.border = JBUI.Borders.empty(8, 10)
        filterCombo.selectedItem = SeenFilter.ALL
        sortCombo.selectedItem = SeenSort.DEFAULT
        filterCombo.addActionListener { refreshModel(autoSelectFirst = false, notifySelection = true) }
        sortCombo.addActionListener { refreshModel(autoSelectFirst = false, notifySelection = true) }
        list.addListSelectionListener {
            if (!it.valueIsAdjusting && !updatingModel) {
                list.requestFocusInWindow()
                onSelectionChanged?.invoke(list.selectedValue)
            }
        }
        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                list.requestFocusInWindow()
            }
            override fun mouseClicked(e: MouseEvent) {
                list.requestFocusInWindow()
                if (e.clickCount == 2) {
                    list.selectedValue?.takeIf { it.status != ChangedFileStatus.DELETED }?.let { onOpenRequested?.invoke(it) }
                }
            }
        })
        object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
                list.selectedValue?.takeIf { it.status != ChangedFileStatus.DELETED }?.let { onDeleteRequested?.invoke(it) }
            }
        }.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE).shortcutSet, component)
        component.preferredSize = Dimension(JBUI.scale(280), JBUI.scale(260))
        component.minimumSize = Dimension(JBUI.scale(220), JBUI.scale(180))
        component.background = list.background
        component.add(createHeader(), BorderLayout.NORTH)
        component.add(JBScrollPane(list).apply {
            border = JBUI.Borders.empty()
            viewport.background = list.background
        }, BorderLayout.CENTER)
    }

    fun setFiles(
        files: List<ChangedFile>,
        selectedFilePath: String?,
        commentCountsByPath: Map<String, FileCommentCounts>,
        seenFileKeys: Set<String>,
    ) {
        this.files = files
        this.selectedFilePath = selectedFilePath
        this.commentCountsByPath = commentCountsByPath
        this.seenFileKeys = seenFileKeys
        refreshModel(autoSelectFirst = true, notifySelection = false)
    }

    fun selectedFile(): ChangedFile? = list.selectedValue

    private fun createHeader(): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = component.background
        border = JBUI.Borders.empty(8, 12, 4, 12)

        add(titleLabel.apply {
            foreground = UIManager.getColor("Label.foreground")
            font = font.deriveFont(font.style or Font.BOLD)
        }, BorderLayout.WEST)

        add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(filterCombo.apply {
                toolTipText = "Filter by seen status"
            }, BorderLayout.WEST)
            add(sortCombo.apply {
                toolTipText = "Sort by seen status"
            }, BorderLayout.EAST)
        }, BorderLayout.EAST)
    }

    private fun refreshModel(autoSelectFirst: Boolean, notifySelection: Boolean) {
        val previousSelectionPath = list.selectedValue?.filePath
        val previousSelectionIndex = list.selectedIndex
        val visibleFiles = files
            .filter { filterCombo.selectionSeenFilter().matches(it, seenFileKeys) }
            .let { sortCombo.selectionSeenSort().applyTo(it, seenFileKeys) }
            .keepSelectedFileStable(previousSelectionPath, previousSelectionIndex)

        val unseenCount = files.count { it.seenKey() !in seenFileKeys }
        titleLabel.text = if (files.isEmpty() || unseenCount == 0) {
            "Changed Files"
        } else {
            "Changed Files ($unseenCount unseen)"
        }

        val wasUpdatingModel = updatingModel
        updatingModel = true
        try {
            model.removeAllElements()
            visibleFiles.forEach(model::addElement)

            val selectedIndex = visibleFiles.indexOfFirst { it.filePath == selectedFilePath }
            list.selectedIndex = when {
                selectedIndex >= 0 -> selectedIndex
                autoSelectFirst && model.size() > 0 -> 0
                else -> -1
            }
        } finally {
            updatingModel = wasUpdatingModel
        }

        if (notifySelection) {
            val currentSelectionPath = list.selectedValue?.filePath
            if (currentSelectionPath != previousSelectionPath || currentSelectionPath == null) {
                onSelectionChanged?.invoke(list.selectedValue)
            }
        }
    }
}

private enum class SeenFilter(private val label: String) {
    ALL("All"),
    UNSEEN("Unseen"),
    SEEN("Seen");

    fun matches(file: ChangedFile, seenFileKeys: Set<String>): Boolean = when (this) {
        ALL -> true
        UNSEEN -> file.seenKey() !in seenFileKeys
        SEEN -> file.seenKey() in seenFileKeys
    }

    override fun toString(): String = label
}

private enum class SeenSort(private val label: String) {
    DEFAULT("Default"),
    UNSEEN_FIRST("Unseen First"),
    SEEN_FIRST("Seen First");

    fun applyTo(files: List<ChangedFile>, seenFileKeys: Set<String>): List<ChangedFile> = when (this) {
        DEFAULT -> files
        UNSEEN_FIRST -> files.sortedBy { it.seenKey() in seenFileKeys }
        SEEN_FIRST -> files.sortedBy { it.seenKey() !in seenFileKeys }
    }

    override fun toString(): String = label
}

private fun JComboBox<SeenFilter>.selectionSeenFilter(): SeenFilter = selectedItem as? SeenFilter ?: SeenFilter.ALL

private fun JComboBox<SeenSort>.selectionSeenSort(): SeenSort = selectedItem as? SeenSort ?: SeenSort.DEFAULT

private fun List<ChangedFile>.keepSelectedFileStable(selectedFilePath: String?, selectedIndex: Int): List<ChangedFile> {
    if (selectedFilePath == null || selectedIndex < 0) return this
    val currentIndex = indexOfFirst { it.filePath == selectedFilePath }
    if (currentIndex < 0 || currentIndex == selectedIndex) return this

    val reordered = toMutableList()
    val selectedFile = reordered.removeAt(currentIndex)
    reordered.add(selectedIndex.coerceIn(0, reordered.size), selectedFile)
    return reordered
}

data class FileCommentCounts(
    val open: Int = 0,
    val resolved: Int = 0,
)

private class ChangedFileCellRenderer(
    private val commentCountsProvider: (ChangedFile) -> FileCommentCounts,
    private val seenProvider: (ChangedFile) -> Boolean,
) : ListCellRenderer<ChangedFile> {
    private val addedColor = JBColor(0x1A7F37, 0x3FB950)
    private val deletedColor = JBColor(0xCF222E, 0xF85149)
    private val openCommentColor = JBColor(0xB07A00, 0xF2CC60)
    private val resolvedCommentColor = JBColor(0x1A7F37, 0x7EE787)
    private val modifiedBadgeBg = JBColor(0xE9EEF8, 0x313A48)
    private val addedBadgeBg = JBColor(0xE7F6EC, 0x243B2D)
    private val deletedBadgeBg = JBColor(0xFBE9E7, 0x3A2525)
    private val selectedRowBg = UIManager.getColor("List.selectionBackground") ?: JBColor(Color(0xDCEBFF), Color(0x2D, 0x4A, 0x73))
    private val selectedRowBorder = UIManager.getColor("Component.focusColor") ?: JBColor(Color(0x9E, 0xBF, 0xEA), Color(0x4F, 0x78, 0xA8))
    private val rowBorder = UIManager.getColor("Component.borderColor") ?: JBColor(Color(0xD8, 0xE3, 0xF2), Color(0x32, 0x39, 0x44))
    private val title = JBLabel()
    private val titleBaseFont = title.font
    private val stats = JBLabel()
    private val commentStats = JBLabel()
    private val subtitle = JBLabel()
    private val statusBadge = JBLabel().apply {
        border = JBUI.Borders.empty(2, 6)
        isOpaque = true
    }
    private val titleRow = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        isOpaque = false
        add(title, BorderLayout.WEST)
    }
    private val content = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
        add(statusBadge)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(4)
            add(titleRow, BorderLayout.WEST)
            alignmentX = 0.0f
        })
        add(subtitle.apply {
            border = JBUI.Borders.emptyTop(3)
            alignmentX = 0.0f
        })
        add(stats.apply {
            border = JBUI.Borders.emptyTop(4)
            alignmentX = 0.0f
        })
        add(commentStats.apply {
            border = JBUI.Borders.emptyTop(3)
            alignmentX = 0.0f
        })
    }
    private val panel = JPanel(BorderLayout()).apply {
        add(content, BorderLayout.CENTER)
    }

    init {
        subtitle.foreground = JBColor(Color(0x4B, 0x63, 0x82), Color(0xF5, 0xF7, 0xFA))
        stats.font = stats.font.deriveFont(stats.font.size2D - 1f)
        subtitle.font = subtitle.font.deriveFont(subtitle.font.size2D - 1f)
        statusBadge.foreground = JBColor(0x355070, 0xB7D1FF)
    }

    override fun getListCellRendererComponent(
        list: JList<out ChangedFile>,
        value: ChangedFile?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): JComponent {
        if (value == null) return panel

        val colors = if (isSelected) {
            list.selectionBackground to list.selectionForeground
        } else {
            list.background to list.foreground
        }
        val (background, foreground) = colors

        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(value.filePath)
        val isSeen = seenProvider(value)
        title.icon = fileType.icon
        val fileName = value.filePath.substringAfterLast('/')
        title.text = if (isSeen) fileName else "$fileName *"
        title.font = titleBaseFont.deriveFont(if (isSeen) Font.PLAIN else Font.BOLD)
        title.foreground = foreground
        statusBadge.text = value.status.name
        statusBadge.foreground = statusForeground(value, isSelected, foreground)
        statusBadge.background = if (isSelected) background else statusBackground(value)

        val lineStats = calculateLineStats(value)
        stats.text = if (isSelected) {
            "+${lineStats.added}  -${lineStats.deleted}"
        } else {
            "<html><span style='color:${colorHex(addedColor)}'>+${lineStats.added}</span>&nbsp;&nbsp;<span style='color:${colorHex(deletedColor)}'>-${lineStats.deleted}</span></html>"
        }
        stats.foreground = foreground

        val commentCounts = commentCountsProvider(value)
        commentStats.text = if (commentCounts.open == 0 && commentCounts.resolved == 0) {
            ""
        } else if (isSelected) {
            "${commentCounts.open} open  ${commentCounts.resolved} resolved"
        } else {
            "<html><span style='color:${colorHex(openCommentColor)}'>${commentCounts.open} open</span>&nbsp;&nbsp;<span style='color:${colorHex(resolvedCommentColor)}'>${commentCounts.resolved} resolved</span></html>"
        }
        commentStats.foreground = foreground
        commentStats.isVisible = commentStats.text.isNotBlank()

        subtitle.text = buildSubtitle(value)
        subtitle.isVisible = subtitle.text.isNotBlank()

        val rowBackground = if (isSelected) selectedRowBg else background
        panel.background = background
        content.background = rowBackground
        title.background = rowBackground
        subtitle.background = rowBackground
        commentStats.background = rowBackground
        titleRow.background = rowBackground

        panel.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        content.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(if (isSelected) selectedRowBorder else rowBorder, 1, true),
            JBUI.Borders.empty(8),
        )
        return panel
    }

    private fun buildSubtitle(value: ChangedFile): String {
        value.previousFilePath?.let { return "$it -> ${value.filePath}" }
        val path = value.filePath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (path.isBlank()) value.filePath else path
    }

    private fun calculateLineStats(value: ChangedFile): LineStats {
        val before = value.beforeContent?.text.orEmpty()
        val after = value.afterContent?.text.orEmpty()
        if (before.isEmpty() && after.isEmpty()) return LineStats(0, 0)
        if (before.isEmpty()) return LineStats(countLines(after), 0)
        if (after.isEmpty()) return LineStats(0, countLines(before))

        val fragments = ComparisonManager.getInstance().compareLines(
            before,
            after,
            ComparisonPolicy.DEFAULT,
            DumbProgressIndicator.INSTANCE,
        )
        var added = 0
        var deleted = 0
        for (fragment in fragments) {
            deleted += fragment.endLine1 - fragment.startLine1
            added += fragment.endLine2 - fragment.startLine2
        }
        return LineStats(added, deleted)
    }

    private fun countLines(text: String): Int = if (text.isEmpty()) 0 else text.lines().size

    private fun colorHex(color: Color): String = String.format("#%02x%02x%02x", color.red, color.green, color.blue)

    private fun statusBackground(value: ChangedFile): JBColor = when (value.status) {
        ChangedFileStatus.ADDED -> addedBadgeBg
        ChangedFileStatus.DELETED -> deletedBadgeBg
        else -> modifiedBadgeBg
    }

    private fun statusForeground(value: ChangedFile, isSelected: Boolean, selectedForeground: Color): Color {
        if (isSelected) return selectedForeground
        return when (value.status) {
            ChangedFileStatus.ADDED -> JBColor(0x1A7F37, 0x7EE787)
            ChangedFileStatus.DELETED -> JBColor(0xB42318, 0xFF8E8A)
            else -> JBColor(0x355070, 0xB7D1FF)
        }
    }

    private data class LineStats(val added: Int, val deleted: Int)
}
