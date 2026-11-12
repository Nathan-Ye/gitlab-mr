package com.nathan.gitlabmr.toolwindow

import com.nathan.gitlabmr.api.GitLabApiClient
import com.nathan.gitlabmr.config.GitLabConfigService
import com.nathan.gitlabmr.config.GitLabProjectConfigService
import com.nathan.gitlabmr.model.*
import com.nathan.gitlabmr.toolwindow.components.*
import com.nathan.gitlabmr.util.GitLabNotifications
import com.nathan.gitlabmr.util.GitUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
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
    private val diffService = MRDiffService(project)

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
    private var currentRepositoryRoot: VirtualFile? = null
    private val mrPollingJobs = mutableMapOf<Long, Job>()
    private val mrRequestVersion = mutableMapOf<Long, Long>()
    private val silentRefreshInFlight = mutableSetOf<Long>()
    private var listReloadGeneration: Long = 0
    private var mergeRequestChangesJob: Job? = null
    private var refreshMergeRequestJob: Job? = null
    private var selectedMergeRequestIid: Long? = null
    private var currentSelectedMergeRequest: GitLabMergeRequest? = null
    private var openDiffJob: Job? = null
    private var currentFilterQueryIndicator: ProgressIndicator? = null

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
        mainContentPanel.setOnRefreshMR { mr -> mainContentPanel.handleRefreshMR(mr) }
        mainContentPanel.setOnChangedFileDoubleClicked { changeFile -> openMergeRequestDiff(changeFile) }
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

    private fun openMergeRequestDiff(changeFile: GitLabMergeRequestChangeFile) {
        val apiClient = currentApiClient ?: run {
            GitLabNotifications.showError(project, "错误", "当前未连接到 GitLab 服务")
            return
        }
        val projectId = currentProjectId ?: run {
            GitLabNotifications.showError(project, "错误", "当前项目信息不存在")
            return
        }
        val mergeRequest = currentSelectedMergeRequest ?: run {
            GitLabNotifications.showError(project, "错误", "请先选择一个合并请求")
            return
        }
        val changeFiles = mainContentPanel.getOrderedChangeFiles().ifEmpty { listOf(changeFile) }

        openDiffJob?.cancel()

        openDiffJob = launch {
            try {
                // diff_refs 缺失时先补全一次，避免差异链里每个文件重复请求合并请求详情
                val resolvedMergeRequest = if (mergeRequest.diffRefs != null) {
                    mergeRequest
                } else {
                    apiClient.getMergeRequest(projectId, mergeRequest.iid).data ?: mergeRequest
                }

                ApplicationManager.getApplication().invokeLater {
                    diffService.showDiff(apiClient, projectId, resolvedMergeRequest, changeFiles, changeFile)
                }
            } catch (_: CancellationException) {
                // Ignore stale open requests triggered by rapid repeated clicks.
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    GitLabNotifications.showError(project, "打开差异失败", e.message ?: "未知错误")
                }
            }
        }
    }

    /**
     * 加载数据
     */
    private fun loadData(server: GitLabServer) {
        cancelAllPollingJobs()

        // 显示加载状态
        loadingStatePanel.setLoadingMessage("正在刷新...")
        showCard(CardState.LOADING)

        // 重置分页状态
        launch {
            try {
                currentPage = 1
                hasMore = true
                mergeRequests.clear()
                filteredMergeRequests = emptyList()

                val apiClient = GitLabApiClient.create(server, project)

                // 策略 1: 从 Git 远程 URL 自动检测项目路径（主要方法）
                val repository = GitUtil.getMainRepository(project)
                if (repository != null) {
                    currentRepositoryRoot = repository.root
                    mainContentPanel.setRepositoryRoot(repository.root)
                    val remoteUrl = GitUtil.getRemoteUrl(repository)
                    if (remoteUrl != null) {
                        val projectPath = GitUtil.extractProjectPathFromUrl(remoteUrl)

                        if (projectPath != null) {
                            val projectResponse = apiClient.getProject(projectPath)
                            if (projectResponse.success && projectResponse.data != null) {
                                currentProject = projectResponse.data
                                apiClient.pipelineMustSucceed =
                                    projectResponse.data.onlyAllowMergeIfPipelineSucceeds == true
                                loadMergeRequestsInitial(apiClient, projectResponse.data.id.toString())
                                return@launch
                            }
                        }
                    }
                }

                currentRepositoryRoot = null
                mainContentPanel.setRepositoryRoot(null)

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

    private fun nextMrRequestVersion(mrIid: Long): Long {
        val nextVersion = (mrRequestVersion[mrIid] ?: 0L) + 1
        mrRequestVersion[mrIid] = nextVersion
        return nextVersion
    }

    private fun isLatestMrRequestVersion(mrIid: Long, version: Long): Boolean {
        return mrRequestVersion[mrIid] == version
    }

    private fun nextListReloadGeneration(): Long {
        listReloadGeneration += 1
        return listReloadGeneration
    }

    private fun shouldIncludeInCurrentFilter(mr: GitLabMergeRequest): Boolean {
        return when (filterState) {
            null -> true
            MergeRequestState.OPENED -> mr.state == MergeRequestState.OPENED || mr.state == MergeRequestState.CHECKING
            else -> mr.state == filterState
        }
    }

    private fun currentListStateParam(): String {
        return when (filterState) {
            MergeRequestState.OPENED, MergeRequestState.CHECKING -> "opened"
            MergeRequestState.CLOSED -> "closed"
            MergeRequestState.LOCKED -> "locked"
            MergeRequestState.MERGED -> "merged"
            null -> "all"
        }
    }

    private fun cancelAllPollingJobs() {
        mrPollingJobs.values.forEach { it.cancel() }
        mrPollingJobs.clear()
    }

    private fun stopPollingMergeRequest(mrIid: Long) {
        mrPollingJobs.remove(mrIid)?.cancel()
    }

    private fun ensurePollingMergeRequest(mrIid: Long) {
        if (mrPollingJobs[mrIid]?.isActive == true) return

        val apiClient = currentApiClient ?: return
        val projectId = currentProjectId ?: return

        mrPollingJobs[mrIid] = launch {
            var consecutiveFailures = 0
            val startedAt = System.currentTimeMillis()

            while (isActive && System.currentTimeMillis() - startedAt < POLLING_TIMEOUT_MS) {
                delay(POLLING_INTERVAL_MS)

                val version = nextMrRequestVersion(mrIid)
                try {
                    val response = apiClient.getMergeRequest(projectId, mrIid)
                    if (!isActive || !isLatestMrRequestVersion(mrIid, version)) continue

                    if (response.success && response.data != null) {
                        consecutiveFailures = 0
                        val updatedMR = response.data
                        ApplicationManager.getApplication().invokeLater {
                            if (!isLatestMrRequestVersion(mrIid, version)) return@invokeLater
                            mainContentPanel.refreshMRInList(updatedMR, reloadChanges = false)
                        }

                        if (!updatedMR.isMergeStatusPending()) {
                            stopPollingMergeRequest(mrIid)
                            break
                        }
                    } else {
                        consecutiveFailures += 1
                        if (consecutiveFailures >= 3) {
                            stopPollingMergeRequest(mrIid)
                            break
                        }
                    }
                } catch (_: CancellationException) {
                    break
                } catch (_: Exception) {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= 3) {
                        stopPollingMergeRequest(mrIid)
                        break
                    }
                }
            }

            mrPollingJobs.remove(mrIid)
        }
    }

    private fun trackCreatedMergeRequest(mrIid: Long) {
        val apiClient = currentApiClient ?: return
        val projectId = currentProjectId ?: return
        val version = nextMrRequestVersion(mrIid)

        launch {
            try {
                val response = apiClient.getMergeRequest(projectId, mrIid)
                ApplicationManager.getApplication().invokeLater {
                    if (!isLatestMrRequestVersion(mrIid, version)) return@invokeLater
                    if (response.success && response.data != null) {
                        mainContentPanel.refreshMRInList(response.data)
                    }
                }
            } catch (_: CancellationException) {
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 初始加载合并请求列表（仅第一页）
     */
    private fun loadMergeRequestsInitial(apiClient: GitLabApiClient, projectId: String) {
        currentApiClient = apiClient
        currentProjectId = projectId
        val generation = nextListReloadGeneration()

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
                            if (generation != listReloadGeneration) return@invokeLater
                            mergeRequests.clear()
                            mergeRequests.addAll(response.data)
                            filteredMergeRequests = response.data
                            currentPage = 1
                            hasMore = response.data.size >= pageSize

                            mainContentPanel.setMergeRequests(response.data, hasMore)
                            showCard(CardState.MAIN)
                            currentSelectedMergeRequest?.takeIf { it.isMergeStatusPending() }?.let {
                                ensurePollingMergeRequest(it.iid)
                            }
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

                val stateParam = currentListStateParam()

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
        // 调用 API 加载数据
        runFilterQueryWithProgress()
    }

    /**
     * 使用当前筛选条件加载合并请求
     */
    private fun loadMergeRequestsWithFilter() {
        val apiClient = currentApiClient ?: return
        val projectId = currentProjectId ?: return
        val generation = nextListReloadGeneration()

        currentPage = 1
        hasMore = true
        mergeRequests.clear()
        filteredMergeRequests = emptyList()

        launch {
            try {
                val stateParam = currentListStateParam()

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
                        if (generation != listReloadGeneration) return@invokeLater
                        mergeRequests.clear()
                        mergeRequests.addAll(response.data)
                        filteredMergeRequests = response.data
                        currentPage = 1
                        hasMore = response.data.size >= pageSize

                        mainContentPanel.setMergeRequests(response.data, hasMore)
                        showCard(CardState.MAIN)
                        currentSelectedMergeRequest?.takeIf { it.isMergeStatusPending() }?.let {
                            ensurePollingMergeRequest(it.iid)
                        }
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
    private fun runFilterQueryWithProgress() {
        val apiClient = currentApiClient ?: return
        val projectId = currentProjectId ?: return
        currentFilterQueryIndicator?.cancel()
        val generation = nextListReloadGeneration()

        currentPage = 1
        hasMore = true

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "查询合并请求", true) {
            override fun run(indicator: ProgressIndicator) {
                currentFilterQueryIndicator = indicator
                indicator.isIndeterminate = true
                indicator.text = "查询中..."

                try {
                    indicator.checkCanceled()
                    val stateParam = currentListStateParam()
                    val response = runBlocking {
                        apiClient.getMergeRequests(
                            projectId = projectId,
                            state = stateParam,
                            page = 1,
                            perPage = pageSize,
                            search = filterTitleKeyword,
                            scope = filterScope
                        )
                    }
                    indicator.checkCanceled()

                    ApplicationManager.getApplication().invokeLater {
                        if (currentFilterQueryIndicator === indicator) {
                            currentFilterQueryIndicator = null
                        }
                        if (indicator.isCanceled || generation != listReloadGeneration) return@invokeLater

                        if (response.success && response.data != null) {
                            applyLoadedMergeRequests(response.data)
                            GitLabNotifications.showSuccess(
                                project,
                                "查询完成",
                                "已完成合并请求查询，共 ${response.data.size} 条结果"
                            )
                        } else {
                            GitLabNotifications.showError(
                                project,
                                "查询失败",
                                response.error ?: "未知错误"
                            )
                        }
                    }
                } catch (_: ProcessCanceledException) {
                    ApplicationManager.getApplication().invokeLater {
                        if (currentFilterQueryIndicator === indicator) {
                            currentFilterQueryIndicator = null
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        if (currentFilterQueryIndicator === indicator) {
                            currentFilterQueryIndicator = null
                        }
                        if (indicator.isCanceled || generation != listReloadGeneration) return@invokeLater
                        GitLabNotifications.showError(
                            project,
                            "查询失败",
                            e.message ?: "未知错误"
                        )
                    }
                }
            }
        })
    }

    private fun applyLoadedMergeRequests(mrs: List<GitLabMergeRequest>) {
        mergeRequests.clear()
        mergeRequests.addAll(mrs)
        filteredMergeRequests = mrs
        currentPage = 1
        hasMore = mrs.size >= pageSize

        mainContentPanel.setMergeRequests(mrs, hasMore)
        showCard(CardState.MAIN)
        currentSelectedMergeRequest?.takeIf { it.isMergeStatusPending() }?.let {
            ensurePollingMergeRequest(it.iid)
        }
    }

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
                val projectConfigService = GitLabProjectConfigService.getInstance(project)
                val appConfigService = GitLabConfigService.getInstance()
                val wasProjectScoped = projectConfigService.getServerById(server.id) != null
                val wasAppScoped = appConfigService.getServerById(server.id) != null

                if (updatedServer.isDefault) {
                    if (wasProjectScoped) {
                        projectConfigService.removeServer(server.id)
                        projectConfigService.setSelectedServer(null)
                    }
                    if (wasAppScoped) {
                        appConfigService.removeServer(server.id)
                    }
                    appConfigService.clearAllDefaultServers()
                    appConfigService.addServer(updatedServer)
                    appConfigService.setSelectedServer(updatedServer.id)
                } else {
                    if (wasProjectScoped) {
                        projectConfigService.removeServer(server.id)
                    }
                    projectConfigService.addServer(updatedServer)
                    projectConfigService.setSelectedServer(updatedServer.id)
                }

                currentServer = updatedServer
                loadInitialState()
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
                            preloadedMembers = membersResponse.data,
                            onMergeRequestCreated = { createdMr ->
                                refreshMergeRequestsSilently()
                                trackCreatedMergeRequest(createdMr.iid)
                            }
                        )
                        dialog.show()

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
        currentFilterQueryIndicator?.cancel()
        cancelAllPollingJobs()
        mergeRequestChangesJob?.cancel()
        refreshMergeRequestJob?.cancel()
        openDiffJob?.cancel()
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
        private var changedFileDoubleClickHandler: ((GitLabMergeRequestChangeFile) -> Unit)? = null

        init {
            // 创建MR列表面板
            mrListPanel = MRListPanel()

            // 创建MR详情面板
            mrDetailsPanel = MRDetailsPanel(project)

            // 使用 IDEA 原生分割面板
            val splitter = com.intellij.ui.JBSplitter(false, 0.6f)
            splitter.dividerWidth = 4
            splitter.divider.border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0)
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
            mrDetailsPanel.setOnChangedFileDoubleClicked { changeFile ->
                changedFileDoubleClickHandler?.invoke(changeFile)
            }
        }

        fun setMergeRequests(mrs: List<GitLabMergeRequest>, hasMore: Boolean = false) {
            mrListPanel.setMergeRequests(mrs, hasMore)
            // 清空详情面板，因为刷新后没有选中任何合并请求
            mrDetailsPanel.clear()
            currentSelectedMergeRequest = null
            selectedMergeRequestIid = null
        }

        fun addMergeRequests(mrs: List<GitLabMergeRequest>) {
            mrListPanel.addMoreMergeRequests(mrs)
        }

        fun updateMRDetails(mr: GitLabMergeRequest) {
            // 切换选中项后停止上一个合并请求的轮询，避免后台持续请求
            selectedMergeRequestIid?.takeIf { it != mr.iid }?.let { stopPollingMergeRequest(it) }
            // 设置当前服务器 URL，用于"在GitLab中打开"功能
            mrDetailsPanel.setCurrentServerUrl(currentServer?.url)
            mrDetailsPanel.setRepositoryRoot(currentRepositoryRoot)
            mrDetailsPanel.setMergeRequest(mr)
            currentSelectedMergeRequest = mr
            selectedMergeRequestIid = mr.iid
            loadMergeRequestChanges(mr)
            if (mr.isMergeStatusPending()) {
                ensurePollingMergeRequest(mr.iid)
            }
            refreshMergeRequestSilently(mr.iid)
        }

        /**
         * 列表接口可能不返回流水线/详细合并状态，选中时静默拉取单条MR纠正展示
         */
        private fun refreshMergeRequestSilently(mrIid: Long) {
            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return
            if (!silentRefreshInFlight.add(mrIid)) return
            val version = nextMrRequestVersion(mrIid)

            launch {
                try {
                    val response = apiClient.getMergeRequest(projectId, mrIid)
                    if (!isLatestMrRequestVersion(mrIid, version)) return@launch
                    val latest = response.data ?: return@launch
                    ApplicationManager.getApplication().invokeLater {
                        if (!isLatestMrRequestVersion(mrIid, version)) return@invokeLater
                        if (selectedMergeRequestIid != mrIid) return@invokeLater
                        refreshMRInList(latest, reloadChanges = false)
                    }
                } catch (_: CancellationException) {
                } catch (_: Exception) {
                } finally {
                    silentRefreshInFlight.remove(mrIid)
                }
            }
        }

        fun setRepositoryRoot(root: VirtualFile?) {
            currentRepositoryRoot = root
            mrDetailsPanel.setRepositoryRoot(root)
        }

        fun getOrderedChangeFiles(): List<GitLabMergeRequestChangeFile> = mrDetailsPanel.getOrderedChangeFiles()

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

        fun setOnRefreshMR(callback: (GitLabMergeRequest) -> Unit) {
            mrDetailsPanel.setOnRefreshMR(callback)
        }

        fun setOnChangedFileDoubleClicked(callback: (GitLabMergeRequestChangeFile) -> Unit) {
            changedFileDoubleClickHandler = callback
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
         * 处理合并MR：先刷新真实合并状态，再按阻塞原因决定直接合并、设置自动合并或提示原因。
         */
        fun handleMergeMR(mr: GitLabMergeRequest) {
            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "合并合并请求", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "正在获取最新合并状态..."

                    val latestMR = fetchLatestMergeRequest(apiClient, projectId, mr)
                    refreshInListIfChanged(latestMR, mr)

                    // 流水线运行中：只能设置"流水线通过后自动合并"
                    if (isPipelineGated(latestMR)) {
                        if (confirmAutoMerge(latestMR)) {
                            mergeMergeRequest(apiClient, projectId, latestMR, indicator, mergeWhenPipelineSucceeds = true)
                        }
                        return
                    }

                    val blockedMessage = mergeBlockedMessage(latestMR)
                    if (blockedMessage != null) {
                        if (latestMR.isMergeStatusPending()) {
                            ApplicationManager.getApplication().invokeLater {
                                ensurePollingMergeRequest(latestMR.iid)
                            }
                            ApplicationManager.getApplication().invokeLater {
                                GitLabNotifications.showInfo(project, "合并状态检查中", blockedMessage)
                            }
                        } else {
                            ApplicationManager.getApplication().invokeLater {
                                GitLabNotifications.showError(project, "合并失败", blockedMessage)
                            }
                        }
                        return
                    }

                    mergeMergeRequest(apiClient, projectId, latestMR, indicator, mergeWhenPipelineSucceeds = false)
                }

                override fun onThrowable(error: Throwable) {
                    super.onThrowable(error)
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(project, "合并失败", error.message ?: "未知错误")
                    }
                }

                private fun mergeMergeRequest(
                    apiClient: GitLabApiClient,
                    projectId: String,
                    mr: GitLabMergeRequest,
                    indicator: ProgressIndicator,
                    mergeWhenPipelineSucceeds: Boolean
                ) {
                    indicator.text = if (mergeWhenPipelineSucceeds) "正在设置自动合并..." else "正在合并请求..."
                    val response = runBlocking {
                        apiClient.mergeMergeRequest(
                            projectId = projectId,
                            mergeRequestIid = mr.iid,
                            shouldRemoveSourceBranch = mr.forceRemoveSourceBranch,
                            mergeWhenPipelineSucceeds = mergeWhenPipelineSucceeds
                        )
                    }

                    if (response.success) {
                        val rawUpdated = response.data
                        val updated = when {
                            rawUpdated == null && mergeWhenPipelineSucceeds -> mr.copy(autoMergeEnabled = true)
                            rawUpdated != null && mergeWhenPipelineSucceeds && !rawUpdated.autoMergeEnabled ->
                                rawUpdated.copy(autoMergeEnabled = true)
                            else -> rawUpdated
                        }
                        ApplicationManager.getApplication().invokeLater {
                            if (updated != null) {
                                refreshMRInList(updated, reloadChanges = !mergeWhenPipelineSucceeds)
                            }
                            if (mergeWhenPipelineSucceeds) {
                                ensurePollingMergeRequest(mr.iid)
                                GitLabNotifications.showSuccess(
                                    project,
                                    "已设置自动合并",
                                    "流水线通过后将自动合并该合并请求"
                                )
                            } else {
                                val message = if (mr.forceRemoveSourceBranch) {
                                    "合并请求已成功合并，源分支将被删除"
                                } else {
                                    "合并请求已成功合并"
                                }
                                GitLabNotifications.showSuccess(project, "合并成功", message)
                            }
                        }
                        return
                    }

                    // 失败兜底：刷新真实状态，按最新状态给出可读原因
                    val latestMR = fetchLatestMergeRequest(apiClient, projectId, mr)
                    refreshInListIfChanged(latestMR, mr)

                    if (response.statusCode == 405 && !mergeWhenPipelineSucceeds &&
                        (latestMR.blockReason == MergeBlockReason.CI_RUNNING || latestMR.isPipelineRunning())
                    ) {
                        if (confirmAutoMerge(latestMR)) {
                            mergeMergeRequest(apiClient, projectId, latestMR, indicator, mergeWhenPipelineSucceeds = true)
                        }
                        return
                    }

                    val message = when {
                        response.statusCode == 409 -> "目标分支已更新，请刷新后再合并"
                        response.statusCode == 405 -> (mergeBlockedMessage(latestMR)
                            ?: "GitLab 判定该合并请求当前不可合并，请检查流水线、审批、冲突或草稿状态") +
                            mergeStatusDiagnostics(latestMR)
                        else -> mergeBlockedMessage(latestMR) ?: response.error ?: "未知错误"
                    }
                    ApplicationManager.getApplication().invokeLater {
                        GitLabNotifications.showError(project, "合并失败", message)
                    }
                }
            })
        }

        private fun fetchLatestMergeRequest(
            apiClient: GitLabApiClient,
            projectId: String,
            mr: GitLabMergeRequest
        ): GitLabMergeRequest {
            val response = runBlocking { apiClient.getMergeRequest(projectId, mr.iid) }
            return if (response.success && response.data != null) response.data else mr
        }

        private fun refreshInListIfChanged(latestMR: GitLabMergeRequest, originalMR: GitLabMergeRequest) {
            if (latestMR == originalMR) return
            ApplicationManager.getApplication().invokeLater {
                refreshMRInList(latestMR, reloadChanges = false)
            }
        }

        /**
         * 流水线运行中时，询问是否设置为流水线通过后自动合并。
         */
        private fun confirmAutoMerge(mr: GitLabMergeRequest): Boolean {
            var confirmed = false
            ApplicationManager.getApplication().invokeAndWait {
                confirmed = Messages.showYesNoDialog(
                    project,
                    "合并请求 !${mr.iid} 的流水线正在运行，是否在流水线通过后自动合并？",
                    "流水线通过后自动合并",
                    "设置自动合并",
                    "取消",
                    Messages.getQuestionIcon()
                ) == Messages.YES
            }
            return confirmed
        }

        /**
         * 当前状态不可直接合并时返回原因；可合并或原因未知（交给接口兜底）时返回 null。
         */
        private fun mergeBlockedMessage(mr: GitLabMergeRequest): String? {
            return when {
                mr.state == MergeRequestState.LOCKED -> "存在合并冲突，请先解决冲突"
                mr.state == MergeRequestState.CHECKING -> "正在检查合并状态，请稍候再合并"
                mr.state != MergeRequestState.OPENED -> "当前合并请求状态不可合并"
                else -> when (mr.blockReason) {
                    MergeBlockReason.NONE, MergeBlockReason.UNKNOWN -> null
                    MergeBlockReason.CI_RUNNING -> "流水线正在运行，暂不能直接合并"
                    MergeBlockReason.CI_FAILED -> "流水线未通过，请修复后重试"
                    MergeBlockReason.CONFLICT -> "存在合并冲突，请先解决冲突"
                    MergeBlockReason.NEED_REBASE -> "源分支需要变基，请先变基后再合并"
                    MergeBlockReason.APPROVAL -> "合并请求尚未审批通过"
                    MergeBlockReason.REQUESTED_CHANGES -> "合并请求有待处理的修改意见"
                    MergeBlockReason.DISCUSSIONS -> "存在未解决的讨论"
                    MergeBlockReason.DRAFT -> "合并请求仍是草稿状态"
                    MergeBlockReason.EXTERNAL_CHECK -> "外部状态检查尚未通过"
                    MergeBlockReason.BLOCKED -> "合并请求被其他合并请求阻塞"
                }
            }
        }

        /**
         * 流水线正在运行且可能阻塞合并。
         * 有些GitLab版本不返回detailed_merge_status，此时只能靠head_pipeline推断。
         */
        private fun isPipelineGated(mr: GitLabMergeRequest): Boolean {
            return mr.state == MergeRequestState.OPENED &&
                (mr.blockReason == MergeBlockReason.CI_RUNNING ||
                    (mr.blockReason == MergeBlockReason.UNKNOWN && mr.isPipelineRunning()))
        }

        /**
         * 合并失败时附带原始状态，便于排查GitLab返回的阻塞原因
         */
        private fun mergeStatusDiagnostics(mr: GitLabMergeRequest): String {
            val statuses = listOfNotNull(
                mr.mergeStatusRaw?.let { "merge_status=$it" },
                mr.detailedMergeStatusRaw?.let { "detailed_merge_status=$it" },
                mr.headPipelineStatus?.let { "pipeline=$it" },
                "blockReason=${mr.blockReason.name}"
            )
            return "\n\n状态信息：" + statuses.joinToString(", ")
        }

        /**
         * 处理删除MR
         */
        fun handleDeleteMR(mr: GitLabMergeRequest) {
            // 显示确认对话框
            if (!com.nathan.gitlabmr.toolwindow.dialog.MRActionConfirmDialog.confirmDelete(project, mr)) {
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
                            currentSelectedMergeRequest = null
                            selectedMergeRequestIid = null
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
        fun handleRefreshMR(mr: GitLabMergeRequest) {
            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return

            refreshMergeRequestJob?.cancel()
            mrDetailsPanel.setMergeRequestRefreshing(true)
            mrDetailsPanel.setMergeRequestChangesLoading()
            val version = nextMrRequestVersion(mr.iid)

            refreshMergeRequestJob = launch {
                try {
                    val response = apiClient.getMergeRequest(projectId, mr.iid)
                    ApplicationManager.getApplication().invokeLater {
                        if (!isLatestMrRequestVersion(mr.iid, version)) return@invokeLater
                        if (selectedMergeRequestIid != mr.iid) return@invokeLater

                        if (response.success && response.data != null) {
                            refreshMRInList(response.data)
                        } else {
                            mrDetailsPanel.setMergeRequestRefreshing(false)
                            mrDetailsPanel.setMergeRequestChangesError(response.error)
                            GitLabNotifications.showError(project, "刷新失败", response.error ?: "未知错误")
                        }
                    }
                } catch (_: CancellationException) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!isLatestMrRequestVersion(mr.iid, version)) return@invokeLater
                        if (selectedMergeRequestIid == mr.iid) {
                            mrDetailsPanel.setMergeRequestRefreshing(false)
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!isLatestMrRequestVersion(mr.iid, version)) return@invokeLater
                        if (selectedMergeRequestIid == mr.iid) {
                            mrDetailsPanel.setMergeRequestRefreshing(false)
                            mrDetailsPanel.setMergeRequestChangesError(e.message)
                            GitLabNotifications.showError(project, "刷新失败", e.message ?: "未知错误")
                        }
                    }
                }
            }
        }

        fun refreshMRInList(updatedMR: GitLabMergeRequest, reloadChanges: Boolean = true) {
            // 更新 mergeRequests 列表中的MR
            val index = mergeRequests.indexOfFirst { it.iid == updatedMR.iid }
            if (index != -1) {
                mergeRequests[index] = updatedMR
            } else {
                mergeRequests.add(0, updatedMR)
            }

            val shouldInclude = shouldIncludeInCurrentFilter(updatedMR)
            val filteredIndex = filteredMergeRequests.indexOfFirst { it.iid == updatedMR.iid }
            if (filteredIndex != -1 && shouldInclude) {
                filteredMergeRequests = filteredMergeRequests.toMutableList().apply {
                    this[filteredIndex] = updatedMR
                }
            } else if (filteredIndex == -1 && shouldInclude) {
                filteredMergeRequests = listOf(updatedMR) + filteredMergeRequests
            } else if (filteredIndex != -1) {
                filteredMergeRequests = filteredMergeRequests.filter { it.iid != updatedMR.iid }
            }

            // 检查是否需要从列表中移除（因状态变化不再符合筛选条件）
            val shouldRemove = !shouldInclude

            if (shouldRemove) {
                // 从列表中移除该 MR
                mrListPanel.removeMergeRequest(updatedMR.iid)
                // 清空详情面板
                mrDetailsPanel.clear()
                currentSelectedMergeRequest = null
                selectedMergeRequestIid = null
                stopPollingMergeRequest(updatedMR.iid)
            } else {
                // 更新列表中该 MR 的显示
                if (!mrListPanel.updateMergeRequest(updatedMR) && filterState != null) {
                    mrListPanel.addMoreMergeRequests(listOf(updatedMR))
                }
                // 更新详情面板
                if (selectedMergeRequestIid == updatedMR.iid) {
                    val scrollBehavior = if (reloadChanges) {
                        MRDetailsPanel.ScrollBehavior.RESET_TOP
                    } else {
                        MRDetailsPanel.ScrollBehavior.PRESERVE
                    }
                    mrDetailsPanel.setMergeRequest(updatedMR, scrollBehavior)
                    mrDetailsPanel.setCurrentServerUrl(currentServer?.url)
                    currentSelectedMergeRequest = updatedMR
                    selectedMergeRequestIid = updatedMR.iid
                    if (reloadChanges) {
                        loadMergeRequestChanges(updatedMR)
                    }
                }
                if (updatedMR.isMergeStatusPending()) {
                    ensurePollingMergeRequest(updatedMR.iid)
                } else {
                    stopPollingMergeRequest(updatedMR.iid)
                }
            }
        }

        /**
         * 从列表中移除MR
         */
        private fun removeMRFromList(mr: GitLabMergeRequest) {
            // 从数据中移除
            mergeRequests.removeAll { it.iid == mr.iid }
            filteredMergeRequests = filteredMergeRequests.filter { it.iid != mr.iid }
            // 从列表 UI 中移除
            mrListPanel.removeMergeRequest(mr.iid)
            stopPollingMergeRequest(mr.iid)
        }

        private fun loadMergeRequestChanges(mr: GitLabMergeRequest) {
            val apiClient = currentApiClient ?: return
            val projectId = currentProjectId ?: return

            mergeRequestChangesJob?.cancel()
            mrDetailsPanel.setMergeRequestChangesLoading()

            mergeRequestChangesJob = launch {
                try {
                    val response = apiClient.getMergeRequestChanges(projectId, mr.iid)
                    ApplicationManager.getApplication().invokeLater {
                        if (selectedMergeRequestIid != mr.iid) return@invokeLater

                        if (response.success && response.data != null) {
                            mrDetailsPanel.setMergeRequestChanges(response.data)
                        } else {
                            mrDetailsPanel.setMergeRequestChangesError(response.error)
                        }
                        mrDetailsPanel.setMergeRequestRefreshing(false)
                    }
                } catch (_: CancellationException) {
                    // Ignore stale requests when selection changes.
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        if (selectedMergeRequestIid == mr.iid) {
                            mrDetailsPanel.setMergeRequestChangesError(e.message)
                            mrDetailsPanel.setMergeRequestRefreshing(false)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 合并状态轮询间隔：3 秒。 */
        private const val POLLING_INTERVAL_MS = 3_000L

        /** 合并状态轮询上限：流水线时间较长，最多持续 10 分钟。 */
        private const val POLLING_TIMEOUT_MS = 600_000L
    }
}
