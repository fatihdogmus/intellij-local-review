package dev.agentreview.intellij.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.agentreview.intellij.model.CommentStatus
import dev.agentreview.intellij.model.Review
import dev.agentreview.intellij.util.displayTimestamp
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class ReviewListPanel {
    private val model = javax.swing.DefaultListModel<Review>()
    private val list = JBList(model)
    private var updatingModel = false
    val component: JComponent = JPanel(BorderLayout())

    var onSelectionChanged: ((String?) -> Unit)? = null
    var onDeleteRequested: ((Review) -> Unit)? = null

    init {
        list.cellRenderer = object : ColoredListCellRenderer<Review>() {
            override fun customizeCellRenderer(
                list: javax.swing.JList<out Review>,
                value: Review?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                value ?: return
                append(value.title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                val openCount = value.comments.count { it.status == CommentStatus.OPEN }
                val resolvedCount = value.comments.count { it.status != CommentStatus.OPEN }
                val countText = listOfNotNull(
                    openCount.takeIf { it > 0 }?.let { "$it Open" },
                    resolvedCount.takeIf { it > 0 }?.let { "$it Resolved" },
                ).joinToString(" ")
                val details = listOfNotNull(value.target.type, countText.takeIf { it.isNotEmpty() }, displayTimestamp(value.updatedAt))
                    .joinToString(" · ")
                append("\n$details")
            }
        }
        list.addListSelectionListener {
            if (!it.valueIsAdjusting && !updatingModel) {
                onSelectionChanged?.invoke(list.selectedValue?.id)
            }
        }
        list.componentPopupMenu = javax.swing.JPopupMenu().apply {
            add(javax.swing.JMenuItem("Delete Review").apply {
                addActionListener { list.selectedValue?.let { review -> onDeleteRequested?.invoke(review) } }
            })
        }
        component.preferredSize = Dimension(JBUI.scale(280), JBUI.scale(220))
        component.minimumSize = Dimension(JBUI.scale(220), JBUI.scale(160))
        component.add(JBLabel("Reviews").apply { border = JBUI.Borders.empty(6, 8) }, BorderLayout.NORTH)
        component.add(JBScrollPane(list), BorderLayout.CENTER)
    }

    fun setReviews(reviews: List<Review>, selectedReviewId: String?) {
        updatingModel = true
        try {
            model.removeAllElements()
            reviews.forEach(model::addElement)
            val selectedIndex = reviews.indexOfFirst { it.id == selectedReviewId }
            list.selectedIndex = when {
                selectedIndex >= 0 -> selectedIndex
                model.size() > 0 -> 0
                else -> -1
            }
        } finally {
            updatingModel = false
        }
    }
}
