package dev.fatihdogmus.agenticreview.export

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor

@TestApplication
class ExportUiSupportTest {
    private val project by projectFixture()

    @Test
    fun copyToClipboardSetsSystemClipboardContents() {
        ExportUiSupport.copyToClipboard(project, "test prompt content")

        val clipboardText = CopyPasteManager.getInstance().contents
            ?.getTransferData(DataFlavor.stringFlavor) as? String
        assertThat(clipboardText).isEqualTo("test prompt content")
    }

    @Test
    fun copyToClipboardWithEmptyText() {
        ExportUiSupport.copyToClipboard(project, "")

        val clipboardText = CopyPasteManager.getInstance().contents
            ?.getTransferData(DataFlavor.stringFlavor) as? String
        assertThat(clipboardText).isEmpty()
    }

    @Test
    fun copyToClipboardDoesNotThrow() {
        ExportUiSupport.copyToClipboard(project, "safe")
    }
}
