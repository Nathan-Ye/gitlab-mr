package com.gitlab.idea.toolwindow.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.Box
import javax.swing.JPanel

/**
 * GitLab工具窗口侧边工具栏
 * 提供设置和刷新按钮，使用IDEA原生的ActionButton样式
 */
class ToolWindowSideToolbar : JPanel() {

    var onSettingsClicked: (() -> Unit)? = null
    var onRefreshClicked: (() -> Unit)? = null
    var onCreateMRClicked: (() -> Unit)? = null

    init {
        layout = BorderLayout()
        background = UIUtil.getPanelBackground()
        preferredSize = Dimension(32, -1)

        // 创建Action列表（按顺序：设置、刷新、分割线、创建MR）
        val actionGroup = DefaultActionGroup()
        actionGroup.add(SettingsAction())
        actionGroup.add(RefreshAction())
        actionGroup.add(Separator.getInstance())
        actionGroup.add(CreateMRAction())

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "GitLabSideToolbar",
            actionGroup,
            false // horizontal = false for vertical layout
        )
        toolbar.targetComponent = null

        // 包装工具栏组件，设置顶部边距
        val toolbarWrapper = JPanel()
        toolbarWrapper.layout = BoxLayout(toolbarWrapper, BoxLayout.Y_AXIS)
        toolbarWrapper.background = UIUtil.getPanelBackground()
        toolbarWrapper.border = JBUI.Borders.emptyTop(8)
        toolbar.component.alignmentX = CENTER_ALIGNMENT
        toolbarWrapper.add(toolbar.component)

        add(toolbarWrapper, BorderLayout.NORTH)
        // 底部弹性空间
        add(Box.createVerticalGlue(), BorderLayout.CENTER)
    }

    /**
     * 设置Action - 使用IDEA原生图标和样式
     */
    private inner class SettingsAction : AnAction(
        "编辑GitLab服务器配置",
        "编辑GitLab服务器配置",
        AllIcons.General.Settings
    ) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            onSettingsClicked?.invoke()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = true
        }
    }

    /**
     * 刷新Action - 使用IDEA原生图标和样式
     */
    private inner class RefreshAction : AnAction(
        "刷新数据",
        "刷新GitLab数据",
        AllIcons.Actions.Refresh
    ) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            onRefreshClicked?.invoke()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = true
        }
    }

    /**
     * 创建MR Action - 使用IDEA原生图标
     */
    private inner class CreateMRAction : AnAction(
        "创建合并请求",
        "创建新的GitLab合并请求",
        AllIcons.General.Add
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            onCreateMRClicked?.invoke()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = true
        }
    }
}
