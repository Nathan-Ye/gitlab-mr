package com.nathan.gitlabmr.toolwindow

import com.nathan.gitlabmr.api.GitLabApiClient
import com.nathan.gitlabmr.model.*
import com.nathan.gitlabmr.util.GitLabNotifications
import com.nathan.gitlabmr.util.GitUtil
import com.nathan.gitlabmr.util.BranchFilters
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

/**
 * 创建GitLab合并请求对话框
 *
 * @param project IntelliJ项目
 * @param server GitLab服务器配置
 * @param projectId 项目ID
 * @param preloadedBranches 预加载的分支列表（可选）
 * @param preloadedMembers 预加载的成员列表（可选）
 */
class CreateMRDialog(
    private val project: Project,
    private val server: GitLabServer,
    private val projectId: String,
    preloadedBranches: List<GitLabBranch>? = null,
    preloadedMembers: List<GitLabMember>? = null,
    private val onMergeRequestCreated: ((CreateMergeRequestResponse) -> Unit)? = null
) : DialogWrapper(project, true) {

    // UI组件
    private val sourceBranchField = ComboBox<String>()
    private val targetBranchField = ComboBox<String>()
    private val titleField = JBTextField()
    private val descriptionArea = JBTextArea(5, 40)
    private val assigneeField = ComboBox<String>()
    private val removeSourceBranchCheckbox = JCheckBox("合并后删除源分支")

    // 数据
    private var branches: List<GitLabBranch> = emptyList()
    private var members: List<GitLabMember> = emptyList()
    private var isLoadingData = false
    private var isFilteringBranches = false
    private var branchCommitMap: MutableMap<String, GitLabCommit> = mutableMapOf()
    private var isTitleManuallyEdited = false
    private var isDescriptionManuallyEdited = false
    private var lastAutoFilledBranch: String? = null

    // API客户端
    private val apiClient: GitLabApiClient = GitLabApiClient.create(server, project)

    init {
        title = "创建GitLab合并请求"
        setOKButtonText("创建")
        setCancelButtonText("取消")
        init()

        // 如果提供了预加载数据，直接使用；否则异步加载
        if (preloadedBranches != null && preloadedMembers != null) {
            // 使用预加载数据
            branches = preloadedBranches
            members = preloadedMembers

            // 更新UI组件
            updateBranchComboBoxes()
            updateAssigneeComboBox()
            setDefaultValues()
        } else {
            // 回退到原有的异步加载逻辑
            ApplicationManager.getApplication().invokeLater {
                loadInitialData()
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(550, 400)

        // 源分支和目标分支在同一行
        val branchPanel = JPanel()
        branchPanel.layout = BoxLayout(branchPanel, BoxLayout.X_AXIS)
        branchPanel.alignmentX = Component.LEFT_ALIGNMENT

        // 源分支
        val sourceLabel = JBLabel("源分支:")
        sourceBranchField.isEditable = true
        sourceBranchField.toolTipText = "输入关键字后按回车筛选分支，或从下拉列表中选择"
        sourceBranchField.preferredSize = Dimension(200, 35)
        sourceBranchField.maximumSize = Dimension(200, 40)

        // 目标分支
        val targetLabel = JBLabel("目标分支:")
        targetBranchField.isEditable = true
        targetBranchField.toolTipText = "输入关键字后按回车筛选分支，或从下拉列表中选择（通常是main或master）"
        targetBranchField.preferredSize = Dimension(200, 35)
        targetBranchField.maximumSize = Dimension(200, 40)

        branchPanel.add(sourceLabel)
        branchPanel.add(Box.createHorizontalStrut(5))
        branchPanel.add(sourceBranchField)
        branchPanel.add(Box.createHorizontalStrut(20))
        branchPanel.add(targetLabel)
        branchPanel.add(Box.createHorizontalStrut(5))
        branchPanel.add(targetBranchField)
        branchPanel.add(Box.createHorizontalGlue())

        // 标题
        val titleLabel = JBLabel("标题:")
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT
        titleField.alignmentX = Component.LEFT_ALIGNMENT
        titleField.toolTipText = "合并请求的标题（将从源分支的最新提交自动填充）"
        titleField.preferredSize = Dimension(Int.MAX_VALUE, 36)
        titleField.maximumSize = Dimension(Int.MAX_VALUE, 40)

        // 描述
        val descriptionLabel = JBLabel("描述:")
        descriptionLabel.alignmentX = Component.LEFT_ALIGNMENT
        descriptionArea.alignmentX = Component.LEFT_ALIGNMENT
        descriptionArea.lineWrap = true
        descriptionArea.wrapStyleWord = true
        val scrollPane = JBScrollPane(descriptionArea)
        scrollPane.alignmentX = Component.LEFT_ALIGNMENT
        scrollPane.minimumSize = Dimension(Int.MAX_VALUE, 120)
        scrollPane.preferredSize = Dimension(Int.MAX_VALUE, 120)
        scrollPane.maximumSize = Dimension(Int.MAX_VALUE, 150)

        // 指派人 - 使用更小的尺寸，放在同一行
        val assigneePanel = JPanel()
        assigneePanel.layout = BoxLayout(assigneePanel, BoxLayout.X_AXIS)
        assigneePanel.alignmentX = Component.LEFT_ALIGNMENT

        val assigneeLabel = JBLabel("指派人:")
        assigneeField.toolTipText = "选择指派给哪个用户（可选）"
        assigneeField.preferredSize = Dimension(150, 35)
        assigneeField.maximumSize = Dimension(180, 40)

        // 删除源分支复选框
        removeSourceBranchCheckbox.toolTipText = "合并成功后自动删除源分支"

        assigneePanel.add(assigneeLabel)
        assigneePanel.add(Box.createHorizontalStrut(5))
        assigneePanel.add(assigneeField)
        assigneePanel.add(Box.createHorizontalGlue())
        assigneePanel.add(removeSourceBranchCheckbox)

        // 添加组件到面板
        panel.add(branchPanel)
        panel.add(Box.createVerticalStrut(12))

        panel.add(titleLabel)
        panel.add(Box.createVerticalStrut(5))
        panel.add(titleField)
        panel.add(Box.createVerticalStrut(12))

        panel.add(descriptionLabel)
        panel.add(Box.createVerticalStrut(5))
        panel.add(scrollPane)
        panel.add(Box.createVerticalStrut(12))

        panel.add(assigneePanel)
        panel.add(Box.createVerticalStrut(10))

        // 监听标题和描述的手动编辑
        titleField.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyReleased(e: java.awt.event.KeyEvent?) {
                isTitleManuallyEdited = true
            }
        })

        descriptionArea.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyReleased(e: java.awt.event.KeyEvent?) {
                isDescriptionManuallyEdited = true
            }
        })

        // 设置事件监听：仅当“从下拉列表真实点选到 branches 中的分支”时才视为有效选择
        sourceBranchField.addActionListener {
            if (!isLoadingData && !isFilteringBranches && isValidBranchSelection(sourceBranchField)) {
                updateTitleAndDescriptionFromBranch()
            }
        }

        targetBranchField.addActionListener {
            // 目标分支改变时不需要特殊处理（不加载提交信息）
        }

        // 回车筛选：可编辑下拉框的编辑器中监听 VK_ENTER
        installBranchFilterEnterHandler(sourceBranchField)
        installBranchFilterEnterHandler(targetBranchField)

        return panel
    }

    /**
     * 在可编辑 ComboBox 的编辑器组件上监听回车：执行关键字筛选、刷新下拉项、消费事件、展开下拉。
     */
    private fun installBranchFilterEnterHandler(comboBox: ComboBox<String>) {
        val editorComponent = comboBox.editor.editorComponent as? JComponent ?: return
        editorComponent.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode != java.awt.event.KeyEvent.VK_ENTER) {
                    return
                }
                // 事件由该监听器消费，避免触发弹窗默认按钮
                e.consume()
                applyBranchFilter(comboBox)
            }
        })
    }

    /**
     * 用输入框当前文字筛选分支并刷新下拉项；结果为空时填入占位项。
     * 刷新过程中始终保留用户输入的关键字，允许继续输入、继续回车筛选。
     * 刷新期间抑制“选中即加载提交信息”的行为监听。
     */
    private fun applyBranchFilter(comboBox: ComboBox<String>) {
        if (isLoadingData) {
            return
        }
        val keyword = comboBox.editor.item as? String ?: ""
        val filtered = BranchFilters.filterBranches(branches, keyword)

        isFilteringBranches = true
        try {
            comboBox.removeAllItems()
            if (filtered.isEmpty()) {
                comboBox.addItem(NO_MATCH_PLACEHOLDER)
            } else {
                BranchFilters.rankBranches(filtered).forEach { comboBox.addItem(it.name) }
            }
            // 不做任何选中（避免清空编辑器），并立即把关键字写回编辑器
            restoreEditorText(comboBox, keyword)
        } finally {
            isFilteringBranches = false
        }

        // 刷新后总是展开下拉；展开后再兜底恢复一次文字，防止 popup 切换显示时清空
        ApplicationManager.getApplication().invokeLater {
            if (!comboBox.isPopupVisible) {
                comboBox.showPopup()
            }
            restoreEditorText(comboBox, keyword)
        }
    }

    /**
     * 把关键字直接写回可编辑 ComboBox 的文本组件，避免 selectedIndex 变化清空输入。
     */
    private fun restoreEditorText(comboBox: ComboBox<String>, keyword: String) {
        (comboBox.editor.editorComponent as? javax.swing.text.JTextComponent)?.text = keyword
        comboBox.editor.item = keyword
    }

    /**
     * 判断当前 selectedItem 是否为 branches 中真实存在的分支名（占位项、手输文字均无效）。
     */
    private fun isValidBranchSelection(comboBox: ComboBox<String>): Boolean {
        val selected = comboBox.selectedItem as? String ?: return false
        return branches.any { it.name == selected }
    }

    override fun createSouthPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()

        // 左侧：自定义按钮区域
        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.X_AXIS)
        leftPanel.border = JBUI.Borders.emptyRight(10)

        val mergeCurrentBranchButton = JButton("合并当前分支")
        mergeCurrentBranchButton.toolTipText = "使用当前Git分支作为源分支，并自动填充提交信息"
        mergeCurrentBranchButton.addActionListener {
            handleMergeCurrentBranch()
        }

        leftPanel.add(mergeCurrentBranchButton)
        leftPanel.add(Box.createHorizontalGlue())

        // 右侧：默认的OK/Cancel按钮
        val rightPanel = super.createSouthPanel() as? JPanel ?: JPanel()

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(rightPanel, BorderLayout.EAST)

        return panel
    }

    /**
     * 加载初始数据（分支列表和成员列表）
     */
    private fun loadInitialData() {
        isLoadingData = true
        sourceBranchField.addItem("加载中...")
        targetBranchField.addItem("加载中...")
        assigneeField.addItem("加载中...")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载数据", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在加载分支和成员列表..."

                try {
                    // 串行加载分支和成员（避免async问题）
                    val branchesResponse = runBlocking {
                        apiClient.getProjectBranches(projectId)
                    }

                    val membersResponse = runBlocking {
                        apiClient.getProjectMembers(projectId)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (branchesResponse.success && branchesResponse.data != null) {
                            branches = branchesResponse.data

                            // 更新分支下拉框
                            updateBranchComboBoxes()

                            // 设置默认值
                            setDefaultValues()
                        } else {
                            GitLabNotifications.showError(
                                project,
                                "加载失败",
                                "无法加载分支列表: ${branchesResponse.error}"
                            )
                            sourceBranchField.removeAllItems()
                            sourceBranchField.addItem("加载失败")
                        }

                        if (membersResponse.success && membersResponse.data != null) {
                            members = membersResponse.data
                            updateAssigneeComboBox()
                        } else {
                            // 成员列表加载失败不影响主流程
                            assigneeField.removeAllItems()
                            assigneeField.addItem("无成员")
                        }

                        isLoadingData = false
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(
                            project,
                            "加载失败",
                            "加载数据时发生错误: ${e.message}"
                        )
                        isLoadingData = false
                        sourceBranchField.removeAllItems()
                        sourceBranchField.addItem("加载失败")
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                super.onThrowable(error)
                ApplicationManager.getApplication().invokeLater {
                    GitLabNotifications.showError(
                        project,
                        "加载失败",
                        "加载数据时发生错误: ${error.message}"
                    )
                    isLoadingData = false
                }
            }
        })
    }

    /**
     * 更新分支下拉框
     */
    private fun updateBranchComboBoxes() {
        // 保存当前选择
        val currentSource = sourceBranchField.selectedItem as? String
        val currentTarget = targetBranchField.selectedItem as? String

        sourceBranchField.removeAllItems()
        targetBranchField.removeAllItems()

        // 对分支进行排序（排序规则统一维护在 BranchFilters.rankBranches）
        val sortedBranches = BranchFilters.rankBranches(branches)

        // 添加排序后的分支
        sortedBranches.forEach { branch ->
            sourceBranchField.addItem(branch.name)
            targetBranchField.addItem(branch.name)
        }

        // 如果之前没有有效选择（或者是"加载中..."），则清除选择
        // 如果有有效的用户选择，则恢复它
        if (currentSource != null && currentSource != "加载中..." && currentSource != "加载失败" && branches.any { it.name == currentSource }) {
            sourceBranchField.selectedItem = currentSource
        } else {
            sourceBranchField.selectedIndex = -1
        }

        if (currentTarget != null && currentTarget != "加载中..." && currentTarget != "加载失败" && branches.any { it.name == currentTarget }) {
            targetBranchField.selectedItem = currentTarget
        } else {
            targetBranchField.selectedIndex = -1
        }
    }

    /**
     * 更新指派人下拉框
     */
    private fun updateAssigneeComboBox() {
        assigneeField.removeAllItems()
        assigneeField.addItem("无")

        members.forEach { member ->
            assigneeField.addItem("${member.name} (@${member.username})")
        }
    }

    /**
     * 设置默认值
     * 注：不设置任何默认值，让用户手动选择
     */
    private fun setDefaultValues() {
        // 不设置任何默认值，保持字段为空
    }

    /**
     * 从源分支的最新提交更新标题和描述
     */
    private fun updateTitleAndDescriptionFromBranch() {
        val sourceBranch = (sourceBranchField.selectedItem as? String)?.trim() ?: return

        if (sourceBranch.isEmpty() || sourceBranch == "加载中..." || sourceBranch == "加载失败") {
            return
        }

        // 如果切换到了不同的分支，允许自动更新标题和描述
        if (lastAutoFilledBranch != null && lastAutoFilledBranch != sourceBranch) {
            // 用户切换了分支，重置手动编辑标志，允许自动填充新的分支信息
            isTitleManuallyEdited = false
            isDescriptionManuallyEdited = false
        }

        // 检查缓存
        if (branchCommitMap.containsKey(sourceBranch)) {
            val commit = branchCommitMap[sourceBranch]!!
            fillTitleAndDescription(commit, sourceBranch)
            return
        }

        // 异步加载提交信息
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载提交信息", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在获取分支提交信息..."

                try {
                    val response = runBlocking {
                        apiClient.getBranchCommit(projectId, sourceBranch)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (response.success && response.data != null) {
                            val commit = response.data
                            branchCommitMap[sourceBranch] = commit
                            fillTitleAndDescription(commit, sourceBranch)
                        }
                    }
                } catch (e: Exception) {
                    // 静默失败，用户可以手动填写
                }
            }
        })
    }

    /**
     * 填充标题和描述
     * @param commit 提交信息
     * @param sourceBranch 源分支名称
     */
    private fun fillTitleAndDescription(commit: GitLabCommit, sourceBranch: String) {
        // 如果用户没有手动编辑过标题，则自动填充
        if (!isTitleManuallyEdited) {
            titleField.text = commit.title
        }

        // 如果用户没有手动编辑过描述，则自动填充
        if (!isDescriptionManuallyEdited) {
            descriptionArea.text = commit.message
        }

        // 记录最后自动填充的分支
        lastAutoFilledBranch = sourceBranch
    }

    /**
     * 处理"合并当前分支"按钮点击
     */
    private fun handleMergeCurrentBranch() {
        // 1. 获取当前Git仓库
        val repository = GitUtil.getMainRepository(project)
        if (repository == null) {
            GitLabNotifications.showError(
                project,
                "无法获取Git仓库",
                "请确保当前项目在Git版本控制下"
            )
            return
        }

        // 2. 获取当前分支名
        val currentBranch = GitUtil.getCurrentBranch(repository)
        if (currentBranch == null) {
            GitLabNotifications.showError(
                project,
                "无法获取当前分支",
                "请确保项目在Git版本控制下并且有活动的分支"
            )
            return
        }

        val branchName = currentBranch.name

        // 3. 检查分支是否在远程列表中（以数据源为准，不受当前筛选结果影响）
        if (branches.none { it.name == branchName }) {
            // 分支不在远程列表中（可能是本地新分支，还未推送）
            GitLabNotifications.showError(
                project,
                "远程分支不存在",
                "当前分支 \"$branchName\" 在远程仓库中不存在。\n请先使用 git push 推送该分支到远程仓库。"
            )
            return
        }

        // 4. 重置下拉项为全部分支，再选中源分支
        updateBranchComboBoxes()
        sourceBranchField.selectedItem = branchName

        // 5. 触发提交信息加载（复用现有逻辑）
        updateTitleAndDescriptionFromBranch()

        // 6. 获取当前用户并设置为指派人
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载用户信息", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在获取当前用户信息..."

                try {
                    val response = runBlocking {
                        apiClient.getCurrentUser()
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (response.success && response.data != null) {
                            val currentUser = response.data
                            // 在成员列表中查找当前用户
                            val assigneeText = "${currentUser.name} (@${currentUser.username})"
                            // 检查指派人下拉框是否包含该用户
                            for (i in 0 until assigneeField.itemCount) {
                                if (assigneeField.getItemAt(i) == assigneeText) {
                                    assigneeField.selectedItem = assigneeText
                                    break
                                }
                            }
                        }
                        // 如果获取失败，静默处理（不影响主流程）
                    }
                } catch (e: Exception) {
                    // 静默失败，用户可以手动选择指派人
                }
            }
        })
    }

    /**
     * 获取选中的指派人ID
     */
    private fun getSelectedAssigneeId(): Long? {
        val selected = assigneeField.selectedItem as? String ?: return null
        if (selected == "无") return null

        // 解析格式: "Name (@username)"
        val username = selected.substringAfter("(@").substringBefore(")")
        return members.find { it.username == username }?.id
    }

    override fun doValidate(): ValidationInfo? {
        if (!isValidBranchSelection(sourceBranchField)) {
            return ValidationInfo("请从下拉中选择源分支", sourceBranchField)
        }

        if (!isValidBranchSelection(targetBranchField)) {
            return ValidationInfo("请从下拉中选择目标分支", targetBranchField)
        }

        val sourceBranch = (sourceBranchField.selectedItem as String).trim()
        val targetBranch = (targetBranchField.selectedItem as String).trim()

        if (sourceBranch == targetBranch) {
            return ValidationInfo("源分支和目标分支不能相同", sourceBranchField)
        }

        if (titleField.text.trim().isEmpty()) {
            return ValidationInfo("请输入合并请求标题", titleField)
        }

        return null
    }

    override fun doOKAction() {
        val validationInfo = doValidate()
        if (validationInfo != null) {
            setErrorText(validationInfo.message, validationInfo.component)
            return
        }
        setErrorText(null)

        val sourceBranch = (sourceBranchField.selectedItem as? String)?.trim() ?: ""

        val targetBranch = (targetBranchField.selectedItem as? String)?.trim() ?: ""

        val title = titleField.text.trim()
        val description = descriptionArea.text.trim().ifBlank { null }
        val assigneeId = getSelectedAssigneeId()
        val removeSourceBranch = removeSourceBranchCheckbox.isSelected

        close(OK_EXIT_CODE)

        // 在后台验证分支并创建MR
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建合并请求", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                // 验证源分支
                indicator.text = "正在验证源分支..."
                val sourceResponse = runBlocking {
                    apiClient.getBranchCommit(projectId, sourceBranch)
                }
                if (!sourceResponse.success) {
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(
                            project,
                            "验证失败",
                            "源分支 '$sourceBranch' 在远程仓库中不存在"
                        )
                    }
                    return
                }

                // 验证目标分支
                indicator.text = "正在验证目标分支..."
                val targetResponse = runBlocking {
                    apiClient.getBranchCommit(projectId, targetBranch)
                }
                if (!targetResponse.success) {
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(
                            project,
                            "验证失败",
                            "目标分支 '$targetBranch' 在远程仓库中不存在"
                        )
                    }
                    return
                }

                // 创建MR请求
                val request = CreateMergeRequestRequest(
                    sourceBranch = sourceBranch,
                    targetBranch = targetBranch,
                    title = title,
                    description = description,
                    assigneeId = assigneeId,
                    removeSourceBranch = removeSourceBranch
                )

                indicator.text = "正在创建合并请求..."
                val response = runBlocking {
                    apiClient.createMergeRequest(projectId, request)
                }

                ApplicationManager.getApplication().invokeLater {
                    if (response.success && response.data != null) {
                        val mr = response.data
                        GitLabNotifications.showSuccess(
                            project,
                            "创建成功",
                            "合并请求 !${mr.iid} \"${mr.title}\" 已创建\n${mr.webUrl}"
                        )
                        onMergeRequestCreated?.invoke(mr)
                    } else {
                        GitLabNotifications.showError(
                            project,
                            "创建失败",
                            response.error ?: "无法创建合并请求"
                        )
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                super.onThrowable(error)
                ApplicationManager.getApplication().invokeLater {
                    GitLabNotifications.showError(
                        project,
                        "创建失败",
                        "创建合并请求时发生错误: ${error.message}"
                    )
                }
            }
        })
    }

    companion object {
        private const val NO_MATCH_PLACEHOLDER = "(无匹配分支)"
    }
}
