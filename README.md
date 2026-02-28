# GitLab Integration Plugin for IntelliJ IDEA

一款功能强大的 IntelliJ IDEA GitLab 集成插件，支持在 IDE 中直接查看和管理 GitLab 合并请求（Merge Requests）。

## 功能特性

### 📋 合并请求管理
- **查看 MR 列表** - 查看项目中所有合并请求，支持按状态筛选（Opened、Closed、Locked、Merged）
- **MR 详情查看** - 查看合并请求的完整信息：
  - 标题和描述（支持自动换行）
  - 分支流向（源分支 → 目标分支）
  - 作者、创建时间
  - 指派人、审核人
  - 合并者、合并时间
  - 状态标签（带颜色区分）
  - 合并后删除源分支选项
- **搜索筛选** - 按标题关键词搜索 MR
- **范围筛选** - 按创建者/指派人范围筛选（我创建的、指派给我的、全部）

### ✨ MR 操作
- **创建 MR** - 从当前分支创建新的合并请求，支持：
  - 选择源分支和目标分支
  - 自动填充最新提交信息作为标题和描述
  - 选择指派人
  - 设置合并后删除源分支
  - "合并当前分支"快捷按钮
- **关闭 MR** - 关闭待合并的合并请求
- **合并 MR** - 执行合并操作，支持设置删除源分支
- **删除 MR** - 删除合并请求（带确认对话框）

### 🔧 配置管理
- **多服务器支持** - 支持配置多个 GitLab 服务器（GitLab.com 和自托管实例）
- **应用级配置** - 默认服务器配置（所有项目共享）
- **项目级配置** - 针对特定项目的独立服务器配置
- **自动项目匹配** - 自动从 Git 远程仓库 URL 匹配 GitLab 项目

### 🎨 用户界面
- **工具窗口** - 位于 IDE 底部的 GitLab 工具窗口
- **状态卡片** - 空状态、错误状态、加载状态、主内容状态智能切换
- **分页加载** - 大数据量时支持滚动加载更多
- **无感刷新** - 创建/操作 MR 后自动刷新列表
- **侧边工具栏** - 快速访问设置、刷新、创建 MR

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Kotlin | 2.1.0 | 编程语言 |
| IntelliJ Platform SDK | 2024.2+ | 插件开发 SDK |
| Gradle | 8.4+ | 构建工具 |
| JDK | 17 | 目标 JVM 版本 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| Gson | 2.10.1 | JSON 序列化/反序列化 |
| Kotlinx Coroutines | Bundled | 异步协程支持 |

### 兼容性
- **支持 IDE**: IntelliJ IDEA Community/Ultimate 2024.2+
- **Since Build**: 241
- **Until Build**: 253.*
- **可选依赖**: Git4Idea（用于 Git 集成特性）

## 快速开始

### 安装插件

#### 方式一：从磁盘安装
1. 下载或构建插件包 `gitlab-idea-plugin-1.0.0.zip`
2. 打开 IDEA，进入 `File -> Settings -> Plugins`
3. 点击齿轮图标，选择 `Install Plugin from Disk...`
4. 选择插件 ZIP 文件并重启 IDEA

#### 方式二：开发时运行
1. 在 IDEA 中打开本项目
2. 运行 `gradlew.bat buildPlugin` 构建插件
3. 配置运行配置为 "Plugin" 并运行

### 配置 GitLab 服务器

1. **打开 GitLab 工具窗口**
   - `View -> Tool Windows -> GitLab`
   - 或点击 IDE 底部工具栏的 GitLab 图标

2. **添加服务器**
   - 点击工具窗口中的 `+` 按钮或侧边栏的设置图标
   - 填写服务器信息：
     - **服务器名称**: 例如 "GitLab Company"
     - **项目地址**: 例如 `https://gitlab.com` 或 `https://gitlab.company.com`
     - **API Token**: 在 GitLab 中生成的个人访问令牌
     - **配置级别**: 应用级（所有项目默认）或项目级（仅当前项目）
   - 点击 "测试连接" 验证配置
   - 点击 "确定" 保存

3. **获取 GitLab API Token**
   - 登录 GitLab
   - 进入 `Preferences -> Access Tokens`
   - 创建新的个人访问令牌，需要以下权限：
     - `api`
     - `read_api`
     - `read_repository`
     - `read_milestone`
     - `read_issue`
     - `read_merge_request`

### 使用指南

#### 查看合并请求
1. 打开 GitLab 工具窗口
2. 如果当前项目是 Git 仓库且远程指向 GitLab，会自动加载对应项目的 MR
3. 使用顶部的筛选器筛选 MR：
   - **状态下拉框**: 筛选 Opened、Closed、Locked、Merged 状态的 MR
   - **范围下拉框**: 筛选我创建的、指派给我的、全部的 MR
   - **搜索框**: 按标题关键词搜索 MR
4. 点击列表中的 MR 查看详情

#### 创建合并请求
1. 点击侧边栏的 "+" 按钮或工具栏的 "创建 MR" 按钮
2. 在弹出的对话框中：
   - 选择**源分支**（要合并的分支）
   - 选择**目标分支**（通常为主分支）
   - 标题和描述会自动从源分支的最新提交填充
   - 可选择**指派人**
   - 勾选**合并后删除源分支**（可选）
3. 点击 "创建"

#### 快捷操作：合并当前分支
- 在创建 MR 对话框中点击 "合并当前分支" 按钮
- 自动使用当前 Git 分支作为源分支
- 自动将当前用户设置为指派人
- 自动填充提交信息

#### 管理合并请求
在 MR 详情面板中，可以执行以下操作：
- **在浏览器中打开** - 在 GitLab 网页中查看 MR
- **复制链接** - 复制 MR 的 URL
- **关闭** - 关闭待合并的 MR
- **合并** - 执行合并操作
- **删除** - 删除 MR（带确认对话框）

## 项目结构

```
src/main/
├── kotlin/com/gitlab/idea/
│   ├── GitLabPlugin.kt                    # 插件主类，版本信息和静态方法
│   ├── actions/                           # 用户操作
│   │   ├── AddServerAction.kt             # 添加服务器动作
│   │   └── RefreshAction.kt               # 刷新数据动作
│   ├── api/                               # GitLab API 层
│   │   └── GitLabApiClient.kt             # REST API 客户端（OkHttp + Gson）
│   ├── config/                            # 配置管理
│   │   ├── GitLabConfigurable.kt          # 应用级设置 UI
│   │   ├── GitLabConfigService.kt         # 应用级配置持久化
│   │   ├── GitLabProjectConfigurable.kt   # 项目级设置 UI
│   │   └── GitLabProjectConfigService.kt  # 项目级配置持久化
│   ├── model/                             # 数据模型
│   │   └── GitLabServer.kt                # Server、MR、User、Project 等数据类
│   ├── toolwindow/                        # 工具窗口 UI
│   │   ├── GitLabToolWindowFactory.kt     # 工具窗口工厂
│   │   ├── GitLabToolWindowContent.kt     # 内容管理（CardLayout）
│   │   ├── GitLabServerDialog.kt          # 添加/编辑服务器对话框
│   │   ├── CreateMRDialog.kt              # 创建 MR 对话框
│   │   ├── ToolWindowMutexManager.kt      # UI 状态管理
│   │   ├── dialog/
│   │   │   └── MRActionConfirmDialog.kt   # MR 操作确认对话框
│   │   └── components/                    # UI 组件
│   │       ├── EmptyStatePanel.kt         # 空状态面板
│   │       ├── ErrorStatePanel.kt         # 错误状态面板
│   │       ├── LoadingStatePanel.kt       # 加载状态面板
│   │       ├── MRListPanel.kt             # MR 列表（带筛选）
│   │       ├── MRDetailsPanel.kt          # MR 详情面板
│   │       ├── MRActionToolbar.kt         # MR 操作工具栏
│   │       └── ToolWindowSideToolbar.kt   # 侧边工具栏
│   └── util/                              # 工具类
│       ├── GitLabNotifications.kt         # 通知辅助类
│       └── GitUtil.kt                     # Git 仓库工具类
└── resources/META-INF/
    ├── plugin.xml                         # 插件配置
    └── gitlab-git.xml                     # Git4Idea 依赖配置
```

## 开发指南

### 环境要求
- **JDK 17** - 插件开发需要 JDK 17
- **IntelliJ IDEA 2023.2+** - 推荐使用 Ultimate 版（完整插件开发支持）
- **Gradle 8.4+** - 项目构建工具

### 构建插件

```bash
# Windows
gradlew.bat buildPlugin

# macOS/Linux
./gradlew buildPlugin
```

打包后的插件文件位于: `build/distributions/gitlab-idea-plugin-1.0.0.zip`

### 开发运行

1. 在 IDEA 中打开项目
2. 确保 Project SDK 设置为 JDK 17
3. 配置运行配置：
   - `Run -> Edit Configurations`
   - 添加 "Plugin" 配置
   - VM 选项（可选）: `-Xmx2g`
4. 点击运行或调试按钮，会启动一个沙盒 IDEA 实例加载插件

### 代码规范

- 遵循 [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用官方 Kotlin 代码风格
- 优先使用不可变数据类（`val` 而非 `var`）
- 使用 Kotlin 协程处理异步操作
- UI 更新必须在 EDT（Event Dispatch Thread）上执行

## GitLab API 集成

插件使用 GitLab REST API v4：

| 端点 | 方法 | 用途 |
|------|------|------|
| `/user` | GET | 获取当前用户信息 |
| `/projects` | GET | 获取项目列表 |
| `/projects/:id` | GET | 获取项目详情 |
| `/projects/:id/merge_requests` | GET | 获取 MR 列表 |
| `/projects/:id/merge_requests/:iid` | GET | 获取单个 MR |
| `/projects/:id/merge_requests` | POST | 创建 MR |
| `/projects/:id/merge_requests/:iid/merge` | PUT | 合并 MR |
| `/projects/:id/merge_requests/:iid` | PUT | 更新 MR（关闭） |
| `/projects/:id/merge_requests/:iid` | DELETE | 删除 MR |
| `/projects/:id/repository/branches` | GET | 获取分支列表 |
| `/projects/:id/members/all` | GET | 获取项目成员 |

### 认证方式
- 支持 URL 参数和 Header 两种认证方式
- 使用 `PRIVATE-TOKEN` Header 更可靠（优先尝试）

## 常见问题排查

### 插件不显示

**原因**: IDEA 版本不兼容或插件加载失败

**解决方案**:
- 检查 IDEA 版本是否 >= 2024.2
- 查看 `Help -> Show Log in Explorer` 查看错误日志
- 确认插件已启用

### 连接 GitLab 失败

**原因**: URL 配置错误、Token 无效或网络问题

**解决方案**:
- 确认 GitLab URL 格式正确（如 `https://gitlab.com`，无尾部斜杠）
- 验证 API Token 是否有效且具有足够权限（需要 `api` 权限）
- 检查网络连接和代理设置
- 使用 "测试连接" 功能验证
- 自托管 GitLab 需确保 SSL 证书可信

### 无法加载合并请求

**原因**: 项目路径匹配失败或无权限访问

**解决方案**:
- 确认当前项目是 Git 仓库
- 检查远程仓库 URL 是否指向 GitLab
- 验证 API Token 对该项目有读取权限
- 在设置中手动配置服务器和项目

### 构建失败

**原因**: Gradle 版本不兼容或依赖下载失败

**解决方案**:
- 检查 JDK 版本是否为 17
- 删除 `.gradle` 目录重新下载依赖
- 配置 Gradle 镜像源（国内用户）

## 开发路线图

- [x] 查看 GitLab 合并请求列表
- [x] 按状态筛选 MR（Opened、Closed、Locked、Merged）
- [x] 查看 MR 详情
- [x] 创建新的合并请求
- [x] 关闭、合并、删除 MR
- [x] 多服务器支持
- [x] 项目级配置
- [ ] 支持 Pipeline 状态查看
- [ ] 支持 Issue 管理
- [ ] 支持代码审查（Comments）
- [ ] 支持文件差异查看（Diff）
- [ ] 支持 CI/CD 状态显示

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [GitLab REST API](https://docs.gitlab.com/ee/api/)
- [OkHttp](https://square.github.io/okhttp/)
- [Gson](https://github.com/google/gson)
