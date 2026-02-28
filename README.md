# GitLab Integration Plugin for IntelliJ IDEA

一款功能强大的 IntelliJ IDEA GitLab 集成插件，支持查看和管理 GitLab 合并请求（Merge Requests）。

## 功能特性

- 📋 **合并请求管理** - 查看项目中所有合并请求，支持按状态筛选
- 🔍 **详情查看** - 查看合并请求的完整信息（分支、作者、时间等）
- 👥 **用户筛选** - 按用户名快速筛选合并请求
- 🎨 **状态分类** - 清晰区分 Opened、Closed、Locked、Merged 四种状态
- 🔧 **多服务器支持** - 支持配置多个 GitLab 服务器
- 🌐 **自动匹配** - 自动从 Git 远程仓库 URL 匹配 GitLab 项目

## 技术栈

- Kotlin 1.9.20
- IntelliJ Platform SDK 2023.2+
- OkHttp 4.12.0 (HTTP 请求)
- Gson 2.10.1 (JSON 解析)
- Kotlin Coroutines (异步处理)

## 开发环境配置

### 前置要求

1. **JDK 17** - 插件开发需要 JDK 17
2. **IntelliJ IDEA 2023.2+** - 推荐使用 IntelliJ IDEA Ultimate (包含对插件开发的完整支持)
3. **Gradle 8.4** - 项目构建工具

### IDEA SDK 配置

1. 打开 `File -> Project Structure -> Project`
2. 设置 SDK 为 JDK 17
3. 设置语言级别为 17

### 插件开发插件安装

在 IDEA 中安装以下插件：
- **Plugin DevKit** - IntelliJ IDEA Ultimate 自带，Community 需要单独安装

### 导入项目

1. 克隆或下载项目到本地
2. 在 IDEA 中选择 `File -> Open`
3. 选择项目根目录
4. 等待 Gradle 依赖下载完成

## 构建和打包

### 使用 Gradle 命令行

```bash
# Windows
gradlew.bat buildPlugin

# macOS/Linux
./gradlew buildPlugin
```

打包后的插件文件位于: `build/distributions/gitlab-idea-plugin-1.0.0.zip`

### 使用 IDEA Gradle 面板

1. 打开右侧 Gradle 面板
2. 展开 `Tasks -> intellij -> buildPlugin`
3. 双击执行

## 安装插件

### 方式一：从磁盘安装

1. 打开 IDEA，进入 `File -> Settings -> Plugins`
2. 点击齿轮图标，选择 `Install Plugin from Disk...`
3. 选择打包好的插件 ZIP 文件
4. 重启 IDEA

### 方式二：开发时运行

1. 在 IDEA 中打开项目
2. 在 `Run/Debug Configurations` 中选择 `Plugin` 配置
3. 点击运行按钮，会启动一个新的 IDEA 实例加载插件

## 使用说明

### 1. 添加 GitLab 服务器

1. 打开 GitLab 工具窗口（View -> Tool Windows -> GitLab）
2. 点击 `+` 按钮
3. 填写服务器信息：
   - **服务器名称**: 例如 "GitLab Self-Hosted"
   - **项目地址**: 例如 `https://gitlab.com` 或您的自托管服务器地址
   - **API Token**: 在 GitLab 用户设置中生成的个人访问令牌
4. 点击 "测试连接" 验证配置
5. 点击 "确定" 保存

### 2. 获取 GitLab API Token

1. 登录 GitLab
2. 进入 `Settings -> Access Tokens`
3. 创建新的个人访问令牌，需要以下权限：
   - `read_api`
   - `read_repository`
   - `read_milestone`
   - `read_issue`
   - `read_merge_request`
4. 复制生成的令牌（只会显示一次）

### 3. 查看合并请求

- 打开 GitLab 工具窗口
- 如果当前项目是 Git 仓库且远程指向 GitLab，会自动加载对应项目的 MR
- 点击列表中的 MR 可查看详情

### 4. 筛选合并请求

- **状态筛选**: 使用顶部的状态下拉框筛选特定状态的 MR
- **用户筛选**: 在用户输入框中输入用户名进行搜索

## 项目结构

```
src/main/
├── resources/
│   └── META-INF/
│       └── plugin.xml              # 插件配置文件
└── kotlin/
    └── com/gitlab/idea/
        ├── GitLabPlugin.kt         # 插件主类
        ├── actions/                # 操作类
        │   ├── AddServerAction.kt
        │   └── RefreshAction.kt
        ├── api/                    # API 调用
        │   └── GitLabApiClient.kt
        ├── config/                 # 配置相关
        │   ├── GitLabConfigurable.kt
        │   ├── GitLabConfigService.kt
        │   └── GitLabProjectConfigurable.kt
        ├── model/                  # 数据模型
        │   └── GitLabServer.kt
        ├── toolwindow/             # 工具窗口
        │   ├── components/         # UI组件
        │   │   ├── EmptyStatePanel.kt
        │   │   ├── ErrorStatePanel.kt
        │   │   ├── MRDetailsPanel.kt
        │   │   └── MRListPanel.kt
        │   ├── GitLabServerDialog.kt
        │   ├── GitLabToolWindowContent.kt
        │   └── GitLabToolWindowFactory.kt
        └── util/                   # 工具类
            ├── GitLabNotifications.kt
            └── GitUtil.kt
```

## 常见问题排查

### 1. 插件不显示

**原因**: IDEA 版本不兼容或插件加载失败

**解决方案**:
- 检查 IDEA 版本是否 >= 2023.2
- 查看 `Help -> Show Log in Explorer` 查看错误日志
- 确认插件已启用

### 2. 连接 GitLab 失败

**原因**: URL 配置错误、Token 无效或网络问题

**解决方案**:
- 确认 GitLab URL 格式正确（如 `https://gitlab.com`）
- 验证 API Token 是否有效且具有足够权限
- 检查网络连接和代理设置
- 使用 "测试连接" 功能验证

### 3. 无法加载合并请求

**原因**: 项目路径匹配失败或无权限访问

**解决方案**:
- 确认当前项目是 Git 仓库
- 检查远程仓库 URL 是否指向 GitLab
- 验证 API Token 对该项目有读取权限
- 在设置中手动配置服务器

### 4. 构建失败

**原因**: Gradle 版本不兼容或依赖下载失败

**解决方案**:
- 检查 JDK 版本是否为 17
- 删除 `.gradle` 目录重新下载依赖
- 配置 Gradle 镜像源（国内用户）
```gradle
// 在 build.gradle.kts 中添加镜像
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}
```

## API 端点

插件使用 GitLab REST API v4：

- `/user` - 获取当前用户信息
- `/projects` - 获取项目列表
- `/projects/:id` - 获取项目详情
- `/projects/:id/merge_requests` - 获取合并请求列表

## 开发路线图

- [ ] 支持 Pipeline 查看
- [ ] 支持 Issue 管理
- [ ] 支持代码审查
- [ ] 支持直接合并 MR
- [ ] 支持 CI/CD 状态显示
- [ ] 支持文件差异查看

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

- Issues: https://github.com/yourusername/gitlab-idea-plugin/issues

## 致谢

- IntelliJ Platform SDK
- GitLab REST API
