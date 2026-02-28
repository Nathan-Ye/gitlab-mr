package com.gitlab.idea.toolwindow

import com.gitlab.idea.api.GitLabApiClient
import com.gitlab.idea.model.GitLabServer
import com.gitlab.idea.util.GitLabNotifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

/**
 * 添加GitLab服务器对话框
 * 用户输入GitLab服务器地址和Token，项目路径自动从Git远程检测
 *
 * @param project IntelliJ项目
 * @param existingServer 要编辑的服务器配置（null表示添加模式）
 */
class GitLabServerDialog(project: Project?, private val existingServer: GitLabServer? = null) : DialogWrapper(project, true) {

    private val projectField: Project? = project
    private val serverUrlField = JBTextField()
    private val tokenField = JBPasswordField()
    private val setAsDefaultCheckbox = JCheckBox("Set as Default Server")

    private val infoArea = JBTextArea().apply {
        isEditable = false
        background = UIUtil.getPanelBackground()
        lineWrap = true
        wrapStyleWord = true
    }

    private var parsedServer: GitLabServer? = null

    init {
        if (existingServer != null) {
            title = "编辑GitLab服务器"
            setOKButtonText("保存")
        } else {
            title = "添加GitLab服务器"
            setOKButtonText("确定")
        }
        setCancelButtonText("取消")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(450, 280)

        // 表单面板
        val formPanel = JPanel()
        formPanel.layout = BoxLayout(formPanel, BoxLayout.Y_AXIS)
        formPanel.alignmentX = Component.LEFT_ALIGNMENT

        // GitLab服务器地址
        val urlLabel = JBLabel("GitLab服务器地址:")
        urlLabel.alignmentX = Component.LEFT_ALIGNMENT
        serverUrlField.alignmentX = Component.LEFT_ALIGNMENT
        serverUrlField.toolTipText = "例如: https://gitlab.com 或 https://your-gitlab.com:8080"

        // API Token
        val tokenLabel = JBLabel("API Token:")
        tokenLabel.alignmentX = Component.LEFT_ALIGNMENT
        tokenField.alignmentX = Component.LEFT_ALIGNMENT
        tokenField.toolTipText = "在GitLab用户设置中生成的个人访问令牌（需要api权限）"

        // Set as Default 复选框 和 测试连接按钮在同一行
        setAsDefaultCheckbox.toolTipText = "勾选后，此服务器配置将作为默认配置，所有项目都可以使用"

        val checkboxButtonPanel = JPanel()
        checkboxButtonPanel.layout = BoxLayout(checkboxButtonPanel, BoxLayout.X_AXIS)
        checkboxButtonPanel.alignmentX = Component.LEFT_ALIGNMENT
        checkboxButtonPanel.add(setAsDefaultCheckbox)
        checkboxButtonPanel.add(Box.createHorizontalGlue())
        val testButton = JButton("测试连接")
        testButton.addActionListener {
            testConnection()
        }
        checkboxButtonPanel.add(testButton)

        // 解析信息显示区域
        val infoLabel = JBLabel("服务器信息:")
        infoLabel.alignmentX = Component.LEFT_ALIGNMENT
        infoArea.alignmentX = Component.LEFT_ALIGNMENT
        infoArea.border = JBUI.Borders.empty(5)

        // 添加组件
        formPanel.add(urlLabel)
        formPanel.add(Box.createVerticalStrut(5))
        formPanel.add(serverUrlField)
        formPanel.add(Box.createVerticalStrut(12))
        formPanel.add(tokenLabel)
        formPanel.add(Box.createVerticalStrut(5))
        formPanel.add(tokenField)
        formPanel.add(Box.createVerticalStrut(12))
        formPanel.add(checkboxButtonPanel)
        formPanel.add(Box.createVerticalStrut(12))
        formPanel.add(infoLabel)
        formPanel.add(Box.createVerticalStrut(5))
        formPanel.add(infoArea)
        formPanel.add(Box.createVerticalStrut(10))

        // 添加说明
//        val helpLabel = JBLabel(
//            "<html><div style='width:420px'>" +
//            "提示: 输入GitLab服务器地址，项目路径将自动从当前项目的Git远程URL中检测<br>" +
//            "如需在所有项目中使用此服务器，请勾选 'Set as Default Server'" +
//            "</div></html>"
//        )
//        helpLabel.alignmentX = Component.LEFT_ALIGNMENT
//        formPanel.add(helpLabel)

        panel.add(formPanel, BorderLayout.NORTH)

        // URL输入变化时自动解析
        serverUrlField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = parseServerUrl()
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = parseServerUrl()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = parseServerUrl()
        })

        // 编辑模式：预填充字段
        if (existingServer != null) {
            serverUrlField.text = existingServer.url
            tokenField.text = existingServer.token
            setAsDefaultCheckbox.isSelected = existingServer.isDefault
            parsedServer = existingServer.copy()
        }

        return panel
    }

    /**
     * 解析服务器URL
     */
    private fun parseServerUrl() {
        val serverUrl = serverUrlField.text.trim()
        if (serverUrl.isBlank()) {
            infoArea.text = "请输入GitLab服务器地址"
            parsedServer = null
            return
        }

        try {
            val uri = java.net.URI(serverUrl)

            // 验证协议
            if (!listOf("http", "https").contains(uri.scheme)) {
                infoArea.text = "⚠️ 无效的URL协议\n\n仅支持 http:// 或 https://"
                parsedServer = null
                return
            }

            // 验证主机
            if (uri.host.isNullOrBlank()) {
                infoArea.text = "⚠️ 无效的服务器地址\n\n无法检测到主机名"
                parsedServer = null
                return
            }

            // 标准化服务器URL（保留端口信息）
            val normalizedUrl = if (uri.port != -1 && uri.port != 80 && uri.port != 443) {
                "${uri.scheme}://${uri.host}:${uri.port}"
            } else {
                "${uri.scheme}://${uri.host}"
            }

            infoArea.text = "✓ 服务器: $normalizedUrl\n\n项目路径将自动从Git远程检测"

            parsedServer = GitLabServer(
                id = GitLabServer.generateId(),
                name = normalizedUrl,
                url = normalizedUrl,
                token = "",
                isDefault = setAsDefaultCheckbox.isSelected
            )
        } catch (e: Exception) {
            infoArea.text = "⚠️ 无效的URL格式\n\n请确保输入正确的GitLab服务器地址\n\n错误: ${e.message}"
            parsedServer = null
        }
    }

    override fun doValidate(): ValidationInfo? {
        val serverUrl = serverUrlField.text.trim()
        val token = tokenField.password.joinToString("").trim()

        if (serverUrl.isEmpty()) {
            return ValidationInfo("请输入GitLab服务器地址", serverUrlField)
        }

        if (!isValidServerUrl(serverUrl)) {
            return ValidationInfo("请输入有效的GitLab服务器地址（例如: https://gitlab.com）", serverUrlField)
        }

        if (token.isEmpty()) {
            return ValidationInfo("请输入API Token", tokenField)
        }

        if (parsedServer == null) {
            return ValidationInfo("无法解析服务器地址，请检查URL格式", serverUrlField)
        }

        return null
    }

    override fun doOKAction() {
        val token = tokenField.password.joinToString("").trim()

        if (parsedServer != null) {
            parsedServer!!.token = token
            parsedServer!!.isDefault = setAsDefaultCheckbox.isSelected
            super.doOKAction()
        }
    }

    /**
     * 测试连接
     */
    private fun testConnection() {
        val serverUrl = serverUrlField.text.trim()
        val token = tokenField.password.joinToString("").trim()

        if (serverUrl.isEmpty() || token.isEmpty()) {
            GitLabNotifications.showError(
                projectField,
                "验证失败",
                "请先填写服务器地址和API Token"
            )
            return
        }

        if (parsedServer == null) {
            GitLabNotifications.showError(
                projectField,
                "验证失败",
                "无法解析服务器地址"
            )
            return
        }

        // 在后台任务中测试连接
        ProgressManager.getInstance().run(object : Task.Backgroundable(projectField, "测试GitLab连接", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在连接到GitLab..."

                try {
                    // 设置token
                    parsedServer!!.token = token

                    val apiClient = GitLabApiClient(parsedServer!!, projectField)
                    val result = runBlocking {
                        apiClient.testConnection()
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (result.success) {
                            GitLabNotifications.showSuccess(
                                projectField,
                                "连接成功",
                                "已成功连接到GitLab服务器"
                            )
                        } else {
                            // 如果是401错误，提供额外的提示
                            val errorHint = if (result.statusCode == 401) {
                                "\n\n提示: 如果您的token是有效的，可能是因为：\n" +
                                "1. Token需要勾选 'api' 权限\n" +
                                "2. Token可能已过期，请重新生成\n" +
                                "3. 请在GitLab设置 -> Access Tokens中检查"
                            } else {
                                ""
                            }

                            GitLabNotifications.showError(
                                projectField,
                                "连接失败",
                                result.error + errorHint
                            )
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(
                            projectField,
                            "连接失败",
                            e.message ?: "无法连接到GitLab服务器"
                        )
                    }
                }
            }
        })
    }

    /**
     * 验证服务器URL格式
     */
    private fun isValidServerUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false
        }

        return try {
            val uri = java.net.URI(url)
            listOf("http", "https").contains(uri.scheme) && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取创建的服务器配置
     */
    fun getServer(): GitLabServer? = parsedServer
}
