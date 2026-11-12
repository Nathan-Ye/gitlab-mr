# GitLab MR

**简体中文** · [English](./README_EN.md)

在 IntelliJ IDEA 中直接查看和管理 GitLab Merge Requests，无需切换到浏览器。

---

## 下载安装

### 安装包

前往 [Releases](https://github.com/Nathan-Ye/gitlab-mr/releases) 页面下载编译好的 ZIP 安装包。

插件文件: `build/distributions/gitlab-mr-[version].zip`

**安装方式**:
1. 下载 ZIP 包
2. IntelliJ IDEA: `Settings → Plugins → Install Plugin from Disk...`
3. 重启 IDEA

### 环境要求

- IntelliJ IDEA **2024.2** 或更高版本（Community 或 Ultimate 均可）
- GitLab 服务器 (支持 gitlab.com 及私有部署版本)
- GitLab **Personal Access Token**，推荐权限：
  ```
  api, read_api, read_repository, read_milestone, read_issue, read_merge_request
  ```

---

## 核心功能

### MR 列表与筛选
- 查看项目所有 Merge Requests
- 按状态筛选：待合并、已关闭、有冲突、已合并
- 按范围筛选：全部、我创建的、指派给我的
- 标题关键字搜索
- 分页加载，支持加载更多

### MR 详情与操作
- 查看 MR 标题、描述、作者、分支、状态、时间等完整信息
- 状态处直接展示合并阻塞原因（流水线中、有冲突、需变基、待审批、草稿等）
- 在浏览器中打开 MR
- 关闭 / 合并 / 删除 MR
- 流水线尚未结束时，可选择设置为"流水线通过后自动合并"，详情页会显示自动合并标记
- 合并状态在后台自动轮询刷新，且不会打断当前阅读位置
- 创建新 MR：源分支和目标分支都选定后，自动生成标题 `Merge Request：源分支 → 目标分支`，并按源分支最新提交自动填充描述
- 创建 MR 时，源/目标分支和指派人下拉框均支持关键字筛选（输入后按回车刷新下拉）

### 变更文件 Diff
- 在"变更"标签页查看改动文件树，按模块分组
- 双击文件调用 IntelliJ 原生 Diff 窗口对比 before/after
- Diff 按变更树的顺序组成差异链，上一个/下一个差异可连续跨文件浏览
- 变更文件在本地仓库存在时，Diff 窗口支持 "Jump to Source" 跳转到源码
- 支持新增、修改、删除、重命名的文本文件
- 全部展开 / 全部收起

---

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.1.0 |
| IntelliJ Platform | IC 2024.2 (Build 242 起，不设上限) |
| OkHttp | 4.12.0 |
| Gson | 2.10.1 |
| Java | 21 |
| Gradle | 8.13 |

> 依赖可选的内置插件 `Git4Idea`，用于从 Git 远程地址自动解析项目。

---

## 快速开始

### 1. 配置 GitLab 服务

启动插件后（`View → Tool Windows → GitLab`），点击工具栏 **+** 添加服务器：

- **URL**: 你的 GitLab 地址，如 `https://gitlab.com`
- **Token**: Personal Access Token
- **作用域**: 选择应用级（所有项目共享）或项目级（仅当前项目）

> 插件会优先使用项目级配置，其次是应用级配置。

### 2. 自动解析项目

对于单仓库项目，插件会自动从 Git 远程地址解析 GitLab 项目并加载 MR 列表。

### 3. 开始使用

- 点击列表中的 MR 查看详情
- 使用顶部筛选框快速定位
- 在详情区操作关闭、合并、删除等
- 双击变更文件查看 Diff

---

## 项目结构

```
src/main/kotlin/com/nathan/gitlabmr/
├── GitLabPlugin.kt                  # 插件主类与服务
├── actions/                         # 工具栏 Action
├── api/
│   ├── GitLabApiClient.kt           # GitLab REST API 客户端
│   └── MRDiffContentLoader.kt       # Diff 内容加载器
├── config/                          # 应用级 / 项目级配置持久化与设置界面
├── model/
│   └── GitLabServer.kt              # 数据模型（含合并阻塞原因、流水线状态）
├── toolwindow/
│   ├── GitLabToolWindowFactory.kt   # 工具窗口工厂
│   ├── GitLabToolWindowContent.kt   # 工具窗口内容管理器
│   ├── MRDiffService.kt             # IntelliJ Diff 集成（跨文件差异链）
│   ├── CreateMRDialog.kt            # 创建 MR 弹框
│   ├── GitLabServerDialog.kt        # 服务器配置对话框
│   └── components/                  # MR 列表 / 详情 / 变更树 / 操作工具栏
└── util/                            # Git、筛选、通知等工具
```

---

## 本地构建

```bash
# Windows
gradlew.bat buildPlugin

# macOS / Linux
./gradlew buildPlugin
```

构建产物: `build/distributions/gitlab-mr-[version].zip`

清理: `./gradlew clean`

---

## 开源协议

[MIT License](LICENSE)
