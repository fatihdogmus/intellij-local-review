package dev.agentreview.intellij.export

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

object ExportUiSupport {

    fun copyToClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
}
