package com.gitlab.idea.actions

import com.gitlab.idea.config.GitLabConfigService
import com.gitlab.idea.toolwindow.GitLabServerDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * 添加GitLab仓库操作
 */
class AddServerAction : AnAction("添加GitLab仓库", "添加GitLab仓库并获取MR", null) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 显示添加仓库对话框
        val dialog = GitLabServerDialog(project)
        if (dialog.showAndGet()) {
            val server = dialog.getServer()
            if (server != null) {
                // 保存配置
                val configService = GitLabConfigService.getInstance()
                configService.addServer(server)
                configService.setSelectedServer(server.id)

                // 显示成功消息并刷新工具窗口
                com.gitlab.idea.util.GitLabNotifications.showSuccess(
                    project,
                    "添加成功",
                    "已添加GitLab服务器: ${server.url}"
                )

                // 触发工具窗口刷新
                val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                val toolWindow = toolWindowManager.getToolWindow("GitLab")

                toolWindow?.contentManager?.let { contentManager ->
                    val content = contentManager.selectedContent
                    val component = content?.component
                    val toolWindowContent = component?.getClientProperty("gitlab.toolwindow.content") as? com.gitlab.idea.toolwindow.GitLabToolWindowContent

                    toolWindowContent?.initialize()
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
