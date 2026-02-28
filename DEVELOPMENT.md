# GitLab IDEA 插件开发文档

## 一、项目整体结构

```
gitlab-idea-plugin/
├── src/
│   └── main/
│       ├── resources/
│       │   └── META-INF/
│       │       ├── plugin.xml              # 插件元数据配置
│       │       └── gitlab-git.xml          # Git4Idea 可选依赖配置
│       └── kotlin/
│           └── com/gitlab/idea/
│               ├── GitLabPlugin.kt         # 插件主入口
│               ├── actions/                # 用户操作类
│               │   ├── AddServerAction.kt   # 添加服务器操作
│               │   └── RefreshAction.kt     # 刷新数据操作
│               ├── api/                    # GitLab API 封装
│               │   └── GitLabApiClient.kt  # API 客户端核心类
│               ├── config/                 # 配置管理
│               │   ├── GitLabConfigurable.kt          # 应用级配置面板
│               │   ├── GitLabConfigService.kt         # 应用级配置持久化
│               │   ├── GitLabProjectConfigurable.kt   # 项目级配置面板
│               │   └── GitLabProjectConfigService.kt  # 项目级配置持久化
│               ├── model/                  # 数据模型
│               │   └── GitLabServer.kt     # 服务器、MR、用户、分支等数据类
│               ├── toolwindow/             # 工具窗口
│               │   ├── components/         # UI 组件
│               │   │   ├── EmptyStatePanel.kt      # 空状态面板
│               │   │   ├── ErrorStatePanel.kt      # 错误状态面板
│               │   │   ├── LoadingStatePanel.kt    # 加载状态面板
│               │   │   ├── MRActionToolbar.kt      # MR 操作工具栏
│               │   │   ├── MRDetailsPanel.kt       # MR 详情面板（含自定义组件）
│               │   │   ├── MRListPanel.kt          # MR 列表面板（含筛选）
│               │   │   └── ToolWindowSideToolbar.kt # 侧边工具栏
│               │   ├── dialog/
│               │   │   └── MRActionConfirmDialog.kt # MR 操作确认对话框
│               │   ├── CreateMRDialog.kt           # 创建 MR 对话框
│               │   ├── GitLabServerDialog.kt       # 添加/编辑服务器对话框
│               │   ├── GitLabToolWindowContent.kt  # 工具窗口内容管理（CardLayout）
│               │   ├── GitLabToolWindowFactory.kt  # 工具窗口工厂
│               │   └── ToolWindowMutexManager.kt   # UI 状态管理
│               └── util/                   # 工具类
│                   ├── GitLabNotifications.kt      # 通知工具
│                   └── GitUtil.kt                  # Git 仓库工具
├── build.gradle.kts                # Gradle 构建配置
├── settings.gradle.kts             # Gradle 设置
├── gradle.properties               # Gradle 属性
├── gradlew.bat                     # Gradle 包装器脚本 (Windows)
└── README.md                       # 项目说明文档
```

## 二、开发环境配置说明

### 2.1 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 必须使用 JDK 17 |
| IntelliJ IDEA | 2023.2+ | 推荐 Ultimate 版本 |
| Gradle | 8.4+ | 自动管理，无需单独安装 |
| Kotlin | 2.1.0 | 编译语言 |
| IntelliJ Platform SDK | 2024.2+ | 插件开发 SDK |

### 2.2 IDEA SDK 配置步骤

1. **打开项目结构**
   ```
   File -> Project Structure (Ctrl+Alt+Shift+S)
   ```

2. **配置 Project SDK**
   - 左侧选择 `Project`
   - SDK 下拉选择 `Java 17`（如未安装，点击 `Download JDK`）
   - Language level 设为 `17 - Sealed types, pattern matching`

3. **配置插件开发 SDK**（仅 Ultimate 需要）
   - 左侧选择 `SDKs`
   - 添加 `IntelliJ Platform Plugin SDK`
   - 选择当前 IDEA 安装目录

### 2.3 依赖说明

```kotlin
// 核心依赖
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

dependencies {
    // Kotlin 标准库
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")

    // JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // HTTP 客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // IntelliJ Platform（包含 bundled kotlinx-coroutines）
    intellijPlatform {
        create("IC", "2024.2")
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
    }
}
```

### 2.4 国内用户镜像配置

在 `build.gradle.kts` 的 `repositories` 块中添加阿里云镜像：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}
```

## 三、核心代码说明

### 3.1 数据模型 (model/GitLabServer.kt)

```kotlin
// GitLab 服务器配置
@Tag("GitLabServer")
data class GitLabServer(
    @SerializedName("id")
    var id: String = "",
    @SerializedName("name")
    var name: String = "",
    @SerializedName("url")
    var url: String = "",
    @SerializedName("token")
    var token: String = "",
    @SerializedName("isDefault")
    var isDefault: Boolean = false  // true=应用级, false=项目级
)

// 合并请求状态枚举
enum class MergeRequestState(val displayName: String) {
    OPENED("OPENED"),
    CLOSED("CLOSED"),
    LOCKED("LOCKED"),
    MERGED("MERGED")
}

// 合并请求（完整字段）
data class GitLabMergeRequest(
    val id: Long,
    val iid: Long,
    val projectId: Long,
    val title: String,
    val description: String?,
    val state: MergeRequestState,
    val sourceBranch: String,
    val targetBranch: String,
    val author: GitLabUser,
    val assignees: List<GitLabUser>,
    val reviewers: List<GitLabUser>,
    val mergedBy: List<GitLabUser>,
    val createdAt: String,
    val updatedAt: String,
    val mergedAt: String?,
    val closedAt: String?,
    val webUrl: String,
    val draft: Boolean = false,
    val workInProgress: Boolean = false,
    val hasConflicts: Boolean = false,
    val labels: List<String> = emptyList(),
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val userNotesCount: Int = 0,
    val forceRemoveSourceBranch: Boolean = false
)

// GitLab 用户信息
data class GitLabUser(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String,
    @SerializedName("state") val state: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("web_url") val webUrl: String? = null
)

// GitLab 项目信息
data class GitLabProject(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("name_with_namespace") val nameWithNamespace: String,
    @SerializedName("path") val path: String,
    @SerializedName("path_with_namespace") val pathWithNamespace: String,
    @SerializedName("web_url") val webUrl: String,
    @SerializedName("default_branch") val defaultBranch: String?
)

// GitLab 分支信息
data class GitLabBranch(
    @SerializedName("name") val name: String,
    @SerializedName("merged") val merged: Boolean,
    @SerializedName("protected") val protected: Boolean,
    @SerializedName("default") val default: Boolean,
    @SerializedName("commit") val commit: GitLabCommit
)

// GitLab 提交信息
data class GitLabCommit(
    @SerializedName("id") val id: String,
    @SerializedName("short_id") val shortId: String,
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("author_name") val authorName: String,
    @SerializedName("committed_date") val committedDate: String
)

// 项目成员
data class GitLabMember(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String,
    @SerializedName("name") val name: String,
    @SerializedName("access_level") val accessLevel: Int
)

// 创建 MR 请求
data class CreateMergeRequestRequest(
    @SerializedName("source_branch") val sourceBranch: String,
    @SerializedName("target_branch") val targetBranch: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("assignee_id") val assigneeId: Long? = null,
    @SerializedName("remove_source_branch") val removeSourceBranch: Boolean? = null
)

// API 响应包装类
data class GitLabApiResponse<T>(
    val data: T?,
    val success: Boolean,
    val error: String? = null,
    val statusCode: Int = -1
)
```

### 3.2 API 客户端 (api/GitLabApiClient.kt)

核心 API 方法：

```kotlin
class GitLabApiClient(server: GitLabServer, private val project: Project? = null) : Disposable {
    
    // ==================== 连接测试 ====================
    suspend fun testConnection(): GitLabApiResponse<Map<String, Any>>
    suspend fun getCurrentUser(): GitLabApiResponse<GitLabUser>
    
    // ==================== 项目相关 ====================
    suspend fun getProject(projectPath: String): GitLabApiResponse<GitLabProject>
    suspend fun matchProjectByUrl(repositoryUrl: String): GitLabApiResponse<GitLabProject>
    suspend fun getUserProjects(page: Int = 1, perPage: Int = 20): GitLabApiResponse<List<GitLabProject>>
    
    // ==================== 合并请求 ====================
    suspend fun getMergeRequests(
        projectId: String,
        state: String = "all",
        page: Int = 1,
        perPage: Int = 20,
        search: String? = null,
        scope: String? = null
    ): GitLabApiResponse<List<GitLabMergeRequest>>
    
    suspend fun getMergeRequest(projectId: String, mergeRequestIid: Long): GitLabApiResponse<GitLabMergeRequest>
    suspend fun getAllMergeRequests(projectId: String, state: String = "all", indicator: ProgressIndicator? = null): GitLabApiResponse<List<GitLabMergeRequest>>
    
    // ==================== 分支相关 ====================
    suspend fun getProjectBranches(projectId: String, search: String? = null): GitLabApiResponse<List<GitLabBranch>>
    suspend fun getBranchCommit(projectId: String, branchName: String): GitLabApiResponse<GitLabCommit>
    
    // ==================== 成员相关 ====================
    suspend fun getProjectMembers(projectId: String): GitLabApiResponse<List<GitLabMember>>
    
    // ==================== 创建 MR ====================
    suspend fun createMergeRequest(
        projectId: String,
        request: CreateMergeRequestRequest
    ): GitLabApiResponse<CreateMergeRequestResponse>
    
    // ==================== MR 操作 ====================
    suspend fun closeMergeRequest(projectId: String, mergeRequestIid: Long): GitLabApiResponse<GitLabMergeRequest>
    suspend fun mergeMergeRequest(
        projectId: String,
        mergeRequestIid: Long,
        shouldRemoveSourceBranch: Boolean = false
    ): GitLabApiResponse<GitLabMergeRequest>
    suspend fun deleteMergeRequest(projectId: String, mergeRequestIid: Long): GitLabApiResponse<Unit>
    
    // 资源释放
    override fun dispose()
}
```

### 3.3 双认证方式支持

API 客户端支持两种 GitLab 认证方式：

```kotlin
/**
 * 方式一：URL 参数认证（浏览器兼容方式）
 */
private fun String.withAuthToken(): String {
    return if (this.contains("?")) {
        "$this&private_token=${java.net.URLEncoder.encode(privateToken, "UTF-8")}"
    } else {
        "$this?private_token=${java.net.URLEncoder.encode(privateToken, "UTF-8")}"
    }
}

// 使用：
val url = "$apiBaseUrl/user".withAuthToken()

/**
 * 方式二：Header 认证（标准方式，更可靠）
 */
val request = Request.Builder()
    .url("$apiBaseUrl/user")
    .header("PRIVATE-TOKEN", privateToken.trim())
    .header("Accept", "application/json")
    .get()
    .build()

/**
 * 连接测试时优先尝试 URL 参数方式，失败后自动回退到 Header 方式
 */
suspend fun testConnection(): GitLabApiResponse<Map<String, Any>> = withContext(Dispatchers.IO) {
    // 先尝试 URL 参数方式
    val response1 = client.newCall(requestWithUrlParam).execute()
    if (response1.isSuccessful) return success
    
    // 失败后尝试 Header 方式
    val response2 = client.newCall(requestWithHeader).execute()
    if (response2.isSuccessful) return success
    
    // 都失败返回错误
    return error
}
```

### 3.4 配置服务

#### 应用级配置 (config/GitLabConfigService.kt)

```kotlin
@Service(Service.Level.APP)
@State(
    name = "GitLabConfigService",
    storages = [Storage("GitLabConfig.xml")]
)
class GitLabConfigService : PersistentStateComponent<GitLabConfigService.State> {
    data class State(
        var servers: MutableList<GitLabServer> = mutableListOf(),
        var selectedServerId: String? = null
    )
    
    fun addServer(server: GitLabServer)
    fun removeServer(serverId: String)
    fun updateServer(server: GitLabServer)
    fun getServers(): List<GitLabServer>
    fun getDefaultServers(): List<GitLabServer>
    fun getSelectedServer(): GitLabServer?
    fun setSelectedServer(serverId: String?)
}
```

#### 项目级配置 (config/GitLabProjectConfigService.kt)

```kotlin
@Service(Service.Level.PROJECT)
@State(
    name = "GitLabProjectConfigService",
    storages = [Storage("GitLabProjectConfig.xml")]
)
class GitLabProjectConfigService(private val project: Project) : PersistentStateComponent<GitLabProjectConfigService.State> {
    data class State(
        var servers: MutableList<GitLabServer> = mutableListOf(),
        var selectedServerId: String? = null
    )
    
    // 与应用级配置相同的接口
    fun addServer(server: GitLabServer)
    fun removeServer(serverId: String)
    fun updateServer(server: GitLabServer)
    fun getAllServers(): List<GitLabServer>
    fun getSelectedServer(): GitLabServer?
    fun setSelectedServer(serverId: String?)
}
```

### 3.5 工具窗口架构

#### CardLayout 状态管理

```kotlin
class GitLabToolWindowContent(private val project: Project, private val toolWindow: ToolWindow) : Disposable {
    private val mainPanel: JPanel = JPanel(CardLayout())
    private val cardLayout: CardLayout = mainPanel.layout as CardLayout
    
    // 四种状态面板
    private val emptyStatePanel: EmptyStatePanel   // 无服务器配置
    private val errorStatePanel: ErrorStatePanel   // 加载/连接错误
    private val loadingStatePanel: LoadingStatePanel // 数据加载中
    private val mainContentPanel: MainContentPanel  // MR 列表和详情
    
    enum class CardState { EMPTY, ERROR, LOADING, MAIN }
    
    private fun showCard(state: CardState) {
        cardLayout.show(mainPanel, state.name)
        when (state) {
            CardState.MAIN -> toolWindow.title = currentProject?.nameWithNamespace ?: "GitLab"
            else -> toolWindow.title = ""
        }
    }
}
```

#### 主内容面板结构

```kotlin
inner class MainContentPanel : JPanel(BorderLayout()) {
    private val sideToolbar: ToolWindowSideToolbar      // 左侧工具栏
    private val mrListPanel: MRListPanel                // MR 列表面板（左）
    private val mrDetailsPanel: MRDetailsPanel          // MR 详情面板（右）
    
    init {
        // 使用 JBSplitter 实现可拖拽分割
        val splitter = com.intellij.ui.JBSplitter(false, 0.6f)
        splitter.firstComponent = mrListPanel
        splitter.secondComponent = mrDetailsPanel
        
        add(sideToolbar, BorderLayout.WEST)
        add(splitter, BorderLayout.CENTER)
    }
}
```

### 3.6 UI 组件说明

| 组件 | 文件 | 功能 |
|------|------|------|
| 空状态面板 | EmptyStatePanel.kt | 显示"请添加GitLab服务"提示 |
| 错误面板 | ErrorStatePanel.kt | 显示错误信息和重试/编辑按钮 |
| 加载面板 | LoadingStatePanel.kt | 显示加载动画和提示文字 |
| MR 列表面板 | MRListPanel.kt | 显示 MR 列表、状态筛选、搜索框、加载更多 |
| MR 详情面板 | MRDetailsPanel.kt | 显示 MR 完整信息，含圆角标签、分支流向 |
| MR 操作工具栏 | MRActionToolbar.kt | 在浏览器打开、复制链接、关闭、合并、删除按钮 |
| 侧边工具栏 | ToolWindowSideToolbar.kt | 设置、刷新、创建 MR 按钮 |

### 3.7 创建 MR 对话框

```kotlin
class CreateMRDialog(
    private val project: Project,
    private val server: GitLabServer,
    private val projectId: String,
    preloadedBranches: List<GitLabBranch>? = null,
    preloadedMembers: List<GitLabMember>? = null
) : DialogWrapper(project, true) {
    
    // UI 组件
    private val sourceBranchField = ComboBox<String>()
    private val targetBranchField = ComboBox<String>()
    private val titleField = JBTextField()
    private val descriptionArea = JBTextArea(5, 40)
    private val assigneeField = ComboBox<String>()
    private val removeSourceBranchCheckbox = JCheckBox("合并后删除源分支")
    
    // 功能特性
    // 1. 从源分支最新提交自动填充标题和描述
    // 2. 分支智能排序（master/main 优先，按提交时间）
    // 3. "合并当前分支"快捷按钮
    // 4. 预加载分支和成员数据（5秒超时）
    // 5. 手动编辑检测（避免自动填充覆盖用户输入）
}
```

## 四、GitLab API 调用详解

### 4.1 API 端点列表

| 端点 | 方法 | 用途 |
|------|------|------|
| `/user` | GET | 获取当前用户信息 |
| `/projects` | GET | 获取用户项目列表 |
| `/projects/:id` | GET | 获取项目详情 |
| `/projects/:id/merge_requests` | GET | 获取 MR 列表（支持分页、筛选、搜索） |
| `/projects/:id/merge_requests/:iid` | GET | 获取单个 MR |
| `/projects/:id/merge_requests` | POST | 创建 MR |
| `/projects/:id/merge_requests/:iid/merge` | PUT | 合并 MR |
| `/projects/:id/merge_requests/:iid` | PUT | 更新 MR（关闭时使用 state_event） |
| `/projects/:id/merge_requests/:iid` | DELETE | 删除 MR |
| `/projects/:id/repository/branches` | GET | 获取分支列表 |
| `/projects/:id/repository/branches/:branch` | GET | 获取分支提交信息 |
| `/projects/:id/members/all` | GET | 获取项目所有成员（含继承） |

### 4.2 项目路径编码

GitLab API 要求对项目路径中的 `/` 进行 URL 编码：

```kotlin
// 路径编码：group/subgroup/project -> group%2Fsubgroup%2Fproject
val encodedPath = java.net.URLEncoder.encode(projectPath, "UTF-8")
val url = "$apiBaseUrl/projects/$encodedPath"

// 数字 ID 不需要编码
val encodedProjectId = if (projectId.all { it.isDigit() }) {
    projectId
} else {
    java.net.URLEncoder.encode(projectId, "UTF-8")
}
```

### 4.3 分页处理

```kotlin
// 自动分页获取所有 MR
suspend fun getAllMergeRequests(
    projectId: String,
    state: String = "all",
    indicator: ProgressIndicator? = null
): GitLabApiResponse<List<GitLabMergeRequest>> = withContext(Dispatchers.IO) {
    val allMrs = mutableListOf<GitLabMergeRequest>()
    var page = 1
    var hasMore = true

    while (hasMore) {
        indicator?.checkCanceled()
        indicator?.text2 = "Loading page $page..."

        val response = getMergeRequests(projectId, state, page, perPage = 100)

        if (response.success && response.data != null) {
            allMrs.addAll(response.data)
            hasMore = response.data.size >= 100
            page++
        } else {
            hasMore = false
        }
    }

    GitLabApiResponse(allMrs, true, null, 200)
}

// 成员列表分页（使用 Link Header）
do {
    val url = "$apiBaseUrl/projects/$encodedProjectId/members/all?page=$page&per_page=$perPage"
    val response = client.newCall(request).execute()
    
    if (response.isSuccessful) {
        val members = parseMembers(response.body?.string())
        allMembers.addAll(members)
        
        // 检查 Link Header 是否包含 next
        val linkHeader = response.header("Link")
        hasMore = linkHeader != null && linkHeader.contains("rel=\"next\"")
        page++
    }
} while (hasMore)
```

### 4.4 搜索和筛选参数

```kotlin
suspend fun getMergeRequests(
    projectId: String,
    state: String = "all",           // opened, closed, locked, merged, all
    page: Int = 1,
    perPage: Int = 20,
    search: String? = null,          // 在标题和描述中搜索
    scope: String? = null            // created_by_me, assigned_to_me, all
): GitLabApiResponse<List<GitLabMergeRequest>>
```

### 4.5 异常处理

```kotlin
try {
    val response = apiClient.getProject(projectPath)
    if (response.success) {
        val project = response.data
        // 处理成功
    } else {
        // 处理业务错误
        showError("获取项目失败", response.error)
    }
} catch (e: CancellationException) {
    // 用户取消操作，不处理
} catch (e: UnknownHostException) {
    showError("网络错误", "无法连接到GitLab服务器，请检查网络连接")
} catch (e: SocketTimeoutException) {
    showError("网络错误", "连接超时，请稍后重试")
} catch (e: Exception) {
    showError("未知错误", e.message)
}
```

## 五、协程与线程处理

### 5.1 后台任务与 UI 更新

```kotlin
// 使用 IDEA 的 ProgressManager 执行后台任务
ProgressManager.getInstance().run(object : Task.Backgroundable(
    project, 
    "Loading GitLab Merge Requests", 
    true  // canBeCancelled
) {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        
        runBlocking {
            val response = apiClient.getMergeRequests(projectId)
            
            // 切换到主线程更新 UI
            ApplicationManager.getApplication().invokeLater {
                if (response.success) {
                    updateUI(response.data)
                } else {
                    showError(response.error)
                }
            }
        }
    }
    
    override fun onThrowable(error: Throwable) {
        super.onThrowable(error)
        ApplicationManager.getApplication().invokeLater {
            showError(error.message)
        }
    }
})
```

### 5.2 Disposable 资源管理

```kotlin
class GitLabApiClient : Disposable {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun someAsyncOperation() {
        coroutineScope.launch {
            // 执行异步操作
        }
    }
    
    override fun dispose() {
        coroutineScope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

class GitLabToolWindowContent : Disposable, CoroutineScope {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override val coroutineContext = coroutineScope.coroutineContext
    
    override fun dispose() {
        coroutineScope.cancel()
    }
}
```

### 5.3 并行数据加载

```kotlin
// 并行加载分支和成员数据
launch {
    try {
        withTimeout(5000L) {
            val branchesDeferred = async { apiClient.getProjectBranches(projectId) }
            val membersDeferred = async { apiClient.getProjectMembers(projectId) }
            
            val branchesResponse = branchesDeferred.await()
            val membersResponse = membersDeferred.await()
            
            // 处理结果
        }
    } catch (e: TimeoutCancellationException) {
        showError("加载超时", "加载数据超过5秒")
    }
}
```

## 六、插件打包与发布

### 6.1 使用 Gradle 命令行

```bash
# Windows
gradlew.bat clean buildPlugin

# macOS/Linux
./gradlew clean buildPlugin
```

### 6.2 输出文件

```
build/distributions/gitlab-idea-plugin-1.0.0.zip
```

### 6.3 本地测试运行

1. **配置运行环境**
   - 打开 `Run -> Edit Configurations`
   - 点击 `+` 添加 `Plugin` 类型的配置
   - 设置 `VM options`（如需增加内存）：
     ```
     -Xmx2g -Xms2g
     ```

2. **运行插件**
   - 点击运行按钮或按 `Shift+F10`
   - 会启动一个新的 IDEA 实例（沙箱环境）
   - 新实例中已加载插件

### 6.4 发布到 JetBrains Marketplace

1. 注册开发者账号：https://plugins.jetbrains.com/
2. 准备插件材料：
   - 插件 ZIP 文件
   - 图标（logo.png，40x40 或 80x80）
   - 描述文档
3. 填写插件信息并上传
4. 等待审核（通常 1-3 个工作日）

构建配置中的签名和发布任务：

```kotlin
tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("253.*")
    }
    
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }
    
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
```

## 七、常见问题排查方案

### 7.1 连接 GitLab 失败

**症状**: 测试连接时返回错误

**排查步骤**:
1. 确认 URL 格式正确（`https://gitlab.com` 或自托管地址，无尾部斜杠）
2. 验证 API Token 是否有效：
   - 登录 GitLab
   - Preferences -> Access Tokens
   - 确认 Token 未过期且有以下权限：
     - `api`
     - `read_api`
     - `read_repository`
     - `read_milestone`
     - `read_issue`
     - `read_merge_request`
3. 检查网络连接：
   - 浏览器访问 GitLab URL
   - 检查代理设置
4. 查看 IDEA 日志：
   ```
   Help -> Show Log in Explorer
   ```

### 7.2 MR 拉取失败

**症状**: 配置成功但无法加载合并请求

**排查步骤**:
1. 确认当前项目是 Git 仓库
2. 检查远程仓库 URL：
   ```bash
   git remote -v
   ```
3. 验证远程 URL 指向 GitLab
4. 确认 Token 对该项目有读取权限：
   - 项目 -> Settings -> Members
   - 检查用户角色至少为 Developer
5. 尝试手动调用 API：
   ```bash
   curl -H "PRIVATE-TOKEN: <your_token>" \
        "https://gitlab.com/api/v4/projects/<project_id>/merge_requests?state=all"
   ```

### 7.3 创建 MR 失败

**症状**: 创建 MR 时提示分支不存在

**排查步骤**:
1. 确认源分支已推送到远程：
   ```bash
   git push origin <branch-name>
   ```
2. 检查分支名称拼写
3. 确认对项目有 Developer 或以上权限

### 7.4 插件不显示

**症状**: 工具窗口找不到

**排查步骤**:
1. 确认插件已启用：
   ```
   Settings -> Plugins -> Installed -> GitLab MR
   ```
2. 检查 IDEA 版本（需 >= 2024.2）
3. 查看日志文件：
   ```
   Help -> Show Log in Explorer
   ```
   搜索 "GitLab" 关键字

### 7.5 构建失败

**症状**: Gradle 构建报错

**排查步骤**:
1. 检查 JDK 版本（必须 17）：
   ```bash
   java -version
   ```
2. 清理 Gradle 缓存：
   ```bash
   gradlew.bat clean --no-daemon
   ```
3. 删除 `.gradle` 和 `build` 目录后重新构建
4. 配置镜像源（国内用户）
5. 增加 Gradle 内存（`gradle.properties`）：
   ```properties
   org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
   ```

## 八、调试技巧

### 8.1 日志输出

使用 IDEA 的日志系统：

```kotlin
import com.intellij.openapi.diagnostic.Logger

private val logger = Logger.getInstance(GitLabApiClient::class.java)

logger.info("Loading merge requests...")
logger.warn("API rate limit exceeded")
logger.error("Failed to connect", exception)
```

或临时使用 println（开发调试）：

```kotlin
println("GitLab API Debug:")
println("  URL: $apiBaseUrl/user")
println("  Token: ${privateToken.take(4)}... (length: ${privateToken.length})")
```

### 8.2 断点调试

1. 在代码行号处点击设置断点
2. 使用 Debug 模式运行（Shift+F9）
3. 在沙箱实例中触发操作
4. 断点命中后可查看变量值、调用栈

### 8.3 网络请求调试

启用 OkHttp 日志（需要添加依赖）：

```kotlin
// build.gradle.kts 中添加
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// 代码中使用
val httpClient = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .build()
```

## 九、扩展开发指南

### 9.1 添加新的 API 端点

在 `GitLabApiClient.kt` 中添加：

```kotlin
// 1. 定义响应数据类（如需要）
data class Pipeline(
    val id: Long,
    val status: String,
    val ref: String
)

// 2. 添加 API 方法
suspend fun getPipelines(projectId: String): GitLabApiResponse<List<Pipeline>> = withContext(Dispatchers.IO) {
    try {
        val encodedProjectId = encodeProjectId(projectId)
        val url = "$apiBaseUrl/projects/$encodedProjectId/pipelines".withAuthToken()
        
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        
        if (response.isSuccessful) {
            val body = response.body?.string()
            val type = object : TypeToken<List<Pipeline>>() {}.type
            val pipelines = gson.fromJson<List<Pipeline>>(body, type)
            GitLabApiResponse(pipelines, true, null, response.code)
        } else {
            GitLabApiResponse(null, false, parseError(response.body?.string()), response.code)
        }
    } catch (e: Exception) {
        GitLabApiResponse(null, false, e.message ?: "Unknown error", -1)
    }
}
```

### 9.2 添加新的 UI 面板

1. 创建面板类继承 `JPanel`
2. 在 `GitLabToolWindowContent.kt` 中注册
3. 添加状态切换逻辑

```kotlin
// 1. 创建新面板
class NewFeaturePanel : JPanel(BorderLayout()) {
    init {
        add(JBLabel("新功能"), BorderLayout.CENTER)
    }
}

// 2. 在 GitLabToolWindowContent 中添加
private val newFeaturePanel: NewFeaturePanel

init {
    newFeaturePanel = NewFeaturePanel()
    mainPanel.add(newFeaturePanel, CardState.NEW_FEATURE.name)
}

// 3. 添加切换方法
private fun showNewFeature() {
    cardLayout.show(mainPanel, CardState.NEW_FEATURE.name)
}
```

### 9.3 添加新的 Action

1. 创建 Action 类继承 `AnAction`
2. 在 `plugin.xml` 中注册
3. 实现 `actionPerformed` 方法

```kotlin
// 1. 创建 Action
class MyNewAction : AnAction("My Action", "Description", AllIcons.Actions.Execute) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        // 实现功能
        Messages.showInfoMessage(project, "Hello!", "Title")
    }
}

// 2. 在 plugin.xml 中注册
<actions>
    <action id="GitLab.MyNewAction" 
            class="com.gitlab.idea.actions.MyNewAction"
            text="My New Action"
            description="Description">
        <add-to-group group-id="GitLab.Toolbar" anchor="last"/>
    </action>
</actions>
```

## 十、最佳实践

### 10.1 代码规范

- 使用 Kotlin 官方代码风格
- 优先使用不可变数据类（`val` 而非 `var`）
- 使用 Kotlin 协程处理异步操作
- UI 更新必须在 EDT（Event Dispatch Thread）上执行
- 网络请求在 `Dispatchers.IO` 上执行

### 10.2 错误处理

- 所有 API 调用使用 try-catch 包装
- 用户取消操作（CancellationException）静默处理
- 网络错误提供清晰的错误信息
- 使用 GitLabNotifications 显示通知

### 10.3 性能优化

- 大数据量使用分页加载
- 并行加载独立数据（分支和成员）
- 使用缓存避免重复请求
- 及时释放资源（实现 Disposable）

### 10.4 安全性

- API Token 使用 IDEA 安全存储（PersistentStateComponent）
- 日志中不输出完整 Token
- 使用 HTTPS 与 GitLab 通信
