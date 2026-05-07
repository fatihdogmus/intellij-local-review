package dev.agentreview.intellij.export

import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection
import java.nio.file.Files

object ExportUiSupport {
    fun saveText(project: Project, fileName: String, extension: String, content: String) {
        val descriptor = FileSaverDescriptor("Export Review", "Choose export target", extension)
        val wrapper = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val target = wrapper.save(project.baseDir, fileName) ?: return
        Files.writeString(target.file.toPath(), content)
    }

    fun copyToClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
}
