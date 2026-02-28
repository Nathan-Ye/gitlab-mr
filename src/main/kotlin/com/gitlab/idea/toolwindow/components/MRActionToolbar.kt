package com.gitlab.idea.toolwindow.components

import com.gitlab.idea.model.GitLabMergeRequest
import com.gitlab.idea.model.MergeRequestState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * MR操作工具栏
 * 包含关闭、合并、删除三个操作按钮
 */
class MRActionToolbar : JPanel() {

    var onCloseMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onMergeMRClicked: ((GitLabMergeRequest) -> Unit)? = null
    var onDeleteMRClicked: ((GitLabMergeRequest) -> Unit)? = null

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
            DeleteMRAction()
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
}
