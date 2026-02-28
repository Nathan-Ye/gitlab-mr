package com.gitlab.idea.config

import com.gitlab.idea.api.GitLabApiClient
import com.gitlab.idea.model.GitLabServer
import com.gitlab.idea.util.GitLabNotifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

/**
 * GitLab应用级配置面板
 * 用于管理全局GitLab服务器配置
 */
class GitLabConfigurable : Configurable {

    private val serverListModel = CollectionListModel<GitLabServer>()
    private val serverList = JBList(serverListModel)
    private val serverNameField = JBTextField()
    private val urlField = JBTextField()
    private val tokenField = JBPasswordField()

    private var modified = false
    private var editingServer: GitLabServer? = null

    override fun getDisplayName(): String = "GitLab"

    override fun createComponent(): JComponent? {
        val panel = JPanel(BorderLayout(0, 10))
        panel.border = JBUI.Borders.empty(10)

        // 左侧服务器列表
        val listPanel = JPanel(BorderLayout())
        listPanel.border = JBUI.Borders.compound(
            JBUI.Borders.empty(5)
        )

        val toolbarDecorator = ToolbarDecorator.createDecorator(serverList)
            .setAddAction { addServer() }
            .setRemoveAction { removeServer() }
            .setEditAction { editServer() }
            .disableUpAction()
            .disableDownAction()

        listPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER)

        // 右侧编辑面板
        val editPanel = JPanel()
        editPanel.layout = BoxLayout(editPanel, BoxLayout.Y_AXIS)
        editPanel.border = JBUI.Borders.empty(10)

        // 服务器名称
        val nameLabel = JBLabel("服务器名称:")
        nameLabel.alignmentX = Component.LEFT_ALIGNMENT
        serverNameField.alignmentX = Component.LEFT_ALIGNMENT

        // 项目地址
        val urlLabel = JBLabel("项目地址:")
        urlLabel.alignmentX = Component.LEFT_ALIGNMENT
        urlField.alignmentX = Component.LEFT_ALIGNMENT

        // API Token
        val tokenLabel = JBLabel("API Token:")
        tokenLabel.alignmentX = Component.LEFT_ALIGNMENT
        tokenField.alignmentX = Component.LEFT_ALIGNMENT

        // 按钮
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val testButton = JButton("测试连接")
        val saveButton = JButton("保存")
        val cancelButton = JButton("取消")

        testButton.addActionListener { testConnection() }
        saveButton.addActionListener { saveEdit() }
        cancelButton.addActionListener { cancelEdit() }

        buttonPanel.add(testButton)
        buttonPanel.add(Box.createHorizontalStrut(10))
        buttonPanel.add(saveButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(cancelButton)

        editPanel.add(nameLabel)
        editPanel.add(Box.createVerticalStrut(5))
        editPanel.add(serverNameField)
        editPanel.add(Box.createVerticalStrut(10))
        editPanel.add(urlLabel)
        editPanel.add(Box.createVerticalStrut(5))
        editPanel.add(urlField)
        editPanel.add(Box.createVerticalStrut(10))
        editPanel.add(tokenLabel)
        editPanel.add(Box.createVerticalStrut(5))
        editPanel.add(tokenField)
        editPanel.add(Box.createVerticalStrut(15))
        editPanel.add(buttonPanel)
        editPanel.add(Box.createVerticalGlue())

        // 添加到主面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, editPanel)
        splitPane.resizeWeight = 0.4
        panel.add(splitPane, BorderLayout.CENTER)

        // 设置选择监听
        serverList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                onServerSelected()
            }
        }

        // 初始禁用编辑
        setEditFieldsEnabled(false)

        return panel
    }

    override fun isModified(): Boolean = modified

    override fun apply() {
        // 配置已经在添加/编辑时保存
        modified = false
    }

    override fun reset() {
        loadServers()
        modified = false
    }

    private fun loadServers() {
        val configService = GitLabConfigService.getInstance()
        val servers = configService.getAllServers()

        serverListModel.removeAll()
        servers.forEach { serverListModel.add(it) }
    }

    private fun onServerSelected() {
        val selected = serverList.selectedValue ?: return

        serverNameField.text = selected.name
        urlField.text = selected.url
        tokenField.text = selected.token

        editingServer = selected
        setEditFieldsEnabled(true)
    }

    private fun addServer() {
        serverNameField.text = ""
        urlField.text = "https://gitlab.com"
        tokenField.text = ""
        editingServer = null
        setEditFieldsEnabled(true)
        serverNameField.requestFocus()
    }

    private fun editServer() {
        // 选择时已经填充，不需要额外操作
    }

    private fun removeServer() {
        val selected = serverList.selectedValue ?: return

        val result = JOptionPane.showConfirmDialog(
            null,
            "确定要删除服务器 \"${selected.name}\" 吗？",
            "确认删除",
            JOptionPane.YES_NO_OPTION
        )

        if (result == JOptionPane.YES_OPTION) {
            val configService = GitLabConfigService.getInstance()
            configService.removeServer(selected.id)
            serverListModel.remove(selected)
            modified = true
        }
    }

    private fun saveEdit() {
        val name = serverNameField.text.trim()
        val url = urlField.text.trim()
        val token = tokenField.password.joinToString("").trim()

        if (name.isEmpty() || url.isEmpty() || token.isEmpty()) {
            GitLabNotifications.showError(null, "验证失败", "请填写所有字段")
            return
        }

        val configService = GitLabConfigService.getInstance()

        if (editingServer != null) {
            // 更新现有服务器
            editingServer!!.name = name
            editingServer!!.url = url
            editingServer!!.token = token
            configService.updateServer(editingServer!!)
            serverList.repaint()
        } else {
            // 添加新服务器
            val server = GitLabServer(
                id = GitLabServer.generateId(),
                name = name,
                url = url,
                token = token,
                isDefault = false
            )
            configService.addServer(server)
            serverListModel.add(server)
        }

        modified = true
        cancelEdit()
    }

    private fun cancelEdit() {
        editingServer = null
        setEditFieldsEnabled(false)
        serverList.clearSelection()
    }

    private fun testConnection() {
        val url = urlField.text.trim()
        val token = tokenField.password.joinToString("").trim()

        if (url.isEmpty() || token.isEmpty()) {
            GitLabNotifications.showError(null, "验证失败", "请先填写URL和Token")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "测试GitLab连接", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在连接..."

                try {
                    val apiClient = GitLabApiClient(url, token, null)
                    val result = runBlocking { apiClient.testConnection() }

                    ApplicationManager.getApplication().invokeLater {
                        if (result.success) {
                            val name = result.data?.get("name") as? String ?: ""
                            val username = result.data?.get("username") as? String ?: ""
                            GitLabNotifications.showSuccess(
                                null,
                                "连接成功",
                                "欢迎用户: $name (@$username)"
                            )
                        } else {
                            GitLabNotifications.showError(
                                null,
                                "连接失败",
                                result.error ?: "未知错误"
                            )
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(null, "连接失败", e.message ?: "无法连接到GitLab")
                    }
                }
            }
        })
    }

    private fun setEditFieldsEnabled(enabled: Boolean) {
        serverNameField.isEnabled = enabled
        urlField.isEnabled = enabled
        tokenField.isEnabled = enabled
    }
}
