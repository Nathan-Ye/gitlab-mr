package com.gitlab.idea.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * GitLab通知工具类
 * 用于在IDEA中显示各种通知消息
 */
object GitLabNotifications {

    private const val NOTIFICATION_GROUP_ID = "GitLab.Notification.Group"

    /**
     * 显示信息通知
     */
    fun showInfo(project: Project? = null, title: String, content: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, NotificationType.INFORMATION)

        notification?.notify(project)
    }

    /**
     * 显示错误通知
     */
    fun showError(project: Project? = null, title: String, content: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, NotificationType.ERROR)

        notification?.notify(project)
    }

    /**
     * 显示警告通知
     */
    fun showWarning(project: Project? = null, title: String, content: String) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            ?.createNotification(title, content, NotificationType.WARNING)

        notification?.notify(project)
    }

    /**
     * 显示成功通知
     */
    fun showSuccess(project: Project? = null, title: String = "Success", content: String) {
        showInfo(project, title, content)
    }
}
