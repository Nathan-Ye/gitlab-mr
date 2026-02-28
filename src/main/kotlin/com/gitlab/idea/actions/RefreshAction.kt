package com.gitlab.idea.actions

import com.gitlab.idea.toolwindow.GitLabToolWindowContent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 刷新GitLab数据操作
 */
class RefreshAction : AnAction("Refresh", "Refresh GitLab data", null) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 获取工具窗口并刷新
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("GitLab")

        toolWindow?.contentManager?.let { contentManager ->
            val content = contentManager.selectedContent
            // 尝试从组件的用户数据中获取 GitLabToolWindowContent
            val component = content?.component
            val toolWindowContent = component?.getClientProperty("gitlab.toolwindow.content") as? GitLabToolWindowContent

            toolWindowContent?.initialize()
        }
    }

    override fun update(e: AnActionEvent) {
        // 只在有项目时启用
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
