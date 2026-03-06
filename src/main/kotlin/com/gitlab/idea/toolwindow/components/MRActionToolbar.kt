package com.gitlab.idea.toolwindow.components

import com.gitlab.idea.model.GitLabMergeRequest
import com.gitlab.idea.model.MergeRequestState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.Separator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.JPanel

/**
 * MR操作工具栏
 * 包含在GitLab中打开、关闭、合并、删除操作按钮
 */
class MRActionToolbar : JPanel() {

    var onCloseMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onMergeMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onDeleteMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var currentServerUrl: String? = null

    private var currentMR: GitLabMergeRequest? = null

    init {
        layout = BorderLayout()
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(8, 8, 0, 8) // top, left, bottom, right

        val actionGroup = DefaultActionGroup()
        actionList.forEach { actionGroup.addAction(it) }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "GitLabMRActionToolbar",
            actionGroup,
            true // horizontal = true
        )
        toolbar.targetComponent = null

        add(toolbar.component, BorderLayout.WEST)
    }

    private val actionList: List<AnAction>
        get() = listOf(
            CloseMRAction(),
            MergeMRAction(),
            DeleteMRAction(),
            Separator.create(),
            OpenInBrowserMRAction()
        )

    /**
     * 更新按钮启用状态
     */
    fun updateButtonStates(mr: GitLabMergeRequest?) {
        currentMR = mr
    }

    /**
     * 关闭MR Action
     */
    private inner class CloseMRAction : AnAction(
        "关闭",
        "关闭合并请求",
        AllIcons.Actions.Cancel
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            currentMR?.let { onCloseMRClicked?.invoke(it) }
        }

        override fun update(e: AnActionEvent) {
            val mr = currentMR
            e.presentation.isEnabled = mr?.state == MergeRequestState.OPENED
        }
    }

    /**
     * 合并MR Action
     */
    private inner class MergeMRAction : AnAction(
        "合并",
        "接受并合并此请求",
        AllIcons.Actions.Checked
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            currentMR?.let { onMergeMRClicked?.invoke(it) }
        }

        override fun update(e: AnActionEvent) {
            val mr = currentMR
            e.presentation.isEnabled = mr?.state == MergeRequestState.OPENED
        }
    }

    /**
     * 删除MR Action
     */
    private inner class DeleteMRAction : AnAction(
        "删除",
        "删除合并请求",
        AllIcons.Actions.GC
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            currentMR?.let { onDeleteMRClicked?.invoke(it) }
        }

        override fun update(e: AnActionEvent) {
            val mr = currentMR
            // 仅 OPENED 和 CLOSED 状态可以删除
            e.presentation.isEnabled = mr?.state == MergeRequestState.OPENED
                || mr?.state == MergeRequestState.CLOSED
        }
    }

    /**
     * 在浏览器中打开MR Action
     */
    private inner class OpenInBrowserMRAction : AnAction(
        "在GitLab中打开",
        "在GitLab中打开此合并请求",
        AllIcons.Ide.Link
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            currentMR?.let { mr ->
                val serverUrl = currentServerUrl
                val webUrl = mr.webUrl
                if (serverUrl != null && serverUrl.isNotEmpty() && webUrl.isNotEmpty()) {
                    try {
                        // 使用 URI 解析，避免使用已弃用的 URL 构造函数
                        val serverUri = URI(serverUrl)
                        val webUri = URI(webUrl)

                        // 只替换域名和端口部分，保留路径
                        val newUrl = URI(
                            serverUri.scheme,
                            serverUri.userInfo,
                            serverUri.host,
                            serverUri.port,
                            webUri.path,
                            webUri.query,
                            webUri.fragment
                        )
                        Desktop.getDesktop().browse(newUrl)
                    } catch (ex: Exception) {
                        // 忽略打开浏览器失败的情况
                    }
                }
            }
        }

        override fun update(e: AnActionEvent) {
            val mr = currentMR
            e.presentation.isEnabled = mr != null && currentServerUrl != null && mr.webUrl.isNotEmpty()
        }
    }
}
