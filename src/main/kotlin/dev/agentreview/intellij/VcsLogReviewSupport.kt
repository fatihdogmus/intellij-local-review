package dev.agentreview.intellij

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.impl.VcsProjectLog

private const val LOCAL_REVIEW_NOTIFICATION_GROUP = "Local Review"

object VcsLogReviewSupport {
    fun openLogAndPromptSelection(project: Project) {
        VcsProjectLog.runInMainLog(project) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(LOCAL_REVIEW_NOTIFICATION_GROUP)
                .createNotification(
                    "Select one or more commits in VCS Log, then run Create Local Review from the toolbar or right-click menu.",
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }
}
