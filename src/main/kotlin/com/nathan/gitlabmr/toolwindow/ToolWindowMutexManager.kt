package com.nathan.gitlabmr.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.messages.MessageBusConnection

/**
 * 工具窗口互斥管理器
 * 仅在底部工具窗口区域内保持互斥，避免影响侧边栏窗口。
 */
class ToolWindowMutexManager(private val project: Project) : Disposable {

    companion object {
        private const val GITLAB_TOOL_WINDOW_ID = "GitLab"

        fun getInstance(project: Project): ToolWindowMutexManager {
            return ToolWindowMutexManager(project)
        }
    }

    private val toolWindowManager: ToolWindowManager = ToolWindowManager.getInstance(project)
    private val connection: MessageBusConnection = project.messageBus.connect()
    private var isProcessing = false

    init {
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(toolWindow: ToolWindow) {
                handleToolWindowShown(toolWindow)
            }
        })
    }

    private fun handleToolWindowShown(toolWindow: ToolWindow) {
        if (isProcessing) return
        isProcessing = true

        try {
            val toolWindowId = toolWindow.id
            val toolWindowAnchor = toolWindow.anchor

            if (toolWindowId == GITLAB_TOOL_WINDOW_ID) {
                closeBottomToolWindowsExceptGitLab()
            } else if (toolWindowAnchor == ToolWindowAnchor.BOTTOM) {
                closeGitLabToolWindow()
            }
        } finally {
            isProcessing = false
        }
    }

    private fun closeBottomToolWindowsExceptGitLab() {
        toolWindowManager.toolWindowIds.forEach { id ->
            if (id != GITLAB_TOOL_WINDOW_ID) {
                val toolWindow = toolWindowManager.getToolWindow(id)
                if (toolWindow != null && toolWindow.isVisible && toolWindow.anchor == ToolWindowAnchor.BOTTOM) {
                    toolWindow.hide()
                }
            }
        }
    }

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
