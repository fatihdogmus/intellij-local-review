package dev.agentreview.intellij.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Disposer
import dev.agentreview.intellij.model.DiffSide
import dev.agentreview.intellij.ui.showInlineCommentForm
import dev.agentreview.intellij.ui.showReviewCommentInlays
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

val REVIEW_DIFF_EDITOR_KEY = Key.create<ReviewDiffRequestData>("local.review.diff.editor.data")

private val GREEN = Color(0x2E, 0xA4, 0x4F)
private val HOVER_BG = Color(0x2E, 0xA4, 0x4F, 18)
private const val ICON_SIZE = 16
private const val ICON_PAD = 4
private const val ICON_ARC = 6

class ReviewDiffExtension : DiffExtension() {
    override fun onViewerCreated(
        viewer: FrameDiffTool.DiffViewer,
        context: DiffContext,
        request: DiffRequest,
    ) {
        val project = context.project ?: return
        val requestData = request.getUserData(REVIEW_DIFF_REQUEST_DATA_KEY) ?: return

        val commentEditor = when (viewer) {
            is TwosideTextDiffViewer -> when (requestData.commentSide) {
                DiffSide.LEFT -> viewer.editor1
                DiffSide.RIGHT -> viewer.editor2
            }
            is OnesideTextDiffViewer -> viewer.editor
            else -> return
        }

        commentEditor.putUserData(REVIEW_DIFF_EDITOR_KEY, requestData)

        val allEditors = when (viewer) {
            is TwosideTextDiffViewer -> listOf(viewer.editor1, viewer.editor2)
            is OnesideTextDiffViewer -> listOf(viewer.editor)
            else -> return
        }
        allEditors.forEach { it.putUserData(REVIEW_DIFF_EDITOR_KEY, requestData) }

        val renderer = CommentLineMarkerRenderer(project, commentEditor, requestData)
        val commentInlays = showReviewCommentInlays(project, commentEditor, requestData)
        val gutterHighlighter = commentEditor.markupModel.addRangeHighlighter(
            null, 0, commentEditor.document.textLength,
            HighlighterLayer.LAST,
            HighlighterTargetArea.LINES_IN_RANGE,
        ).apply {
            setGreedyToLeft(true)
            setGreedyToRight(true)
            setLineMarkerRenderer(renderer)
        }

        viewer.addListener(object : DiffViewerListener() {
            override fun onDispose() {
                commentInlays.forEach(Disposer::dispose)
                commentEditor.markupModel.removeHighlighter(gutterHighlighter)
                renderer.dispose()
            }
        })
    }
}

private class CommentLineMarkerRenderer(
    private val project: com.intellij.openapi.project.Project,
    private val editor: EditorEx,
    private val requestData: ReviewDiffRequestData,
) : LineMarkerRendererEx, ActiveGutterRenderer {

    @Volatile
    private var hoveredLine: Int = -1
    private var selectedStartLine: Int = -1
    private var selectedEndLine: Int = -1
    private var bgHighlighter: RangeHighlighter? = null
    private var multiLineHighlighter: RangeHighlighter? = null

    private val motionListener = object : EditorMouseMotionListener {
        override fun mouseMoved(e: EditorMouseEvent) {
            val newLine = e.logicalPosition.line.coerceIn(0, editor.document.lineCount - 1)
            if (newLine != hoveredLine) {
                val prev = hoveredLine
                hoveredLine = newLine
                updateBackgroundHighlight(newLine)
                if (prev >= 0) repaintLineGutter(prev)
                repaintLineGutter(newLine)
            }
        }
    }.also { editor.addEditorMouseMotionListener(it) }

    private val selListener = object : SelectionListener {
        override fun selectionChanged(e: SelectionEvent) {
            val range = e.newRange ?: return
            if (range.isEmpty) {
                val prev = selectedStartLine
                selectedStartLine = -1
                selectedEndLine = -1
                updateMultiLineHighlight(-1, -1)
                if (prev >= 0) repaintLineGutter(prev)
                return
            }
            val startLine = editor.offsetToLogicalPosition(range.startOffset).line
            val endLine = editor.offsetToLogicalPosition(range.endOffset).line
            if (startLine >= endLine) {
                selectedStartLine = -1
                selectedEndLine = -1
                updateMultiLineHighlight(-1, -1)
                return
            }
            val prev = selectedStartLine
            selectedStartLine = startLine
            selectedEndLine = endLine
            updateMultiLineHighlight(startLine, endLine)
            if (prev >= 0 && prev != startLine) repaintLineGutter(prev)
            repaintLineGutter(startLine)
        }
    }.also { editor.selectionModel.addSelectionListener(it) }

    fun dispose() {
        editor.removeEditorMouseMotionListener(motionListener)
        editor.selectionModel.removeSelectionListener(selListener)
        bgHighlighter?.dispose()
        bgHighlighter = null
        multiLineHighlighter?.dispose()
        multiLineHighlighter = null
    }

    private fun updateBackgroundHighlight(line: Int) {
        bgHighlighter?.dispose()
        bgHighlighter = null
        if (line < 0 || line >= editor.document.lineCount) return
        val startOffset = editor.document.getLineStartOffset(line)
        val endOffset = editor.document.getLineEndOffset(line)
        val attrs = TextAttributes(null, HOVER_BG, null, null, Font.PLAIN)
        bgHighlighter = editor.markupModel.addRangeHighlighter(
            startOffset, endOffset,
            HighlighterLayer.SELECTION - 1,
            attrs,
            HighlighterTargetArea.LINES_IN_RANGE,
        )
    }

    private fun updateMultiLineHighlight(startLine: Int, endLine: Int) {
        multiLineHighlighter?.dispose()
        multiLineHighlighter = null
        if (startLine < 0 || endLine <= startLine) return
        val startOffset = editor.document.getLineStartOffset(startLine)
        val endOffset = editor.document.getLineEndOffset(endLine)
        val attrs = TextAttributes(null, HOVER_BG, null, null, Font.PLAIN)
        multiLineHighlighter = editor.markupModel.addRangeHighlighter(
            startOffset, endOffset,
            HighlighterLayer.SELECTION - 1,
            attrs,
            HighlighterTargetArea.LINES_IN_RANGE,
        )
    }

    private fun repaintLineGutter(line: Int) {
        if (line < 0 || line >= editor.document.lineCount) return
        val startOffset = editor.document.getLineStartOffset(line)
        val y = editor.logicalPositionToXY(editor.offsetToLogicalPosition(startOffset)).y
        editor.gutterComponentEx.repaint(0, y, editor.gutterComponentEx.width, editor.lineHeight)
    }

    private fun activeLine(): Int {
        if (selectedStartLine in 0 until editor.document.lineCount) return selectedStartLine
        return hoveredLine
    }

    override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.RIGHT

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        val line = activeLine()
        if (line < 0 || line >= editor.document.lineCount) return

        val lineHeight = editor.lineHeight
        val lineStartOffset = editor.document.getLineStartOffset(line)
        val y = editor.logicalPositionToXY(editor.offsetToLogicalPosition(lineStartOffset)).y
        val cx = r.x + 2
        val cy = y + (lineHeight - ICON_SIZE) / 2

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g2d.color = GREEN
        g2d.fillRoundRect(cx, cy, ICON_SIZE, ICON_SIZE, ICON_ARC, ICON_ARC)

        g2d.color = Color.WHITE
        g2d.stroke = BasicStroke(1.8f)
        val mid = ICON_SIZE / 2
        g2d.drawLine(cx + ICON_PAD, cy + mid, cx + ICON_SIZE - ICON_PAD, cy + mid)
        g2d.drawLine(cx + mid, cy + ICON_PAD, cx + mid, cy + ICON_SIZE - ICON_PAD)
    }

    override fun doAction(editor: Editor, e: MouseEvent) {
        val editorPoint = SwingUtilities.convertPoint(e.component, e.point, editor.contentComponent)
        val logicalLine = editor.xyToLogicalPosition(editorPoint).line
        if (logicalLine < 0 || logicalLine >= editor.document.lineCount) return
        val endLine = if (selectedStartLine >= 0 && selectedEndLine > selectedStartLine) selectedEndLine else null
        showInlineCommentForm(project, editor as EditorEx, logicalLine, requestData, endLine)
    }

    override fun canDoAction(editor: Editor, e: MouseEvent): Boolean = true

    override fun getTooltipText(): String = "Add review comment"

    override fun getAccessibleName(): String = "Add review comment"
}
