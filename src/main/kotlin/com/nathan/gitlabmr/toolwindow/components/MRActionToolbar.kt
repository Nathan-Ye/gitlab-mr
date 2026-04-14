package com.nathan.gitlabmr.toolwindow.components

import com.nathan.gitlabmr.model.GitLabMergeRequest
import com.nathan.gitlabmr.model.MergeRequestState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.JPanel

/**
 * MR操作工具栏
 * 包含在GitLab中打开、关闭、合并、删除等操作按钮。
 */
class MRActionToolbar : JPanel() {

    var onRefreshMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onCloseMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onMergeMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onDeleteMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onExpandAllChangesClicked: (() -> Unit)? = null
    var onCollapseAllChangesClicked: (() -> Unit)? = null
    var currentServerUrl: String? = null

    private var currentMR: GitLabMergeRequest? = null
    private var changeActionsVisible: Boolean = false
    private var isRefreshing: Boolean = false

    init {
        layout = BorderLayout()
        isOpaque = false
        border = JBUI.Borders.empty(8, 8, 0, 8)

        val leftActionGroup = DefaultActionGroup().apply {
            actionList.forEach { addAction(it) }
        }
        val leftToolbar = ActionManager.getInstance().createActionToolbar(
            "GitLabMRActionToolbar",
            leftActionGroup,
            true
        )
        leftToolbar.targetComponent = null
        leftToolbar.component.isOpaque = false

        val rightActionGroup = DefaultActionGroup().apply {
            addAction(ExpandAllChangesAction())
            addAction(CollapseAllChangesAction())
        }
        val rightToolbar = ActionManager.getInstance().createActionToolbar(
            "GitLabMRChangesToolbar",
            rightActionGroup,
            true
        )
        rightToolbar.targetComponent = null
        rightToolbar.component.isOpaque = false

        add(leftToolbar.component, BorderLayout.WEST)
        add(rightToolbar.component, BorderLayout.EAST)
    }

    private val actionList: List<AnAction>
        get() = listOf(
            CloseMRAction(),
            MergeMRAction(),
            DeleteMRAction(),
            Separator.create(),
            RefreshMRAction(),
            OpenInBrowserMRAction()
        )

    fun updateButtonStates(mr: GitLabMergeRequest?) {
        currentMR = mr
    }

    fun setChangeActionsVisible(visible: Boolean) {
        changeActionsVisible = visible
    }

    fun setRefreshing(refreshing: Boolean) {
        isRefreshing = refreshing
    }

    private inner class RefreshMRAction : AnAction(
        "刷新",
        "刷新当前合并请求",
        AllIcons.Actions.Refresh
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            currentMR?.let { onRefreshMRClicked?.invoke(it) }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = currentMR != null && !isRefreshing
        }
    }

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
            e.presentation.isEnabled = !isRefreshing && (
                mr?.state == MergeRequestState.OPENED || mr?.state == MergeRequestState.LOCKED
            )
        }
    }

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
            e.presentation.isEnabled = !isRefreshing && mr?.state == MergeRequestState.OPENED
        }
    }

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
            e.presentation.isEnabled = !isRefreshing && (
                mr?.state == MergeRequestState.OPENED || mr?.state == MergeRequestState.CLOSED
            )
        }
    }

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
                if (!serverUrl.isNullOrEmpty() && webUrl.isNotEmpty()) {
                    try {
                        val serverUri = URI(serverUrl)
                        val webUri = URI(webUrl)
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
                    } catch (_: Exception) {
                        // Ignore browser open failures.
                    }
                }
            }
        }

        override fun update(e: AnActionEvent) {
            val mr = currentMR
            e.presentation.isEnabled = !isRefreshing && mr != null && currentServerUrl != null && mr.webUrl.isNotEmpty()
        }
    }

    private inner class ExpandAllChangesAction : AnAction(
        "全部展开",
        "全部展开",
        AllIcons.Actions.Expandall
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            onExpandAllChangesClicked?.invoke()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = changeActionsVisible
            e.presentation.isEnabled = changeActionsVisible
        }
    }

    private inner class CollapseAllChangesAction : AnAction(
        "全部收起",
        "全部收起",
        AllIcons.Actions.Collapseall
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            onCollapseAllChangesClicked?.invoke()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isVisible = changeActionsVisible
            e.presentation.isEnabled = changeActionsVisible
        }
    }
}
