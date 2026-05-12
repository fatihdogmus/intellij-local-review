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
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshot
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.seenKey
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.UIManager

class ChangedFilesPanel {
    private val model = DefaultListModel<ChangedFile>()
    private val list = JBList(model)
    private var updatingModel = false
    private var reviewFiles: List<ChangedFile> = emptyList()
    private var turnFilesById: Map<String, List<ChangedFile>> = emptyMap()
    private var seenFileKeys: Set<String> = emptySet()
    private var selectedFilePath: String? = null
    private var turnsEnabled = false
    private val titleLabel = JBLabel("Changed Files")
    private val turnCombo = JComboBox<TurnComboItem>()
    var onSelectionChanged: ((ChangedFile?) -> Unit)? = null
    var onOpenRequested: ((ChangedFile) -> Unit)? = null
    var onDeleteRequested: ((ChangedFile) -> Unit)? = null
    var onTurnChanged: ((TurnSnapshot?) -> Unit)? = null

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout())

    init {
        list.cellRenderer = ChangedFileCellRenderer { changedFile ->
            if (selectedTurn() != null) null else changedFile.seenKey() !in seenFileKeys
        }
        list.background = UIManager.getColor("List.background") ?: UIUtil.getListBackground()
        list.selectionBackground = UIManager.getColor("List.selectionBackground") ?: UIUtil.getListSelectionBackground(true)
        list.selectionForeground = UIManager.getColor("List.selectionForeground") ?: UIUtil.getListForeground(true, true)
        list.border = JBUI.Borders.empty(8, 10)

        turnCombo.renderer = TurnComboRenderer()
        turnCombo.addActionListener {
            refreshModel(autoSelectFirst = true, notifySelection = true)
            val selected = (turnCombo.selectedItem as? TurnComboItem)?.turn
            onTurnChanged?.invoke(selected)
        }

        list.addListSelectionListener {
            if (!it.valueIsAdjusting && !updatingModel) {
                list.requestFocusInWindow()
                onSelectionChanged?.invoke(list.selectedValue)
            }
        }
        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                list.requestFocusInWindow()
            }
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
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

    fun setReviewFiles(
        files: List<ChangedFile>,
        selectedFilePath: String?,
        seenFileKeys: Set<String>,
    ) {
        this.reviewFiles = files
        this.selectedFilePath = selectedFilePath
        this.seenFileKeys = seenFileKeys
        val item = turnCombo.selectedItem as? TurnComboItem
        if (item == null || item.turn == null) {
            refreshModel(autoSelectFirst = true, notifySelection = false)
        }
    }

    fun refreshTurns(turnSnapshotService: TurnSnapshotService) {
        if (!turnsEnabled) {
            turnFilesById = emptyMap()
            turnCombo.removeAllItems()
            turnCombo.addItem(TurnComboItem("Review Changes", null))
            turnCombo.selectedIndex = 0
            turnCombo.isVisible = false
            titleLabel.text = "Changed Files"
            return
        }
        val previousId = (turnCombo.selectedItem as? TurnComboItem)?.turn?.id
        turnFilesById = turnSnapshotService.getCompletedTurns().associate { it.id to turnSnapshotService.getTurnDiffs(it.id) }
        turnCombo.removeAllItems()
        turnCombo.addItem(TurnComboItem("Review Changes", null))
        for (turn in turnSnapshotService.getCompletedTurns()) {
            turnCombo.addItem(TurnComboItem(turnLabel(turn), turn))
        }
        val selectIndex = if (previousId != null) {
            (0 until turnCombo.itemCount).indexOfFirst { i ->
                turnCombo.getItemAt(i).turn?.id == previousId
            }.coerceAtLeast(0)
        } else 0
        turnCombo.selectedIndex = selectIndex
        turnCombo.isVisible = true
    }

    fun setTurnsEnabled(enabled: Boolean) {
        if (turnsEnabled == enabled) return
        turnsEnabled = enabled
        if (!enabled) {
            turnCombo.selectedIndex = 0
            refreshModel(autoSelectFirst = true, notifySelection = false)
        }
    }

    private fun turnLabel(turn: TurnSnapshot): String {
        val agent = turn.agent ?: "unknown"
        val started = try {
            Instant.parse(turn.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(FMT)
        } catch (_: Exception) { "?" }
        val duration = if (turn.endedAt != null) {
            try {
                val d = Duration.between(Instant.parse(turn.startedAt), Instant.parse(turn.endedAt))
                "${d.seconds}s"
            } catch (_: Exception) { "" }
        } else ""
        val count = turn.changedPaths.size
        return "$started  $agent  $duration  $count files"
    }

    fun selectedFile(): ChangedFile? = list.selectedValue

    fun selectedTurn(): TurnSnapshot? = (turnCombo.selectedItem as? TurnComboItem)?.turn

    fun currentFiles(): List<ChangedFile> {
        val turn = selectedTurn()
        return turn?.let { turnFilesById[it.id].orEmpty() } ?: reviewFiles
    }

    private fun createHeader(): JComponent = JPanel(BorderLayout()).apply {
        isOpaque = true
        background = component.background
        border = JBUI.Borders.empty(8, 12, 4, 12)
        add(titleLabel.apply {
            foreground = UIManager.getColor("Label.foreground")
            font = font.deriveFont(font.style or Font.BOLD)
        }, BorderLayout.WEST)
        add(turnCombo.apply {
            toolTipText = "Select turn to view its changes"
        }, BorderLayout.EAST)
    }

    private fun refreshModel(autoSelectFirst: Boolean, notifySelection: Boolean) {
        val previousSelectionPath = list.selectedValue?.filePath
        val turn = selectedTurn()
        val visibleFiles = turn?.let { turnFilesById[it.id].orEmpty() } ?: reviewFiles

        titleLabel.text = if (turn != null) "Turn Changed Files" else "Changed Files"

        val wasUpdatingModel = updatingModel
        updatingModel = true
        try {
            model.removeAllElements()
            visibleFiles.forEach(model::addElement)

            list.selectedIndex = when {
                previousSelectionPath != null -> visibleFiles.indexOfFirst { it.filePath == previousSelectionPath }.coerceAtLeast(0)
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

    private data class TurnComboItem(
        val label: String,
        val turn: TurnSnapshot?,
    ) {
        override fun toString(): String = label
    }

    private class TurnComboRenderer : ListCellRenderer<TurnComboItem> {
        private val delegate = DefaultListCellRenderer()

        override fun getListCellRendererComponent(
            list: JList<out TurnComboItem>,
            value: TurnComboItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val comp = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (comp is JLabel) {
                comp.text = value?.label ?: ""
            }
            return comp
        }
    }

    companion object {
        private val FMT = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

private class ChangedFileCellRenderer(
    private val unseenState: (ChangedFile) -> Boolean?,
) : ListCellRenderer<ChangedFile> {
    private val addedColor = JBColor(0x1A7F37, 0x3FB950)
    private val deletedColor = JBColor(0xCF222E, 0xF85149)
    private val modifiedBadgeBg = JBColor(0xE9EEF8, 0x313A48)
    private val addedBadgeBg = JBColor(0xE7F6EC, 0x243B2D)
    private val deletedBadgeBg = JBColor(0xFBE9E7, 0x3A2525)
    private val selectedRowBg = UIManager.getColor("List.selectionBackground") ?: JBColor(Color(0xDCEBFF), Color(0x2D, 0x4A, 0x73))
    private val selectedRowBorder = UIManager.getColor("Component.focusColor") ?: JBColor(Color(0x9E, 0xBF, 0xEA), Color(0x4F, 0x78, 0xA8))
    private val rowBorder = UIManager.getColor("Component.borderColor") ?: JBColor(Color(0xD8, 0xE3, 0xF2), Color(0x32, 0x39, 0x44))
    private val title = JBLabel()
    private val titleBaseFont = title.font
    private val stats = JBLabel()
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

        val bg = if (isSelected) list.selectionBackground else list.background
        val fg = if (isSelected) list.selectionForeground else list.foreground

        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(value.filePath)
        val unseen = unseenState(value)
        title.icon = fileType.icon
        title.text = if (unseen == true) "* ${value.filePath.substringAfterLast('/')}" else value.filePath.substringAfterLast('/')
        title.font = titleBaseFont.deriveFont(if (unseen == true) Font.BOLD else Font.PLAIN)
        title.foreground = fg
        statusBadge.text = value.status.name
        statusBadge.foreground = statusForeground(value, isSelected, fg)
        statusBadge.background = if (isSelected) bg else statusBackground(value)

        val lineStats = calculateLineStats(value)
        stats.text = if (isSelected) {
            "+${lineStats.added}  -${lineStats.deleted}"
        } else {
            "<html><span style='color:${colorHex(addedColor)}'>+${lineStats.added}</span>&nbsp;&nbsp;<span style='color:${colorHex(deletedColor)}'>-${lineStats.deleted}</span></html>"
        }
        stats.foreground = fg

        subtitle.text = buildSubtitle(value)
        subtitle.isVisible = subtitle.text.isNotBlank()

        val rowBackground = if (isSelected) selectedRowBg else bg
        panel.background = bg
        content.background = rowBackground
        title.background = rowBackground
        subtitle.background = rowBackground
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
