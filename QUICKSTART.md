# GitLab IDEA 插件 - 快速启动指南

## 一分钟快速开始

### 第一步：配置 IDEA SDK

1. 打开项目后，进入 `File -> Project Structure -> Project` (Ctrl+Alt+Shift+S)
2. 设置 SDK 为 **Java 17**（如未安装，点击 `Download JDK` 下载）
3. 设置 Language level 为 **17 - Sealed types, pattern matching for switch**

### 第二步：同步 Gradle

1. 等待 IDEA 自动下载 Gradle 依赖（首次可能需要几分钟）
2. 如有提示，点击 "Trust Project" 和 "Load Gradle Project"

**国内用户加速**：如需配置镜像，在 `build.gradle.kts` 的 `repositories` 中添加：
```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
```

### 第三步：运行插件

1. 打开 `Run -> Edit Configurations` (Ctrl+Alt+S)
2. 点击左上角 `+` 选择 `Plugin`
3. 设置配置：
   - **Name**: `Run with IDE`
   - **VM options**: `-Xmx2g`（可选，增加内存避免卡顿）
   - **Use classpath of module**: 选择主模块
4. 点击 `OK`
5. 点击绿色运行按钮 ▶️（或 Shift+F10）

### 第四步：测试插件

1. 新打开的 IDEA 窗口（沙箱环境）会自动加载插件
2. 打开任意 Git 项目
3. 进入 `View -> Tool Windows -> GitLab` 打开工具窗口
4. 点击 `+` 或侧边栏设置图标添加 GitLab 服务器：
   - **服务器名称**：例如 "GitLab" 或 "公司 GitLab"
   - **项目地址**：`https://gitlab.com` 或自托管地址如 `https://gitlab.company.com`
   - **API Token**：从 GitLab 获取（见下方说明）
   - **配置级别**：
     - ☑️ 默认（应用级）：所有项目共享此配置
     - ☐ 项目级：仅当前项目使用此配置
5. 点击 "测试连接" 验证配置
6. 点击 "确定" 保存

如果当前项目 Git 远程仓库指向 GitLab，会自动加载该项目的合并请求列表。

---

## 获取 GitLab API Token

### GitLab.com 用户

1. 登录 [GitLab](https://gitlab.com)
2. 点击右上角头像 -> `Edit profile`
3. 左侧菜单 -> `Access Tokens`
4. 点击 `Add new token`
5. 设置：
   - **Token name**: `IDEA Plugin`
   - **Expiration**: 选择过期时间（建议 1 年，或留空永不过期）
   - **Select scopes**: 勾选以下权限
     - ✅ `api`（必须，用于创建/合并/关闭 MR）
     - ✅ `read_api`（读取 API 数据）
     - ✅ `read_repository`（读取仓库信息）
     - ✅ `read_milestone`（读取里程碑）
     - ✅ `read_issue`（读取 Issue）
     - ✅ `read_merge_request`（读取合并请求）
6. 点击 `Create personal access token`
7. **⚠️ 立即复制生成的 Token**（只会显示一次！）

### 自托管 GitLab 用户

步骤同上，访问地址为 `https://your-gitlab-domain.com/-/profile/personal_access_tokens`

---

## 核心功能速览

### 查看合并请求
- **MR 列表**：按状态筛选（Opened/Closed/Locked/Merged）
- **关键词搜索**：按标题搜索 MR
- **范围筛选**：我创建的 / 指派给我的 / 全部
- **分页加载**：滚动加载更多 MR

### MR 详情
- 标题、描述（自动换行）
- 分支流向（源分支 → 目标分支）
- 作者、指派人、审核人
- 状态标签（带颜色区分）
- 创建时间、合并时间、合并者

### MR 操作
- **创建 MR**：选择分支、自动填充提交信息、选择指派人
- **快捷创建**："合并当前分支"按钮，一键使用当前 Git 分支
- **关闭 MR**：关闭待合并的合并请求
- **合并 MR**：执行合并，支持设置删除源分支
- **删除 MR**：删除合并请求（带确认对话框）
- **在浏览器打开**：跳转到 GitLab 网页查看

---

## 打包插件

### 使用命令行

```bash
# Windows
gradlew.bat clean buildPlugin

# macOS/Linux
./gradlew clean buildPlugin
```

### 使用 IDEA Gradle 面板

1. 打开右侧 `Gradle` 面板
2. 展开 `Tasks -> intellij -> buildPlugin`
3. 双击运行

打包完成后，插件位于：`build/distributions/gitlab-idea-plugin-1.0.0.zip`

---

## 安装到生产环境

1. 进入 `File -> Settings -> Plugins` (Ctrl+Alt+S)
2. 点击齿轮图标 ⚙️ -> `Install Plugin from Disk...`
3. 选择 `build/distributions/gitlab-idea-plugin-1.0.0.zip`
4. 点击 `OK` 并重启 IDEA
5. 重启后，按上述 "测试插件" 步骤配置服务器

---

## 常见问题

### Q: Gradle 下载很慢或失败？

**A**: 配置国内镜像，编辑 `build.gradle.kts`：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}
```

同时可在 `gradle.properties` 中配置：
```properties
org.gradle.jvmargs=-Xmx2048m
systemProp.http.proxyHost=proxy.company.com
systemProp.http.proxyPort=8080
```

### Q: 找不到 Plugin 类型的运行配置？

**A**: 
1. 确保安装了 **Plugin DevKit** 插件：
   - IDEA Ultimate 自带
   - IDEA Community 需手动安装：`Settings -> Plugins -> Marketplace` 搜索 "Plugin DevKit"
2. 等待 Gradle 同步完成
3. 确保项目正确导入为 Gradle 项目

### Q: 运行时提示 "Plugin is not signed"？

**A**: 这是正常的，开发环境可以忽略。正式发布时需要使用 JetBrains 签名的证书。

### Q: 工具窗口找不到？

**A**: 检查：
1. 插件是否已启用（`Settings -> Plugins -> Installed` 中查看 "GitLab MR"）
2. 在 `View -> Tool Windows` 中查找 "GitLab"
3. 查看 `Help -> Show Log in Explorer` 中的错误日志
4. 确认 IDEA 版本 >= 2024.2

### Q: 无法连接 GitLab？

**A**: 检查：
1. URL 格式：`https://gitlab.com`（不要以 `/` 结尾）
2. Token 是否有效且具有 `api` 权限（不只是 read 权限）
3. 网络连接是否正常，能否在浏览器访问该 URL
4. 是否需要配置代理（公司网络常见）
5. 自托管 GitLab 需确保 SSL 证书可信

### Q: 配置成功但无法加载 MR 列表？

**A**: 检查：
1. 当前项目是否为 Git 仓库（`git status` 测试）
2. Git 远程仓库是否指向 GitLab（`git remote -v` 查看）
3. Token 是否对该仓库有读取权限（至少 Developer 角色）
4. 点击刷新按钮或重启工具窗口重试

### Q: 创建 MR 时提示 "分支不存在"？

**A**: 
1. 确认源分支已推送到远程：`git push origin <branch-name>`
2. 检查分支名称拼写是否正确
3. 确认对该仓库有 Developer 或以上权限

---

## 调试技巧

### 查看日志

```
Help -> Show Log in Explorer/Finder
```

在日志文件中搜索 "GitLab" 查看插件相关日志。

### 启用详细日志

如需更详细的日志，可在运行时添加 JVM 参数：
```
-Didea.log.debug=true
```

### 断点调试

1. 在代码行号处点击设置断点（红色圆点）
2. 使用 Debug 模式运行（Shift+F9 或点击虫子图标）
3. 在沙箱实例中触发操作
4. 断点命中后可查看变量值、调用栈、单步执行

### 网络请求调试

如需调试 API 请求，可在 `GitLabApiClient.kt` 中临时添加：
```kotlin
println("API Request: $url")
println("Response: ${response.code}")
```

---

## 项目结构速查

```
src/main/
├── kotlin/com/gitlab/idea/
│   ├── GitLabPlugin.kt              # 插件主类
│   ├── actions/                     # 用户操作（添加服务器、刷新等）
│   │   ├── AddServerAction.kt
│   │   └── RefreshAction.kt
│   ├── api/                         # GitLab API 客户端
│   │   └── GitLabApiClient.kt
│   ├── config/                      # 配置管理（应用级 + 项目级）
│   │   ├── GitLabConfigurable.kt
│   │   ├── GitLabConfigService.kt
│   │   ├── GitLabProjectConfigurable.kt
│   │   └── GitLabProjectConfigService.kt
│   ├── model/                       # 数据模型
│   │   └── GitLabServer.kt          # Server, MR, User, Branch 等
│   ├── toolwindow/                  # 工具窗口和 UI 组件
│   │   ├── components/              # UI 组件
│   │   │   ├── EmptyStatePanel.kt
│   │   │   ├── ErrorStatePanel.kt
│   │   │   ├── LoadingStatePanel.kt
│   │   │   ├── MRActionToolbar.kt
│   │   │   ├── MRDetailsPanel.kt
│   │   │   ├── MRListPanel.kt
│   │   │   └── ToolWindowSideToolbar.kt
│   │   ├── dialog/
│   │   │   └── MRActionConfirmDialog.kt
│   │   ├── CreateMRDialog.kt        # 创建 MR 对话框
│   │   ├── GitLabServerDialog.kt    # 服务器配置对话框
│   │   ├── GitLabToolWindowContent.kt
│   │   ├── GitLabToolWindowFactory.kt
│   │   └── ToolWindowMutexManager.kt
│   └── util/                        # 工具类
│       ├── GitLabNotifications.kt
│       └── GitUtil.kt
└── resources/META-INF/
    ├── plugin.xml                   # 插件配置
    └── gitlab-git.xml               # Git4Idea 依赖配置
```

---

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.1.0 |
| IntelliJ Platform SDK | 2024.2+ |
| Gradle | 8.4+ |
| JDK | 17 |
| OkHttp | 4.12.0 |
| Gson | 2.10.1 |

**兼容性**：IntelliJ IDEA 2024.2+ (build 241-253)

---

## 下一步

- 📖 阅读完整功能文档：[README.md](README.md)
- 🔧 查看开发指南：[DEVELOPMENT.md](DEVELOPMENT.md)
- 🐛 报告问题：GitHub Issues

**祝开发愉快！** 🚀
