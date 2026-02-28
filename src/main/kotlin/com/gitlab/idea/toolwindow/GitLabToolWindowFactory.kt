package com.gitlab.idea.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * GitLab工具窗口工厂
 * 负责创建和管理GitLab工具窗口
 */
class GitLabToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 设置工具窗口显示名称
        toolWindow.stripeTitle = "GitLab MR"

        // 初始化工具窗口互斥管理器
        ToolWindowMutexManager.getInstance(project)

        val contentFactory = ContentFactory.getInstance()
        val toolWindowContent = GitLabToolWindowContent(project, toolWindow)

        val content = contentFactory.createContent(
            toolWindowContent.getContent(),
            "",
            false
        )

        // 存储 GitLabToolWindowContent 实例到 component 的客户端属性
        content.component.putClientProperty("gitlab.toolwindow.content", toolWindowContent)

        // 设置内容
        toolWindow.contentManager.addContent(content)

        // 确保工具窗口可用并自动显示
        toolWindow.isAvailable = true
        toolWindow.show(null)

        // 初始化加载
        toolWindowContent.initialize()
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        // 总是显示工具窗口，即使没有Git仓库
        return true
    }
}
