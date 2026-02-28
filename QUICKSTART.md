# GitLab IDEA 插件 - 快速启动指南

## 一分钟快速开始

### 第一步：配置 IDEA SDK

1. 打开项目后，进入 `File -> Project Structure -> Project`
2. 设置 SDK 为 **Java 17**（点击 Download JDK 下载）
3. 设置 Language level 为 **17**

### 第二步：同步 Gradle

1. 等待 IDEA 自动下载 Gradle 依赖
2. 如有提示，点击 "Trust Project" 和 "Auto-Import"

### 第三步：运行插件

1. 打开 `Run -> Edit Configurations`
2. 点击左上角 `+` 选择 `Plugin`
3. 设置配置：
   - Name: `Run with IDE`
   - VM options: `-Xmx2g`（可选，增加内存）
4. 点击 `OK`
5. 点击绿色运行按钮（或 Shift+F10）

### 第四步：测试插件

1. 新打开的 IDEA 窗口（沙箱环境）会自动加载插件
2. 打开任意项目
3. 进入 `View -> Tool Windows -> GitLab`
4. 点击 `+` 添加 GitLab 服务器：
   - 服务器名称：例如 "GitLab"
   - 项目地址：`https://gitlab.com`
   - API Token：从 GitLab Settings -> Access Tokens 获取

---

## 获取 GitLab API Token

1. 登录 [GitLab](https://gitlab.com)
2. 点击右上角头像 -> `Settings`
3. 左侧菜单 -> `Access Tokens`
4. 点击 `Add new token`
5. 设置：
   - Token name: `IDEA Plugin`
   - Expiration: 选择过期时间（或留空永不过期）
   - Select scopes: 勾选以下权限
     - ✅ `api`
     - ✅ `read_api`
     - ✅ `read_repository`
     - ✅ `read_milestone`
     - ✅ `read_issue`
     - ✅ `read_merge_request`
6. 点击 `Create personal access token`
7. **复制生成的 Token**（只会显示一次！）

---

## 打包插件

### 使用命令行

```bash
# Windows
gradlew.bat clean buildPlugin

# macOS/Linux
./gradlew clean buildPlugin
```

### 使用 IDEA 界面

1. 打开右侧 `Gradle` 面板
2. 展开 `gitlab-idea-plugin -> Tasks -> intellij`
3. 双击 `buildPlugin`

打包完成后，插件位于：`build/distributions/gitlab-idea-plugin-1.0.0.zip`

---

## 安装到生产环境

1. 进入 `File -> Settings -> Plugins`
2. 点击齿轮图标 ⚙️
3. 选择 `Install Plugin from Disk...`
4. 选择 `build/distributions/gitlab-idea-plugin-1.0.0.zip`
5. 重启 IDEA

---

## 常见问题

### Q: Gradle 下载很慢怎么办？

**A**: 配置国内镜像，编辑 `build.gradle.kts`：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    mavenCentral()
}
```

### Q: 找不到 Plugin 类型的运行配置？

**A**: 确保安装了 **Plugin DevKit** 插件：
- IDEA Ultimate 自带
- IDEA Community 需要手动安装

### Q: 运行时提示 "Plugin is not signed"？

**A**: 这是正常的，开发环境可以忽略。正式发布时可以签名。

### Q: 工具窗口找不到？

**A**: 检查：
1. 插件是否已启用（Settings -> Plugins -> Installed）
2. 在 View -> Tool Windows 中查找 "GitLab"
3. 查看 Help -> Show Log in Explorer 中的错误日志

### Q: 无法连接 GitLab？

**A**: 检查：
1. URL 格式：`https://gitlab.com`（不要以 / 结尾）
2. Token 是否有效且具有所需权限
3. 网络连接是否正常
4. 是否需要配置代理

---

## 调试技巧

### 查看日志

```
Help -> Show Log in Explorer
```

### 启用详细日志

在 `log/idea.log` 中搜索 "GitLab" 关键字查看插件日志。

### 断点调试

1. 在代码行号处点击设置断点
2. 使用 Debug 模式运行（Shift+F9）
3. 在沙箱实例中触发操作

---

## 项目结构速查

```
src/main/
├── kotlin/com/gitlab/idea/
│   ├── actions/          # 用户操作（添加服务器、刷新等）
│   ├── api/              # GitLab API 客户端
│   ├── config/           # 配置管理
│   ├── model/            # 数据模型
│   ├── toolwindow/       # 工具窗口和 UI 组件
│   └── util/             # 工具类
└── resources/META-INF/
    └── plugin.xml        # 插件配置文件
```

---

## 下一步

- 阅读完整文档：[README.md](README.md)
- 开发指南：[DEVELOPMENT.md](DEVELOPMENT.md)
- 报告问题：GitHub Issues

祝开发愉快！🚀
