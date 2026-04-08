# MR「检查中」状态与轮询防冲突设计

## 1. 目标与范围

- 新增一个内部状态：`检查中`，用于表示 GitLab 正在异步计算 MR 可合并性。
- 创建 MR 后立即获取该 MR 最新状态；若处于检查态，显示为`检查中`并自动轮询。
- 轮询结束后回写最终业务状态（`待合并`/`有冲突`/其他既有状态）。
- 轮询期间兼容手动“刷新列表”和“刷新当前 MR”，避免并发覆盖与重复任务。
- 本次不改动 GitLab 服务端行为，只调整插件端状态建模、拉取策略与并发控制。

## 2. 现状问题定位

### 2.1 状态映射过于粗糙

- 当前状态映射仅依赖 `state + has_conflicts`，未使用 `detailed_merge_status`。
- 结果是 MR 在 GitLab 仍处于“检查中”时，插件可能已显示为`待合并`。

对应位置：

- `src/main/kotlin/com/gitlab/idea/api/GitLabApiClient.kt`（`MergeResponseDto.toGitLabMergeRequest`）

### 2.2 合并按钮启用过早

- 合并按钮当前仅判断 `mr.state == OPENED`。
- 如果 MR 实际仍是检查态，点击合并会触发 GitLab 侧拒绝（常见 `405`）。

对应位置：

- `src/main/kotlin/com/gitlab/idea/toolwindow/components/MRActionToolbar.kt`

### 2.3 创建后缺少检查态跟踪

- 创建 MR 成功后仅触发列表静默刷新，没有对新建 MR 进行“检查态轮询”。
- 用户在“检查中”窗口期容易看到不一致状态并触发失败操作。

对应位置：

- `src/main/kotlin/com/gitlab/idea/toolwindow/CreateMRDialog.kt`
- `src/main/kotlin/com/gitlab/idea/toolwindow/GitLabToolWindowContent.kt`

## 3. API 与字段策略

## 3.1 读取字段

MR 详情或列表解析时，补充读取以下原始字段：

- `detailed_merge_status`（主判断字段）
- `merge_status`（兼容兜底）
- `has_conflicts`（冲突兜底）

### 3.2 检查态判定集合

建议将以下值视为“检查态”：

- `checking`
- `preparing`
- `unchecked`
- `approvals_syncing`

说明：

- 以 `detailed_merge_status` 为主。
- `merge_status` 仅用于兼容旧返回结构。

## 4. 数据模型改造

### 4.1 状态枚举

在 `MergeRequestState` 新增：

- `CHECKING("检查中")`

### 4.2 MR 模型扩展

在 `GitLabMergeRequest` 增加原始字段，便于诊断与后续扩展：

- `mergeStatusRaw: String?`
- `detailedMergeStatusRaw: String?`

### 4.3 DTO 扩展

在 `MergeResponseDto` 增加字段映射：

- `merge_status: String?`
- `detailed_merge_status: String?`

## 5. 状态映射规则

## 5.1 映射优先级

1. 若 API `state == merged`，映射 `MERGED`。
2. 若 API `state == closed`，映射 `CLOSED`。
3. 若 API `state == opened`，继续判断：
   - `detailed_merge_status` 在检查态集合中 -> `CHECKING`
   - `detailed_merge_status == conflict` 或 `has_conflicts == true` -> `LOCKED`
   - 其他 -> `OPENED`

### 5.2 兼容原则

- `detailed_merge_status` 优先级高于 `has_conflicts`。
- `has_conflicts` 仅做兜底，避免状态回退。

## 6. 轮询设计

### 6.1 触发时机

- 创建 MR 成功后（拿到 `iid`）立即调用 `getMergeRequest(iid)`。
- 手动刷新 MR 详情返回 `CHECKING` 时，确保轮询任务存在。
- 列表刷新后若当前选中 MR 为 `CHECKING`，保持或恢复轮询。

### 6.2 轮询参数

- 轮询间隔：`2s`
- 最大轮询时长：`90s`
- 连续失败阈值：`3` 次（超过则停止）

### 6.3 终止条件

- 状态不再是检查态。
- MR 不存在或请求失败达到阈值。
- 项目切换、服务器切换、工具窗口销毁（`dispose`）。

## 7. 并发防冲突设计（核心）

### 7.1 单飞控制

- 维护 `mrPollingJobs: MutableMap<Long, Job>`，按 `mrIid` 控制。
- 同一 MR 同时只允许一个轮询任务。

### 7.2 版本控制

- 维护 `mrRequestVersion: MutableMap<Long, Long>`。
- 每次主动刷新/轮询请求前对该 MR `version++`。
- 回写 UI 前比较版本，仅最新版本允许落盘；旧响应直接丢弃。

### 7.3 列表代际控制

- 维护 `listReloadGeneration: Long`。
- 每次列表全量刷新生成新代际。
- 响应回写时校验代际，防止旧列表响应覆盖新结果。

### 7.4 手动操作兼容规则

- 手动刷新详情：
  - 不创建第二条轮询。
  - 若已有轮询，仅更新版本并复用该任务。
- 手动刷新列表：
  - 不强制中断详情轮询。
  - 轮询回写需同时通过“MR 仍存在 + 版本最新 + 选中项匹配”校验。
- 切换选中 MR：
  - 详情仅在 `selectedMergeRequestIid` 一致时更新（沿用现有保护）。

## 8. UI 行为设计

### 8.1 状态展示

- 列表状态标签支持 `检查中`。
- 详情页状态标签支持 `检查中`。
- 状态颜色建议：蓝灰系，与`待合并`/`有冲突`明显区分。

### 8.2 操作按钮策略

- `CHECKING` 时：
  - 禁用“合并”
  - 禁用“关闭”
  - 禁用“删除”
  - 允许“刷新”
  - 允许“在 GitLab 中打开”

### 8.3 筛选项（保持不变）

- 状态筛选下拉不新增“检查中”，沿用现有选项：`全部 / 待合并 / 已关闭 / 有冲突 / 已合并`。
- `CHECKING` 仅作为展示与操作控制状态，不作为独立筛选条件。
- 筛选语义调整为：
  - 当筛选为`待合并`时，列表包含 `OPENED` 与 `CHECKING`。
  - 当筛选为`有冲突`时，仅包含 `LOCKED`。
  - 其余筛选语义保持原样。
- 与 GitLab API 交互时，`待合并`仍使用 `state=opened` 拉取，再在本地按上述语义归类展示。

## 9. 代码落点建议

- 状态与模型：
  - `src/main/kotlin/com/gitlab/idea/model/GitLabServer.kt`
- DTO 与状态映射：
  - `src/main/kotlin/com/gitlab/idea/api/GitLabApiClient.kt`
- 轮询编排与并发控制：
  - `src/main/kotlin/com/gitlab/idea/toolwindow/GitLabToolWindowContent.kt`
- 合并按钮启用规则：
  - `src/main/kotlin/com/gitlab/idea/toolwindow/components/MRActionToolbar.kt`
- 列表筛选（语义调整）与状态颜色：
  - `src/main/kotlin/com/gitlab/idea/toolwindow/components/MRListPanel.kt`
  - `src/main/kotlin/com/gitlab/idea/toolwindow/components/MRDetailsPanel.kt`

## 10. 关键流程（时序）

### 10.1 创建 MR 后

1. `CreateMRDialog` 创建成功，返回 `iid`。
2. `ToolWindowContent` 拉取该 MR 详情。
3. 若映射为 `CHECKING`：
   - 更新列表/详情为“检查中”
   - 启动该 `iid` 的单飞轮询任务
4. 轮询结束后落最终状态并刷新变更区。

### 10.2 检查中期间手动刷新详情

1. 用户点击刷新详情。
2. 触发一次即时拉取，版本号递增。
3. 若仍 `CHECKING`，复用已有轮询。
4. 若非 `CHECKING`，终止轮询并落最终状态。

### 10.3 检查中期间手动刷新列表

1. 用户刷新列表，代际号递增。
2. 列表回写只接受当前代际。
3. 对当前选中 MR：
   - 若仍 `CHECKING`，保持轮询
   - 若已出检查态，终止轮询并更新详情

## 11. 验证清单

- 新建 MR 后立即显示“检查中”，并在检查结束后自动切换为最终状态。
- 检查中时“合并 / 关闭 / 删除”按钮均不可用。
- 检查中时反复点“刷新 MR 详情”，无重复轮询、无状态回跳。
- 检查中时点击“刷新列表”，不会出现旧响应覆盖新状态。
- 切换项目/关闭工具窗口后轮询任务全部释放。

## 12. 风险与回退

### 12.1 风险

- 不同 GitLab 版本 `detailed_merge_status` 值可能有差异。
- 高频轮询在弱网环境可能放大失败率。

### 12.2 缓解

- 维护检查态白名单并可快速扩展。
- 使用失败阈值 + 最大轮询时长保护。
- 版本号与代际号确保“最终一致，不倒灌”。

### 12.3 回退策略

- 若出现兼容性问题，可暂时仅将 `checking/preparing` 识别为检查态。
- 保留 `has_conflicts` 兜底逻辑，避免误放开“合并”操作。
