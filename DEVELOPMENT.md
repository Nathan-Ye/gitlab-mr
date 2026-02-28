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
│               │   ├── GitLabConfigurable.kt          # 全局配置面板
│               │   ├── GitLabConfigService.kt         # 配置持久化服务
│               │   └── GitLabProjectConfigurable.kt   # 项目配置面板
│               ├── model/                  # 数据模型
│               │   └── GitLabServer.kt     # 服务器、MR、用户等数据类
│               ├── toolwindow/             # 工具窗口
│               │   ├── components/         # UI 组件
│               │   │   ├── EmptyStatePanel.kt      # 空状态面板
│               │   │   ├── ErrorStatePanel.kt      # 错误状态面板
│               │   │   ├── MRDetailsPanel.kt       # MR 详情面板
│               │   │   └── MRListPanel.kt          # MR 列表面板
│               │   ├── GitLabServerDialog.kt       # 添加服务器对话框
│               │   ├── GitLabToolWindowContent.kt  # 工具窗口内容管理
│               │   └── GitLabToolWindowFactory.kt  # 工具窗口工厂
│               └── util/                   # 工具类
│                   ├── GitLabNotifications.kt      # 通知工具
│                   └── GitUtil.kt                  # Git 工具
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
| Gradle | 8.4 | 自动管理，无需单独安装 |
| Kotlin | 1.9.20 | 编译语言 |

### 2.2 IDEA SDK 配置步骤

1. **打开项目结构**
   ```
   File -> Project Structure (Ctrl+Alt+Shift+S)
   ```

2. **配置 Project SDK**
   - 左侧选择 `Project`
   - SDK 下拉选择 `Java 17`（如未安装，点击 `Download JDK`）
   - Language level 设为 `17 - Record patterns, pattern matching for switch`

3. **配置插件开发 SDK**（仅 Ultimate 需要）
   - 左侧选择 `SDKs`
   - 添加 `IntelliJ Platform Plugin SDK`
   - 选择当前 IDEA 安装目录

### 2.3 依赖说明

```kotlin
// 核心依赖
dependencies {
    // Kotlin 标准库
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

    // JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // HTTP 客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

### 2.4 国内用户镜像配置

在 `build.gradle.kts` 的 `repositories` 块中添加阿里云镜像：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/central") }
    mavenCentral()
}
```

## 三、核心代码说明

### 3.1 数据模型 (model/GitLabServer.kt)

```kotlin
// GitLab 服务器配置
data class GitLabServer(
    var id: String,              // 唯一标识
    var name: String,            // 服务器名称
    var url: String,             // GitLab URL
    var token: String,           // API Token
    var isProjectLevel: Boolean, // 是否项目级配置
    var projectPath: String?     // 关联项目路径
)

// 合并请求状态枚举
enum class MergeRequestState {
    OPENED,   // 待合并
    CLOSED,   // 已关闭
    LOCKED,   // 有冲突
    MERGED    // 已合并
}

// 合并请求
data class GitLabMergeRequest(
    val id: Long,
    val title: String,
    val state: MergeRequestState,
    val sourceBranch: String,
    val targetBranch: String,
    val author: GitLabUser,
    // ... 更多字段
)
```

### 3.2 API 客户端 (api/GitLabApiClient.kt)

核心 API 方法：

```kotlin
class GitLabApiClient {
    // 测试连接
    suspend fun testConnection(): GitLabApiResponse<Map<String, Any>>

    // 获取项目信息
    suspend fun getProject(projectPath: String): GitLabApiResponse<GitLabProject>

    // 获取合并请求列表
    suspend fun getMergeRequests(
        projectId: String,
        state: String = "all",
        page: Int = 1,
        perPage: Int = 20
    ): GitLabApiResponse<List<GitLabMergeRequest>>

    // 获取所有合并请求（自动分页）
    suspend fun getAllMergeRequests(
        projectId: String,
        state: String = "all",
        indicator: ProgressIndicator?
    ): GitLabApiResponse<List<GitLabMergeRequest>>
}
```

### 3.3 配置服务 (config/GitLabConfigService.kt)

配置持久化使用 IDEA 的 `PersistentStateComponent`：

```kotlin
@Service(Service.Level.APP)
@State(
    name = "GitLabConfigService",
    storages = [Storage("GitLabConfig.xml")]
)
class GitLabConfigService : PersistentStateComponent<GitLabConfigService.State> {
    // 添加服务器
    fun addServer(server: GitLabServer)

    // 删除服务器
    fun removeServer(serverId: String)

    // 获取选中的服务器
    fun getSelectedServer(): GitLabServer?
}
```

### 3.4 工具窗口 (toolwindow/GitLabToolWindowFactory.kt)

工具窗口在 `plugin.xml` 中注册：

```xml
<toolWindow id="GitLab"
            secondary="true"
            anchor="bottom"
            factoryClass="com.gitlab.idea.toolwindow.GitLabToolWindowFactory"/>
```

- `id`: 工具窗口唯一标识
- `secondary`: 是否为次要工具窗口
- `anchor`: 位置（bottom/top/left/right）
- `factoryClass`: 工厂类

### 3.5 UI 组件说明

| 组件 | 文件 | 功能 |
|------|------|------|
| 空状态面板 | EmptyStatePanel.kt | 显示"请添加GitLab服务" |
| 错误面板 | ErrorStatePanel.kt | 显示错误信息和重试按钮 |
| MR列表面板 | MRListPanel.kt | 显示MR列表和筛选器 |
| MR详情面板 | MRDetailsPanel.kt | 显示MR详细信息 |

## 四、GitLab API 调用关键代码

### 4.1 API 请求封装

所有 API 请求使用 OkHttp 发送：

```kotlin
private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private suspend fun <T> executeRequest(
    requestBuilder: Request.Builder.() -> Unit
): GitLabApiResponse<T> = withContext(Dispatchers.IO) {
    val request = Request.Builder().apply(requestBuilder).build()
    val response = client.newCall(request).execute()

    if (response.isSuccessful) {
        // 解析响应
        val body = response.body?.string()
        val data = gson.fromJson(body, type)
        GitLabApiResponse(data, true)
    } else {
        // 处理错误
        val error = parseError(response.body?.string())
        GitLabApiResponse(null, false, error, response.code)
    }
}
```

### 4.2 鉴权处理

GitLab API 使用 Header 鉴权：

```kotlin
val request = Request.Builder()
    .url("$apiBaseUrl/projects/$projectId")
    .header("PRIVATE-TOKEN", privateToken)  // 添加鉴权 Token
    .get()
    .build()
```

### 4.3 分页处理

自动处理分页获取所有数据：

```kotlin
suspend fun getAllMergeRequests(
    projectId: String,
    state: String = "all"
): GitLabApiResponse<List<GitLabMergeRequest>> {
    val allMrs = mutableListOf<GitLabMergeRequest>()
    var page = 1
    var hasMore = true

    while (hasMore) {
        val response = getMergeRequests(projectId, state, page, 100)
        if (response.success && response.data != null) {
            allMrs.addAll(response.data)
            hasMore = response.data.size >= 100
            page++
        } else {
            hasMore = false
        }
    }

    return GitLabApiResponse(allMrs, true)
}
```

### 4.4 异常处理

完整的异常捕获和处理：

```kotlin
try {
    val response = apiClient.getProject(projectPath)
    if (response.success) {
        // 处理成功响应
        val project = response.data
    } else {
        // 处理业务错误
        showError("获取项目失败", response.error)
    }
} catch (e: UnknownHostException) {
    showError("网络错误", "无法连接到GitLab服务器")
} catch (e: SocketTimeoutException) {
    showError("网络错误", "连接超时")
} catch (e: Exception) {
    showError("未知错误", e.message)
}
```

## 五、插件打包步骤

### 5.1 使用 Gradle 命令行

```bash
# Windows
gradlew.bat clean buildPlugin

# macOS/Linux
./gradlew clean buildPlugin
```

### 5.2 使用 IDEA Gradle 面板

1. 打开右侧 `Gradle` 面板
2. 展开 `gitlab-idea-plugin -> Tasks -> intellij`
3. 双击 `buildPlugin`

### 5.3 输出文件

打包完成后，插件文件位于：
```
build/distributions/gitlab-idea-plugin-1.0.0.zip
```

## 六、插件安装与测试

### 6.1 本地测试运行

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

### 6.2 安装到生产环境

1. **从磁盘安装**
   ```
   File -> Settings -> Plugins -> 齿轮图标 -> Install Plugin from Disk...
   ```
2. 选择打包的 ZIP 文件
3. 重启 IDEA

### 6.3 调试插件

1. 使用 Debug 模式运行（Shift+F9）
2. 在沙箱实例中复现问题
3. 断点会触发调试

## 七、常见问题排查方案

### 7.1 连接 GitLab 失败

**症状**: 测试连接时返回错误

**排查步骤**:
1. 确认 URL 格式正确（`https://gitlab.com` 或自托管地址）
2. 验证 API Token 是否有效：
   - 登录 GitLab
   - Settings -> Access Tokens
   - 确认 Token 未过期且有以下权限：
     - `api`
     - `read_repository`
     - `read_api`
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
   - 检查用户角色至少为 Reporter
5. 尝试手动调用 API：
   ```bash
   curl -H "PRIVATE-TOKEN: <your_token>" \
        https://gitlab.com/api/v4/projects/<project_id>/merge_requests
   ```

### 7.3 插件不显示

**症状**: 工具窗口找不到

**排查步骤**:
1. 确认插件已启用：
   ```
   Settings -> Plugins -> Installed -> GitLab Integration
   ```
2. 检查 IDEA 版本（需 >= 2023.2）
3. 查看日志文件：
   ```
   Help -> Show Log in Explorer
   ```
   搜索 "GitLab" 关键字
4. 重新启用插件：
   - 取消勾选插件
   - 重启 IDEA
   - 重新勾选插件
   - 再次重启

### 7.4 构建失败

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

### 7.5 UI 显示异常

**症状**: 界面错乱或组件不显示

**排查步骤**:
1. 检查 IDEA 主题（Dark/Light 模式兼容性）
2. 清除 IDEA 缓存：
   ```
   File -> Invalidate Caches -> Invalidate and Restart
   ```
3. 检查 `CardLayout` 是否正确切换
4. 验证组件是否正确添加到父容器

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

### 8.2 断点调试

1. 在代码行号处点击设置断点
2. 使用 Debug 模式运行
3. 在沙箱实例中触发操作
4. 断点命中后可查看变量值

### 8.3 网络请求调试

启用 OkHttp 日志：

```kotlin
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
suspend fun getPipelines(projectId: String): GitLabApiResponse<List<Pipeline>> {
    // 实现获取 Pipeline 的逻辑
}
```

### 9.2 添加新的 UI 面板

1. 创建面板类继承 `JPanel`
2. 在 `GitLabToolWindowContent.kt` 中注册
3. 添加状态切换逻辑

### 9.3 添加新的操作

1. 创建 Action 类继承 `AnAction`
2. 在 `plugin.xml` 中注册
3. 实现 `actionPerformed` 方法

## 十、发布到 JetBrains Marketplace

1. 注册开发者账号：https://plugins.jetbrains.com/
2. 准备插件材料：
   - 插件 ZIP 文件
   - 图标（logo.png）
   - 描述文档
3. 填写插件信息并上传
4. 等待审核（通常 1-3 个工作日）
