package com.gitlab.idea.config

import com.gitlab.idea.model.GitLabServer
import com.intellij.openapi.options.Configurable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

/**
 * GitLab项目级配置面板
 * 用于为特定项目配置GitLab服务器
 */
class GitLabProjectConfigurable : Configurable {

    private val serverListModel = CollectionListModel<GitLabServer>()
    private val serverList = JBList(serverListModel)

    override fun getDisplayName(): String = "GitLab"

    override fun createComponent(): JComponent? {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)

        val description = JBLabel("""
            <html>
            <p>在此配置当前项目的GitLab服务器。项目级配置会覆盖全局配置。</p>
            <p>请先在全局设置中添加GitLab服务器。</p>
            </html>
        """.trimIndent())

        val listPanel = JPanel(BorderLayout())
        listPanel.border = JBUI.Borders.emptyTop(10)

        val toolbarDecorator = ToolbarDecorator.createDecorator(serverList)
            .setAddAction { selectFromGlobal() }
            .setRemoveAction { removeServer() }

        listPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER)

        panel.add(description, BorderLayout.NORTH)
        panel.add(listPanel, BorderLayout.CENTER)

        return panel
    }

    override fun isModified(): Boolean = false

    override fun apply() {
        // 项目级配置暂未实现
    }

    private fun selectFromGlobal() {
        JOptionPane.showMessageDialog(
            null,
            "请先在全局设置中添加GitLab服务器",
            "提示",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun removeServer() {
        // 移除项目级配置
    }
}
