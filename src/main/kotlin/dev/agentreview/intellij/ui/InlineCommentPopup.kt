package dev.agentreview.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.util.IconUtil
import dev.agentreview.intellij.ReviewManagerService
import dev.agentreview.intellij.diff.ReviewDiffRequestData
import dev.agentreview.intellij.model.ReviewComment
import java.awt.Toolkit
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.AbstractBorder

private const val PLACEHOLDER_TEXT = "Add comment"
private const val COMMENT_ACTIONS_PLACE = "LocalReview.CommentActions"
private const val COMMENT_CARD_ARC = 18
private val BLUE_BORDER = UIManager.getColor("Component.focusColor") ?: JBColor(Color(0x35, 0x7A, 0xB8), Color(0x6C, 0xB8, 0xFF))
private val COMMENT_BORDER = UIManager.getColor("Component.borderColor") ?: JBColor.border()
private val DELETE_RED = JBColor(Color(0xC4, 0x2B, 0x1C), Color(0xFF, 0x8E, 0x8A))
private val MENU_HOVER_BG = UIManager.getColor("List.selectionBackground") ?: JBColor(Color(0xE8, 0xF1, 0xFF), Color(0x2C, 0x3D, 0x55))

@Suppress("UnstableApiUsage")
fun showReviewCommentInlays(
    project: Project,
    editor: EditorEx,
    requestData: ReviewDiffRequestData,
): List<Inlay<*>> {
    val manager = ReviewManagerService.getInstance(project)
    return manager.commentsForFile(requestData.reviewId, requestData.changedFile.filePath)
        .mapNotNull { comment ->
            val line = (comment.anchor.newLine ?: comment.anchor.oldLine)?.minus(1) ?: return@mapNotNull null
            val panel = ExistingCommentPanel(project, editor, comment)
            val inlay = insertInlineComponent(editor, line, panel)
            panel.inlayRef = inlay
            inlay
        }
}

@Suppress("UnstableApiUsage")
fun showInlineCommentForm(
    project: Project,
    editor: EditorEx,
    startLine: Int,
    requestData: ReviewDiffRequestData,
    endLine: Int? = null,
) {
    val form = InlineCommentFormPanel(project, editor, startLine, requestData, endLine)
    val inlay = insertInlineComponent(editor, startLine, form)

    form.inlayRef = inlay
    form.requestTextFocus()
}

@Suppress("UnstableApiUsage")
private fun insertInlineComponent(editor: EditorEx, line: Int, component: JPanel): Inlay<*>? {
    val safeLine = line.coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))
    val offset = if (editor.document.lineCount > 0) editor.document.getLineEndOffset(safeLine) else editor.document.textLength
    return editor.addComponentInlay(
        offset,
        InlayProperties()
            .relatesToPrecedingText(true)
            .priority(0),
        component,
        com.intellij.openapi.editor.ComponentInlayAlignment.FIT_VIEWPORT_WIDTH,
    )
}

private class InlineCommentFormPanel(
    private val project: Project,
    private val editor: EditorEx,
    private val startLine: Int,
    private val requestData: ReviewDiffRequestData,
    private val endLine: Int?,
) : JPanel(BorderLayout(0, 6)) {

    var inlayRef: Inlay<*>? = null
    private val textArea: JBTextArea

    init {
        isOpaque = true
        background = editor.colorsScheme.defaultBackground
        border = BorderFactory.createEmptyBorder(8, 16, 8, 16)

        // Text area with placeholder behaviour
        textArea = JBTextArea(3, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            foreground = JBColor.GRAY
            text = PLACEHOLDER_TEXT
            background = editor.colorsScheme.defaultBackground
            caretColor = editor.colorsScheme.defaultForeground
            cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)

            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent?) {
                    if (text == PLACEHOLDER_TEXT) {
                        text = ""
                        foreground = editor.colorsScheme.defaultForeground
                    }
                }

                override fun focusLost(e: FocusEvent?) {
                    if (text.isBlank()) {
                        text = PLACEHOLDER_TEXT
                        foreground = JBColor.GRAY
                    }
                }
            })
        }

        // Scroll pane with blue border
        val scrollPane = JScrollPane(textArea).apply {
            border = BorderFactory.createLineBorder(BLUE_BORDER, 1)
            preferredSize = Dimension(400, 80)
            isOpaque = false
            viewport.isOpaque = false
        }

        // Hint label
        val hintText = if (endLine != null && endLine > startLine) {
            "Commenting on lines ${startLine + 1}-${endLine + 1}"
        } else {
            "Commenting on line ${startLine + 1}"
        }
        val hintLabel = JLabel(hintText).apply {
            foreground = JBColor.GRAY
        }

        // Buttons
        val cancelButton = JButton("Cancel").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { dismiss() }
        }
        val commentButton = JButton("Comment").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                val body = textArea.text.trim()
                if (body.isNotEmpty() && body != PLACEHOLDER_TEXT) {
                    ReviewManagerService.getInstance(project).addComment(
                        requestData.reviewId,
                        requestData.changedFile,
                        requestData.commentSide,
                        startLine + 1,
                        body,
                        endLineNumber = endLine?.let { it + 1 },
                    )
                }
                dismiss()
            }
        }
        installSubmitShortcut(textArea) { commentButton.doClick() }

        // Bottom panel with hint and buttons
        val bottomPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(hintLabel, BorderLayout.WEST)
            val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(cancelButton)
                add(commentButton)
            }
            add(buttonsPanel, BorderLayout.EAST)
        }

        add(scrollPane, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
    }

    fun requestTextFocus() {
        textArea.requestFocusInWindow()
    }

    private fun dismiss() {
        inlayRef?.let { Disposer.dispose(it) }
    }
}

private class ExistingCommentPanel(
    project: Project,
    editor: EditorEx,
    private val comment: ReviewComment,
) : JPanel(BorderLayout(0, 6)) {

    var inlayRef: Inlay<*>? = null
    private val normalBackground = editor.colorsScheme.defaultBackground
    private var editing = false
    private val menuButton: JButton
    private val body: JBTextArea
    private val editActionsPanel: JPanel

    init {
        isOpaque = true
        background = normalBackground
        border = BorderFactory.createCompoundBorder(
            RoundedLineBorder(COMMENT_BORDER, COMMENT_CARD_ARC),
            BorderFactory.createEmptyBorder(8, 12, 8, 12),
        )

        val line = comment.anchor.newLine ?: comment.anchor.oldLine ?: 0
        val endLine = comment.anchor.endNewLine ?: comment.anchor.endOldLine
        val lineLabel = if (endLine != null && endLine > line) "Lines $line-$endLine" else "Line $line"

        menuButton = JButton("...").apply {
            icon = AllIcons.Actions.More
            text = null
            isFocusable = false
            isContentAreaFilled = false
            isBorderPainted = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Comment actions"
            isVisible = true
            addActionListener {
                val group = DefaultActionGroup().apply {
                    add(object : AnAction("Edit comment", null, IconUtil.colorize(AllIcons.Actions.Edit, BLUE_BORDER, false, false)) {
                        override fun actionPerformed(e: AnActionEvent) {
                            enterEditMode()
                        }
                    })
                    add(object : AnAction("Resolve comment", null, IconUtil.colorize(AllIcons.Actions.Checked, BLUE_BORDER, false, false)) {
                        override fun actionPerformed(e: AnActionEvent) {
                            ReviewManagerService.getInstance(project).markCommentResolved(comment.id)
                        }
                    })
                    add(object : AnAction("Delete comment", null, IconUtil.colorize(AllIcons.General.Remove, DELETE_RED, false, false)) {
                        override fun actionPerformed(e: AnActionEvent) {
                            ReviewManagerService.getInstance(project).deleteComment(comment.id)
                        }
                    })
                }
                val menu = ActionManager.getInstance().createActionPopupMenu(COMMENT_ACTIONS_PLACE, group).component
                menu.show(this, 0, height)
                SwingUtilities.invokeLater { stylePopupMenu(menu) }
            }
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JLabel(lineLabel).apply { foreground = JBColor.GRAY }, BorderLayout.WEST)
            add(menuButton, BorderLayout.EAST)
        }

        body = JBTextArea(comment.body).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder()
            background = normalBackground
            foreground = editor.colorsScheme.defaultForeground
            cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
        }

        val saveButton = JButton("Save").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                val updatedBody = body.text.trim()
                if (updatedBody.isNotEmpty()) {
                    ReviewManagerService.getInstance(project).updateComment(comment.id, updatedBody, comment.status)
                    body.text = comment.body
                }
                exitEditMode()
            }
        }
        installSubmitShortcut(body) { saveButton.doClick() }
        val cancelButton = JButton("Cancel").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                body.text = comment.body
                exitEditMode()
            }
        }
        editActionsPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            isVisible = false
            add(cancelButton)
            add(saveButton)
        }

        add(header, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        add(editActionsPanel, BorderLayout.SOUTH)
        body.background = normalBackground
    }

    private fun enterEditMode() {
        editing = true
        body.isEditable = true
        body.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BLUE_BORDER, 1),
            BorderFactory.createEmptyBorder(4, 4, 4, 4),
        )
        editActionsPanel.isVisible = true
        body.background = UIManager.getColor("TextArea.background") ?: normalBackground
        body.requestFocusInWindow()
        body.caretPosition = body.text.length
        revalidate()
    }

    private fun exitEditMode() {
        editing = false
        body.isEditable = false
        body.border = BorderFactory.createEmptyBorder()
        editActionsPanel.isVisible = false
        body.background = normalBackground
        revalidate()
    }

    private fun stylePopupMenu(menu: JPopupMenu) {
        val defaultBg = UIManager.getColor("PopupMenu.background") ?: menu.background
        menu.subElements.mapNotNull { it.component as? JComponent }.forEach { component ->
            component.isOpaque = true
            component.background = defaultBg
            if (component is javax.swing.JMenuItem) {
                component.iconTextGap = 8
                if (component.text == "Delete comment") {
                    component.foreground = DELETE_RED
                }
            }
            component.addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    component.background = MENU_HOVER_BG
                }

                override fun mouseExited(e: MouseEvent) {
                    component.background = defaultBg
                }
            })
        }
    }
}

private class RoundedLineBorder(
    private val color: Color,
    private val arc: Int,
) : AbstractBorder() {
    override fun paintBorder(c: java.awt.Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
        } finally {
            g2.dispose()
        }
    }

    override fun getBorderInsets(c: java.awt.Component?) = java.awt.Insets(1, 1, 1, 1)

    override fun getBorderInsets(c: java.awt.Component?, insets: java.awt.Insets): java.awt.Insets {
        insets.set(1, 1, 1, 1)
        return insets
    }
}

private fun installSubmitShortcut(textArea: JBTextArea, submit: () -> Unit) {
    val actionKey = "localReview.submitComment"
    textArea.inputMap.put(commentSubmitKeyStroke(), actionKey)
    textArea.actionMap.put(actionKey, object : AbstractAction() {
        override fun actionPerformed(e: java.awt.event.ActionEvent?) {
            submit()
        }
    })
}

private fun commentSubmitKeyStroke(): KeyStroke = KeyStroke.getKeyStroke(
    KeyEvent.VK_ENTER,
    Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx,
)
