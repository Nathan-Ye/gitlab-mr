package com.gitlab.idea.toolwindow

import com.gitlab.idea.api.GitLabApiClient
import com.gitlab.idea.config.GitLabConfigService
import com.gitlab.idea.config.GitLabProjectConfigService
import com.gitlab.idea.model.*
import com.gitlab.idea.toolwindow.components.*
import com.gitlab.idea.util.GitLabNotifications
import com.gitlab.idea.util.GitUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import kotlinx.coroutines.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.*

/**
 * GitLab工具窗口内容面板
 * 管理所有子面板的状态切换
 */
class GitLabToolWindowContent(
    private val project: Project,
    private val toolWindow: ToolWindow
) : Disposable, CoroutineScope {

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override val coroutineContext = coroutineScope.coroutineContext

    private val mainPanel: JPanel = JPanel(CardLayout())
    private val cardLayout: CardLayout = mainPanel.layout as CardLayout

    // 子面板
    private val emptyStatePanel: EmptyStatePanel
    private val errorStatePanel: ErrorStatePanel
    private val loadingStatePanel: LoadingStatePanel
    private val mainContentPanel: MainContentPanel

    // 数据
    private var currentServer: GitLabServer? = null
    private var currentProject: GitLabProject? = null
    private var mergeRequests: MutableList<GitLabMergeRequest> = mutableListOf()
    private var filteredMergeRequests: List<GitLabMergeRequest> = mutableListOf()

    // 分页状态
    private var currentPage: Int = 1
    private val pageSize: Int = 100
    private var hasMore: Boolean = true
    private var isLoadingMore: Boolean = false
    private var currentApiClient: GitLabApiClient? = null
    private var currentProjectId: String? = null

    // 筛选条件
    private var filterState: MergeRequestState? = null
    private var filterScope: String? = null
    private var filterTitleKeyword: String? = null

    init {
        // 初始化子面板
        emptyStatePanel = EmptyStatePanel()
        errorStatePanel = ErrorStatePanel()
        loadingStatePanel = LoadingStatePanel()
        mainContentPanel = MainContentPanel()

        // 添加所有卡片
        mainPanel.add(emptyStatePanel, CardState.EMPTY.name)
        mainPanel.add(errorStatePanel, CardState.ERROR.name)
        mainPanel.add(loadingStatePanel, CardState.LOADING.name)
        mainPanel.add(mainContentPanel, CardState.MAIN.name)
    }

    /**
     * 初始化
     */
    fun initialize() {
        // 设置面板事件回调
        setupEventHandlers()

        // 加载配置并决定显示哪个面板
        loadInitialState()
    }

    /**
     * 设置事件处理器
     */
    private fun setupEventHandlers() {
        // 空状态面板 - 添加服务器按钮
        emptyStatePanel.onAddServerClicked = {
            showAddServerDialog()
        }

        // 错误面板 - 编辑和刷新按钮
        errorStatePanel.onEditClicked = {
            showAddServerDialog()
        }
        errorStatePanel.onRefreshClicked = {
            loadInitialState()
        }

        // 主面板 - 筛选和MR选择
        mainContentPanel.onFilterChanged = { state, scope, titleKeyword ->
            applyFilters(state, scope, titleKeyword)
        }
        mainContentPanel.onMRSelected = { mr ->
            mainContentPanel.updateMRDetails(mr)
        }

        // 主面板 - 设置和刷新按钮
        mainContentPanel.onSettingsClicked = {
            showEditServerDialog()
        }
        mainContentPanel.onRefreshClicked = {
            loadInitialState()
        }

        // 主面板 - 创建MR按钮
        mainContentPanel.onCreateMRClicked = {
            showCreateMRDialog()
        }

        // MR操作回调
        mainContentPanel.setOnCloseMR { mr -> mainContentPanel.handleCloseMR(mr) }
        mainContentPanel.setOnMergeMR { mr -> mainContentPanel.handleMergeMR(mr) }
        mainContentPanel.setOnDeleteMR { mr -> mainContentPanel.handleDeleteMR(mr) }
    }

    /**
     * 加载初始状态
     */
    private fun loadInitialState() {
        val projectConfigService = GitLabProjectConfigService.getInstance(project)
        val appConfigService = GitLabConfigService.getInstance()

        val projectSelectedServer = projectConfigService.getSelectedServer()
        val defaultServers = appConfigService.getDefaultServers()
        val appSelectedServer = appConfigService.getSelectedServer()

        when {
            // 1. 项目级选中的服务器（最高优先级）
            projectSelectedServer != null -> {
                currentServer = projectSelectedServer
                loadData(projectSelectedServer)
            }
            // 2. 应用级默认服务器
            appSelectedServer != null || defaultServers.isNotEmpty() -> {
                val server = appSelectedServer ?: defaultServers.first()
                currentServer = server
                loadData(server)
            }
            // 3. 降级：任意可用服务器
            projectConfigService.getAllServers().isNotEmpty() -> {
                val server = projectConfigService.getAllServers().first()
                projectConfigService.setSelectedServer(server.id)
                currentServer = server
                loadData(server)
            }
            // 无服务器配置
            else -> showCard(CardState.EMPTY)
        }
    }

    /**
     * 加载数据
     */
    private fun loadData(server: GitLabServer) {
        // 显示加载状态
        loadingStatePanel.setLoadingMessage("正在刷新...")
        showCard(CardState.LOADING)

        // 重置分页状态
        currentPage = 1
        hasMore = true
        mergeRequests.clear()
        filteredMergeRequests = emptyList()

        launch {
            try {
                val apiClient = GitLabApiClient.create(server, project)

                // 策略 1: 从 Git 远程 URL 自动检测项目路径（主要方法）
                val repository = GitUtil.getMainRepository(project)
                if (repository != null) {
                    val remoteUrl = GitUtil.getRemoteUrl(repository)
                    if (remoteUrl != null) {
                        val projectPath = GitUtil.extractProjectPathFromUrl(remoteUrl)

                        if (projectPath != null) {
                            val projectResponse = apiClient.getProject(projectPath)
                            if (projectResponse.success && projectResponse.data != null) {
                                currentProject = projectResponse.data
                                loadMergeRequestsInitial(apiClient, projectResponse.data.id.toString())
                                return@launch
                            }
                        }
                    }
                }

                // 策略 2: 降级 - 提示用户配置 Git 远程
                showError(
                    "无法检测项目路径",
                    "无法从 Git 远程 URL 自动检测项目路径。\n\n" +
                    "建议操作：\n" +
                    "• 点击下方编辑图标手动配置项目路径\n" +
                    "• 或检查 Git 远程配置是否正确"
                )
            } catch (e: Exception) {
                showError(
                    "加载失败",
                    "连接 GitLab 服务器失败。\n\n" +
                    "请检查：\n" +
                    "• 网络连接是否正常\n" +
                    "• 点击下方编辑图标检查服务器配置\n\n" +
                    "错误信息：${e.message}"
                )
            }
        }
    }

    /**
     * 初始加载合并请求列表（仅第一页）
     */
    private fun loadMergeRequestsInitial(apiClient: GitLabApiClient, projectId: String) {
        currentApiClient = apiClient
        currentProjectId = projectId

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading GitLab Merge Requests", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                runBlocking {
                    val response = apiClient.getMergeRequests(
                        projectId = projectId,
                        state = "all",
                        page = 1,
                        perPage = pageSize
                    )

                    if (response.success && response.data != null) {
                        ApplicationManager.getApplication().invokeLater {
                            mergeRequests.clear()
                            mergeRequests.addAll(response.data)
                            filteredMergeRequests = response.data
                            currentPage = 1
                            hasMore = response.data.size >= pageSize

                            mainContentPanel.setMergeRequests(response.data, hasMore)
                            showCard(CardState.MAIN)
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater {
                            showError("获取合并请求失败", response.error)
                        }
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                super.onThrowable(error)
                ApplicationManager.getApplication().invokeLater {
                    showError("加载失败", error.message)
                }
            }
        })
    }

    /**
     * 无感刷新合并请求列表（不显示加载状态，直接更新数据）
     * 使用当前筛选条件刷新数据
     */
    private fun refreshMergeRequestsSilently() {
        // 直接使用保存的筛选条件调用 API
        currentPage = 1
        hasMore = true
        loadMergeRequestsWithFilter()
    }

    /**
     * 加载更多合并请求（下一页）
     */
    fun loadMoreMergeRequests() {
        if (isLoadingMore || !hasMore || currentApiClient == null || currentProjectId == null) {
            return
        }

        isLoadingMore = true

        launch {
            try {
                val nextPage = currentPage + 1

                // 构建状态参数
                val stateParam = when (filterState) {
                    MergeRequestState.OPENED -> "opened"
                    MergeRequestState.CLOSED -> "closed"
                    MergeRequestState.LOCKED -> "locked"
                    MergeRequestState.MERGED -> "merged"
                    null -> "all"
                }

                // 使用筛选条件调用 API
                val response = currentApiClient!!.getMergeRequests(
                    projectId = currentProjectId!!,
                    state = stateParam,
                    page = nextPage,
                    perPage = pageSize,
                    search = filterTitleKeyword,
                    scope = filterScope
                )

                ApplicationManager.getApplication().invokeLater {
                    if (response.success && response.data != null) {
                        // 将新数据追加到完整列表
                        mergeRequests.addAll(response.data)
                        currentPage = nextPage
                        hasMore = response.data.size >= pageSize

                        // 直接追加新数据到列表显示
                        mainContentPanel.addMergeRequests(response.data)
                        mainContentPanel.updateLoadMoreStatus(hasMore)
                    } else {
                        showError("加载更多失败", response.error)
                        mainContentPanel.updateLoadMoreStatus(false)
                    }
                    isLoadingMore = false
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    showError("加载更多失败", e.message)
                    mainContentPanel.updateLoadMoreStatus(false)
                    isLoadingMore = false
                }
            }
        }
    }

    /**
     * 显示卡片
     */
    private fun showCard(state: CardState) {
        when (state) {
            CardState.EMPTY -> {
                cardLayout.show(mainPanel, CardState.EMPTY.name)
                toolWindow.title = ""
            }
            CardState.ERROR -> {
                cardLayout.show(mainPanel, CardState.ERROR.name)
            }
            CardState.LOADING -> {
                cardLayout.show(mainPanel, CardState.LOADING.name)
            }
            CardState.MAIN -> {
                cardLayout.show(mainPanel, CardState.MAIN.name)
                toolWindow.title = currentProject?.nameWithNamespace ?: "GitLab"
            }
        }
    }

    /**
     * 显示错误状态
     */
    private fun showError(title: String, message: String?) {
        errorStatePanel.setError(title, message ?: "未知错误")
        showCard(CardState.ERROR)
    }

    /**
     * 应用筛选条件
     */
    private fun applyFilters(state: MergeRequestState?, scope: String?, titleKeyword: String?) {
        // 保存筛选条件
        filterState = state
        filterScope = scope
        filterTitleKeyword = titleKeyword

        // 重置分页状态
        currentPage = 1
        hasMore = true
        mergeRequests.clear()
        filteredMergeRequests = emptyList()

        // 调用 API 加载数据
        loadMergeRequestsWithFilter()
    }

    /**
     * 使用当前筛选条件加载合并请求
     */
    private fun loadMergeRequestsWithFilter() {
        val apiClient = currentApiClient ?: return
        val projectId = currentProjectId ?: return

        launch {
            try {
                // 构建状态参数
                val stateParam = when (filterState) {
                    MergeRequestState.OPENED -> "opened"
                    MergeRequestState.CLOSED -> "closed"
                    MergeRequestState.LOCKED -> "locked"
                    MergeRequestState.MERGED -> "merged"
                    null -> "all"
                }

                // 调用 API
                val response = apiClient.getMergeRequests(
                    projectId = projectId,
                    state = stateParam,
                    page = 1,
                    perPage = pageSize,
                    search = filterTitleKeyword,
                    scope = filterScope
                )

                if (response.success && response.data != null) {
                    ApplicationManager.getApplication().invokeLater {
                        mergeRequests.clear()
                        mergeRequests.addAll(response.data)
                        filteredMergeRequests = response.data
                        currentPage = 1
                        hasMore = response.data.size >= pageSize

                        mainContentPanel.setMergeRequests(response.data, hasMore)
                        showCard(CardState.MAIN)
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        showError("获取合并请求失败", response.error)
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    showError("加载失败", e.message)
                }
            }
        }
    }

    /**
     * 显示添加服务器对话框
     */
    private fun showAddServerDialog() {
        val dialog = GitLabServerDialog(project)
        if (dialog.showAndGet()) {
            val server = dialog.getServer()
            if (server != null) {
                if (server.isDefault) {
                    // 应用级：保存到 GitLabConfigService
                    val appConfigService = GitLabConfigService.getInstance()
                    appConfigService.addServer(server)
                    appConfigService.setSelectedServer(server.id)
                } else {
                    // 项目级：保存到 GitLabProjectConfigService
                    val projectConfigService = GitLabProjectConfigService.getInstance(project)
                    projectConfigService.addServer(server)
                    projectConfigService.setSelectedServer(server.id)
                }

                loadInitialState()
            }
        }
    }

    /**
     * 显示编辑服务器对话框
     */
    private fun showEditServerDialog() {
        val server = currentServer ?: run {
            GitLabNotifications.showError(project, "错误", "无当前服务器配置")
            return
        }

        val dialog = GitLabServerDialog(project, server) // 编辑模式
        if (dialog.showAndGet()) {
            val updatedServer = dialog.getServer()
            if (updatedServer != null) {
                // 根据配置级别更新对应的服务
                if (server.isDefault) {
                    val configService = GitLabConfigService.getInstance()
                    configService.updateServer(updatedServer)
                } else {
                    val configService = GitLabProjectConfigService.getInstance(project)
                    configService.updateServer(updatedServer)
                }

                // 刷新数据
                currentServer = updatedServer
                loadData(updatedServer)
            }
        }
    }

    /**
     * 显示创建MR对话框
     */
    private fun showCreateMRDialog() {
        val server = currentServer ?: run {
            GitLabNotifications.showError(project, "错误", "无当前服务器配置")
            return
        }

        val gitProject = currentProject ?: run {
            GitLabNotifications.showError(project, "错误", "无当前项目信息")
            return
        }

        // 显示加载通知（保存引用以便后续关闭）
        val loadingNotification = com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("GitLab.Notification.Group")
            ?.createNotification("加载数据", "正在加载分支和成员列表...", com.intellij.notification.NotificationType.INFORMATION)
        loadingNotification?.notify(project)

        // 创建API客户端
        val apiClient = GitLabApiClient.create(server, project)

        // 使用协程加载数据，设置5秒超时
        launch {
            try {
                // 并行加载分支和成员数据，总超时5秒
                val branchesDeferred = async {
                    apiClient.getProjectBranches(gitProject.id.toString())
                }
                val membersDeferred = async {
                    apiClient.getProjectMembers(gitProject.id.toString())
                }

                // 使用 withTimeout 设置5秒超时
                withTimeout(5000L) {
                    val branchesResponse = branchesDeferred.await()
                    val membersResponse = membersDeferred.await()

                    // 检查结果
                    if (!branchesResponse.success) {
                        loadingNotification?.expire()
                        GitLabNotifications.showError(
                            project,
                            "加载失败",
                            "无法加载分支列表: ${branchesResponse.error}"
                        )
                        return@withTimeout
                    }

                    if (!membersResponse.success) {
                        loadingNotification?.expire()
                        GitLabNotifications.showError(
                            project,
                            "加载失败",
                            "无法加载成员列表: ${membersResponse.error}"
                        )
                        return@withTimeout
                    }

                    // 成功获取数据，在EDT线程显示对话框
                    ApplicationManager.getApplication().invokeLater {
                        // 关闭加载通知
                        loadingNotification?.expire()

                        val dialog = CreateMRDialog(
                            project,
                            server,
                            gitProject.id.toString(),
                            preloadedBranches = branchesResponse.data,
                            preloadedMembers = membersResponse.data
                        )
                        dialog.show()

                        // 如果成功创建，无感刷新MR列表
                        if (dialog.exitCode == OK_EXIT_CODE) {
                            refreshMergeRequestsSilently()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                loadingNotification?.expire()
                GitLabNotifications.showError(
                    project,
                    "加载超时",
                    "加载数据超时（超过5秒），请检查网络连接后重试"
                )
            } catch (e: Exception) {
                loadingNotification?.expire()
                GitLabNotifications.showError(
                    project,
                    "加载失败",
                    "加载数据时发生错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 获取内容面板
     */
    fun getContent(): JComponent = mainPanel

    override fun dispose() {
        coroutineScope.cancel()
    }

    /**
     * 卡片状态
     */
    private enum class CardState {
        EMPTY, ERROR, LOADING, MAIN
    }

    /**
     * 主内容面板（包含MR列表和详情）
     */
    inner class MainContentPanel : JPanel(BorderLayout()) {
        private val sideToolbar: ToolWindowSideToolbar = ToolWindowSideToolbar()
        private val mrListPanel: MRListPanel
        private val mrDetailsPanel: MRDetailsPanel

        var onFilterChanged: ((MergeRequestState?, String?, String?) -> Unit)? = null
        var onMRSelected: ((GitLabMergeRequest) -> Unit)? = null
        var onSettingsClicked: (() -> Unit)? = null
        var onRefreshClicked: (() -> Unit)? = null
        var onCreateMRClicked: (() -> Unit)? = null

        init {
            // 创建MR列表面板
            mrListPanel = MRListPanel()

            // 创建MR详情面板
            mrDetailsPanel = MRDetailsPanel()

            // 使用 IDEA 原生分割面板
            val splitter = com.intellij.ui.JBSplitter(false, 0.6f)
            splitter.firstComponent = mrListPanel
            splitter.secondComponent = mrDetailsPanel

            // 创建侧边工具栏容器面板（不显示分割线）
            val westPanel = JPanel(BorderLayout())
            westPanel.add(sideToolbar, BorderLayout.WEST)

            // 添加侧边工具栏容器到WEST
            add(westPanel, BorderLayout.WEST)
            // splitter 添加到CENTER
            add(splitter, BorderLayout.CENTER)

            // 设置工具栏事件
            sideToolbar.onSettingsClicked = { onSettingsClicked?.invoke() }
            sideToolbar.onRefreshClicked = { onRefreshClicked?.invoke() }
            sideToolbar.onCreateMRClicked = { onCreateMRClicked?.invoke() }

            // 设置事件回调
            mrListPanel.onFilterChanged = { state, scope, titleKeyword ->
                onFilterChanged?.invoke(state, scope, titleKeyword)
            }
            mrListPanel.onMRSelected = { mr ->
                onMRSelected?.invoke(mr)
            }
            mrListPanel.onLoadMore = {
                loadMoreMergeRequests()
            }
        }

        fun setMergeRequests(mrs: List<GitLabMergeRequest>, hasMore: Boolean = false) {
            mrListPanel.setMergeRequests(mrs, hasMore)
            // 清空详情面板，因为刷新后没有选中任何合并请求
            mrDetailsPanel.clear()
        }

        fun addMergeRequests(mrs: List<GitLabMergeRequest>) {
            mrListPanel.addMoreMergeRequests(mrs)
        }

        fun updateMRDetails(mr: GitLabMergeRequest) {
            mrDetailsPanel.setMergeRequest(mr)
        }

        fun updateLoadMoreStatus(hasMore: Boolean) {
            mrListPanel.updateLoadStatus(hasMore)
        }

        // MR操作回调设置
        fun setOnCloseMR(callback: (GitLabMergeRequest) -> Unit) {
            mrDetailsPanel.setOnCloseMR(callback)
        }

        fun setOnMergeMR(callback: (GitLabMergeRequest) -> Unit) {
            mrDetailsPanel.setOnMergeMR(callback)
        }

        fun setOnDeleteMR(callback: (GitLabMergeRequest) -> Unit) {
            mrDetailsPanel.setOnDeleteMR(callback)
        }

        /**
         * 处理关闭MR
         */
        fun handleCloseMR(mr: GitLabMergeRequest) {
            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "关闭合并请求", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "正在关闭合并请求..."

                    val response = runBlocking {
                        apiClient.closeMergeRequest(projectId, mr.iid)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (response.success) {
                            GitLabNotifications.showSuccess(project, "关闭成功", "合并请求已关闭")
                            refreshMRInList(response.data!!)
                        } else {
                            GitLabNotifications.showError(project, "关闭失败", response.error ?: "未知错误")
                        }
                    }
                }

                override fun onThrowable(error: Throwable) {
                    super.onThrowable(error)
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(project, "关闭失败", error.message ?: "未知错误")
                    }
                }
            })
        }

        /**
         * 处理合并MR
         */
        fun handleMergeMR(mr: GitLabMergeRequest) {
            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "合并合并请求", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "正在合并请求..."

                    val response = runBlocking {
                        apiClient.mergeMergeRequest(projectId, mr.iid, mr.forceRemoveSourceBranch)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (response.success) {
                            val message = if (mr.forceRemoveSourceBranch) {
                                "合并请求已成功合并，源分支将被删除"
                            } else {
                                "合并请求已成功合并"
                            }
                            GitLabNotifications.showSuccess(project, "合并成功", message)
                            refreshMRInList(response.data!!)
                        } else {
                            GitLabNotifications.showError(project, "合并失败", response.error ?: "未知错误")
                        }
                    }
                }

                override fun onThrowable(error: Throwable) {
                    super.onThrowable(error)
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(project, "合并失败", error.message ?: "未知错误")
                    }
                }
            })
        }

        /**
         * 处理删除MR
         */
        fun handleDeleteMR(mr: GitLabMergeRequest) {
            // 显示确认对话框
            if (!com.gitlab.idea.toolwindow.dialog.MRActionConfirmDialog.confirmDelete(project, mr)) {
                return
            }

            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "删除合并请求", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "正在删除合并请求..."

                    val response = runBlocking {
                        apiClient.deleteMergeRequest(projectId, mr.iid)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        if (response.success) {
                            GitLabNotifications.showSuccess(project, "删除成功", "合并请求已删除")
                            // 从列表中移除
                            removeMRFromList(mr)
                            // 清空详情面板
                            mrDetailsPanel.clear()
                        } else {
                            GitLabNotifications.showError(project, "删除失败", response.error ?: "未知错误")
                        }
                    }
                }

                override fun onThrowable(error: Throwable) {
                    super.onThrowable(error)
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(project, "删除失败", error.message ?: "未知错误")
                    }
                }
            })
        }

        /**
         * 在列表中刷新单个MR
         */
        private fun refreshMRInList(updatedMR: GitLabMergeRequest) {
            // 更新 mergeRequests 列表中的MR
            val index = mergeRequests.indexOfFirst { it.iid == updatedMR.iid }
            if (index != -1) {
                mergeRequests[index] = updatedMR
            }

            // 检查是否需要从列表中移除（因状态变化不再符合筛选条件）
            val shouldRemove = when {
                filterState != null && updatedMR.state != filterState -> true
                else -> false
            }

            if (shouldRemove) {
                // 重新应用筛选（会调用 API）
                applyFilters(filterState, filterScope, filterTitleKeyword)
            } else {
                // 仅更新显示
                mainContentPanel.updateMRDetails(updatedMR)
            }

            // 更新详情面板
            mrDetailsPanel.setMergeRequest(updatedMR)
        }

        /**
         * 从列表中移除MR
         */
        private fun removeMRFromList(mr: GitLabMergeRequest) {
            mergeRequests.removeAll { it.iid == mr.iid }
            // 重新应用筛选（会调用 API）
            applyFilters(filterState, filterScope, filterTitleKeyword)
        }
    }
}
