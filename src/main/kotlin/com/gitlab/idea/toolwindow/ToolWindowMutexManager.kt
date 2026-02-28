package com.gitlab.idea.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.messages.MessageBusConnection

/**
 * 工具窗口互斥管理器
 * 确保 GitLab 工具窗口与其他工具窗口互斥显示
 */
class ToolWindowMutexManager(private val project: Project) : Disposable {

    companion object {
        private const val GITLAB_TOOL_WINDOW_ID = "GitLab"

        /**
         * 获取或创建管理器实例
         */
        fun getInstance(project: Project): ToolWindowMutexManager {
            return ToolWindowMutexManager(project)
        }
    }

    private val toolWindowManager: ToolWindowManager = ToolWindowManager.getInstance(project)
    private val connection: MessageBusConnection = project.messageBus.connect()
    private var isProcessing = false

    init {
        // 订阅工具窗口状态变化监听器
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(toolWindow: ToolWindow) {
                handleToolWindowShown(toolWindow)
            }
        })
    }

    /**
     * 处理工具窗口显示事件
     */
    private fun handleToolWindowShown(toolWindow: ToolWindow) {
        if (isProcessing) return
        isProcessing = true

        try {
            val toolWindowId = toolWindow.id

            if (toolWindowId == GITLAB_TOOL_WINDOW_ID) {
                // GitLab 工具窗口显示时，关闭其他工具窗口
                closeOtherToolWindows()
            } else {
                // 其他工具窗口显示时，关闭 GitLab 工具窗口
                closeGitLabToolWindow()
            }
        } finally {
            isProcessing = false
        }
    }

    /**
     * 关闭除 GitLab 外的所有工具窗口
     */
    private fun closeOtherToolWindows() {
        toolWindowManager.toolWindowIds.forEach { id ->
            if (id != GITLAB_TOOL_WINDOW_ID) {
                val tw = toolWindowManager.getToolWindow(id)
                if (tw != null && tw.isVisible) {
                    tw.hide()
                }
            }
        }
    }

    /**
     * 关闭 GitLab 工具具窗口
     */
    private fun closeGitLabToolWindow() {
        val gitLabToolWindow = toolWindowManager.getToolWindow(GITLAB_TOOL_WINDOW_ID) ?: return
        if (gitLabToolWindow.isVisible) {
            gitLabToolWindow.hide()
        }
    }

    override fun dispose() {
        connection.disconnect()
    }
}
