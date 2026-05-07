package dev.agentreview.intellij.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.agentreview.intellij.model.ReviewComment
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class CommentsPanel(
    private val onEditComment: () -> Unit,
    private val onMarkAddressed: (ReviewComment) -> Unit,
    private val onResolve: (ReviewComment) -> Unit,
    private val onReopen: (ReviewComment) -> Unit,
    private val onWontFix: (ReviewComment) -> Unit,
) {
    private val model = javax.swing.DefaultListModel<ReviewComment>()
    private val list = JBList(model)
    val component: JComponent = JPanel(BorderLayout())

    init {
        list.cellRenderer = object : ColoredListCellRenderer<ReviewComment>() {
            override fun customizeCellRenderer(
                list: javax.swing.JList<out ReviewComment>,
                value: ReviewComment?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                value ?: return
                val line = value.anchor.newLine ?: value.anchor.oldLine ?: 0
                val endLine = value.anchor.endNewLine ?: value.anchor.endOldLine
                val lineLabel = if (endLine != null && endLine > line) "Lines $line-$endLine" else "Line $line"
                append("$lineLabel · ${value.severity} · ${value.status}", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("\n${value.body}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }

        val actions = JPanel().apply {
            add(JButton("Edit").apply { addActionListener { onEditComment() } })
            add(JButton("Mark Addressed").apply { addActionListener { selectedComment()?.let(onMarkAddressed) } })
            add(JButton("Resolve").apply { addActionListener { selectedComment()?.let(onResolve) } })
            add(JButton("Reopen").apply { addActionListener { selectedComment()?.let(onReopen) } })
            add(JButton("Won't Fix").apply { addActionListener { selectedComment()?.let(onWontFix) } })
        }

        component.preferredSize = Dimension(JBUI.scale(380), JBUI.scale(220))
        component.minimumSize = Dimension(JBUI.scale(260), JBUI.scale(180))
        component.add(JBLabel("Comments").apply { border = JBUI.Borders.empty(6, 8) }, BorderLayout.NORTH)
        component.add(JBScrollPane(list), BorderLayout.CENTER)
        component.add(actions, BorderLayout.SOUTH)
    }

    fun setComments(comments: List<ReviewComment>) {
        model.removeAllElements()
        comments.forEach(model::addElement)
        if (model.size() > 0 && list.selectedIndex < 0) {
            list.selectedIndex = 0
        }
    }

    fun selectedComment(): ReviewComment? = list.selectedValue
}
