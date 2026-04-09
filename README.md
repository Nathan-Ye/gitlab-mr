# GitLab MR Plugin for IntelliJ IDEA

`GitLab MR` 是一个 IntelliJ IDEA 插件，用于在 IDE 内查看和操作 GitLab Merge Requests。

当前版本：`2.1.1`

## 主要能力

### Merge Request 列表与筛选

- 查看当前 GitLab 项目的 MR 列表
- 按状态筛选：`OPENED`、`CLOSED`、`LOCKED`、`MERGED`
- 按范围筛选：全部、我创建的、指派给我的
- 按标题关键字搜索
- 支持分页加载和继续加载更多

### Merge Request 详情与操作

- 查看 MR 标题、描述、作者、分支、状态、时间等信息
- 在浏览器中打开当前 MR
- 关闭 MR
- 合并 MR
- 删除 MR
- 创建 MR
- 创建或合并时可选择删除源分支
- 创建 MR 时支持从当前分支最新提交预填标题和描述
- 创建 MR 对话框支持“合并当前分支”快捷操作

### 变更文件与差异查看

- 在 MR 详情区的“变更”标签页查看改动文件树
- 按模块分组展示改动文件
- 自动压缩连续目录链，减少树层级噪音
- 双击改动文件，调用 IntelliJ 原生 Diff 窗口查看 before / after 差异
- 支持新增、修改、删除、重命名四类文本文件差异
- “变更”标签页顶部操作栏支持：
  - 全部展开
  - 全部收起
- “全部收起”后仅保留模块级节点

## 技术栈

- Kotlin `2.1.0`
- IntelliJ Platform Gradle Plugin `2.11.0`
- IntelliJ Platform `IC 2024.2`
- OkHttp `4.12.0`
- Gson `2.10.1`
- Java `17`
- Gradle Wrapper `8.13`

## 兼容性

- `since-build="241"`
- `until-build="253.*"`
- 依赖可选 bundled plugin：`Git4Idea`

## 项目解析方式

当前插件通过 Git 仓库远程地址解析 GitLab 项目：

1. 读取当前 IDEA 项目的 Git 仓库
2. 仅在项目中存在且仅存在一个 Git 仓库时继续
3. 读取 `origin` 远程地址
4. 从远程地址中提取 GitLab project path
5. 调用 GitLab API 获取项目信息
6. 加载该项目的 Merge Requests

已知限制：

- 多仓库项目当前不支持自动解析
- 没有实现“手动配置 project path 后兜底加载”的完整流程

## 构建

### 本地构建插件

```powershell
./gradlew.bat buildPlugin
```

输出产物位于：

```text
build/distributions/
```

### 已验证状态

已于 `2026-03-31` 本地执行：

```powershell
./gradlew.bat buildPlugin
```

结果：`BUILD SUCCESSFUL`

## 配置 GitLab 服务

可以通过工具窗口中的新增/编辑服务功能配置：

- GitLab 服务地址
- Access Token
- 作用域为应用级或项目级

Token 至少应具备适合当前场景的 API 访问权限，常见为：

- `api`
- `read_api`
- `read_repository`

## 代码结构

```text
src/main/kotlin/com/gitlab/idea/
├─ api/
│  ├─ GitLabApiClient.kt
│  └─ MRDiffContentLoader.kt
├─ config/
├─ model/
│  └─ GitLabServer.kt
├─ toolwindow/
│  ├─ GitLabToolWindowContent.kt
│  ├─ GitLabToolWindowFactory.kt
│  ├─ MRDiffService.kt
│  └─ components/
│     ├─ MRActionToolbar.kt
│     ├─ MRChangesTreePanel.kt
│     ├─ MRDetailsPanel.kt
│     └─ MRListPanel.kt
└─ util/
   ├─ GitLabNotifications.kt
   └─ GitUtil.kt
```

## 最近完成的能力

最近三条提交已经引入以下功能：

1. 获取并展示 MR 改动文件树
2. 双击改动文件，打开 IntelliJ 原生 Diff
3. 在“变更”页签中支持全部展开 / 全部收起

## 手工验证建议

建议至少验证以下场景：

- 单仓库项目能正常加载 MR
- “变更”页签能正常显示改动文件树
- 双击文本文件能打开 Diff
- 新增 / 修改 / 删除 / 重命名文件都能正确展示差异
- “全部展开”恢复完整展开
- “全部收起”后仅显示模块节点

## 许可

MIT License
