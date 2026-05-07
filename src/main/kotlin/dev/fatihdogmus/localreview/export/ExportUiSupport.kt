package dev.fatihdogmus.localreview.export

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection

object ExportUiSupport {
    private const val COPY_NOTIFICATION_GROUP = "Local Review"

    fun copyToClipboard(project: Project, text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        NotificationGroupManager.getInstance()
            .getNotificationGroup(COPY_NOTIFICATION_GROUP)
            .createNotification("Prompt copied to clipboard", NotificationType.INFORMATION)
            .notify(project)
    }
}
