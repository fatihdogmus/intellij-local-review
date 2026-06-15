package dev.fatihdogmus.agenticreview.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTextArea
import com.intellij.util.IconUtil
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.diff.ReviewDiffRequestData
import dev.fatihdogmus.agenticreview.model.ReviewComment
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.AbstractBorder

private const val PLACEHOLDER_TEXT = "Add comment"
private const val COMMENT_ACTIONS_PLACE = "AgenticReview.CommentActions"
private const val COMMENT_CARD_ARC = 18
private val BLUE_BORDER =
    UIManager.getColor("Component.focusColor") ?: JBColor(Color(0x35, 0x7A, 0xB8), Color(0x6C, 0xB8, 0xFF))
private val COMMENT_BORDER = UIManager.getColor("Component.borderColor") ?: JBColor.border()
private val DELETE_RED = JBColor(Color(0xC4, 0x2B, 0x1C), Color(0xFF, 0x8E, 0x8A))
private val MENU_HOVER_BG =
    UIManager.getColor("List.selectionBackground") ?: JBColor(Color(0xE8, 0xF1, 0xFF), Color(0x2C, 0x3D, 0x55))

fun showReviewCommentInlays(
    project: Project,
    editor: EditorEx,
    requestData: ReviewDiffRequestData,
): List<Inlay<*>> {
    val manager = ReviewManagerService.getInstance(project)
    return manager.commentsForFile(requestData.reviewId, requestData.changedFile.filePath)
        .mapNotNull { comment ->
            val line = (comment.anchor.newLine ?: comment.anchor.oldLine)?.minus(1) ?: return@mapNotNull null
            showExistingCommentInlay(project, editor, comment, line)
        }
}

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
    val offset =
        if (editor.document.lineCount > 0) editor.document.getLineEndOffset(safeLine) else editor.document.textLength
    return editor.addComponentInlay(
        offset,
        InlayProperties()
            .relatesToPrecedingText(true)
            .priority(0),
        component,
        ComponentInlayAlignment.FIT_VIEWPORT_WIDTH,
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

        val scrollPane = JScrollPane(textArea).apply {
            border = BorderFactory.createLineBorder(BLUE_BORDER, 1)
            preferredSize = Dimension(400, 80)
            isOpaque = false
            viewport.isOpaque = false
        }

        val hintText = if (endLine != null && endLine > startLine) {
            "Commenting on lines ${startLine + 1}-${endLine + 1}"
        } else {
            "Commenting on line ${startLine + 1}"
        }
        val hintLabel = JLabel(hintText).apply {
            foreground = JBColor.GRAY
        }

        val cancelButton = JButton("Cancel").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { dismiss() }
        }
        val commentButton = JButton("Comment").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                val body = textArea.text.trim()
                if (body.isNotEmpty() && body != PLACEHOLDER_TEXT) {
                    val comment = ReviewManagerService.getInstance(project).addComment(
                        requestData.reviewId,
                        requestData.changedFile,
                        requestData.commentSide,
                        startLine + 1,
                        body,
                        endLineNumber = endLine?.let { it + 1 },
                    )
                    dismiss()
                    if (comment != null) {
                        showExistingCommentInlay(project, editor, comment, startLine)
                    }
                } else {
                    dismiss()
                }
            }
        }
        installSubmitShortcut(textArea) { commentButton.doClick() }

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

private fun showExistingCommentInlay(
    project: Project,
    editor: EditorEx,
    comment: ReviewComment,
    line: Int,
): Inlay<*>? {
    val panel = ExistingCommentPanel(project, editor, comment)
    val inlay = insertInlineComponent(editor, line, panel)
    panel.inlayRef = inlay
    return inlay
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
                    add(object : AnAction(
                        "Edit comment",
                        null,
                        IconUtil.colorize(AllIcons.Actions.Edit, BLUE_BORDER, false, false)
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            enterEditMode()
                        }
                    })
                    add(object : AnAction(
                        "Resolve comment",
                        null,
                        IconUtil.colorize(AllIcons.Actions.Checked, BLUE_BORDER, false, false)
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            resolveCommentAndDismiss(project, comment.id, ::dismiss)
                        }
                    })
                    add(object : AnAction(
                        "Delete comment",
                        null,
                        IconUtil.colorize(AllIcons.General.Remove, DELETE_RED, false, false)
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            deleteCommentAndDismiss(project, comment.id, ::dismiss)
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
                    ReviewManagerService.getInstance(project).updateComment(comment.id, updatedBody)
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

    private fun dismiss() {
        inlayRef?.dispose()
        inlayRef = null
    }

    private fun stylePopupMenu(menu: JPopupMenu) {
        val defaultBg = UIManager.getColor("PopupMenu.background") ?: menu.background
        menu.subElements.mapNotNull { it.component as? JComponent }.forEach { component ->
            component.isOpaque = true
            component.background = defaultBg
            if (component is JMenuItem) {
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
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc)
        } finally {
            g2.dispose()
        }
    }

    override fun getBorderInsets(c: Component?) = Insets(1, 1, 1, 1)

    override fun getBorderInsets(c: Component?, insets: Insets): Insets {
        insets.set(1, 1, 1, 1)
        return insets
    }
}

internal fun resolveCommentAndDismiss(project: Project, commentId: String, dismiss: () -> Unit): Boolean {
    val changed = ReviewManagerService.getInstance(project).markCommentResolved(commentId)
    if (changed) dismiss()
    return changed
}

internal fun deleteCommentAndDismiss(project: Project, commentId: String, dismiss: () -> Unit): Boolean {
    val changed = ReviewManagerService.getInstance(project).deleteComment(commentId)
    if (changed) dismiss()
    return changed
}

private fun installSubmitShortcut(textArea: JBTextArea, submit: () -> Unit) {
    val actionKey = "localReview.submitComment"
    textArea.inputMap.put(commentSubmitKeyStroke(), actionKey)
    textArea.actionMap.put(actionKey, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
            submit()
        }
    })
}

private fun commentSubmitKeyStroke(): KeyStroke = KeyStroke.getKeyStroke(
    KeyEvent.VK_ENTER,
    if (GraphicsEnvironment.isHeadless()) InputEvent.CTRL_DOWN_MASK else Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx,
)
