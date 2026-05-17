# Recorded Daily Script Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为录制后的日常脚本提供一个可视化查看器，能展示复杂 `DailyTaskPlan` 的节点与逻辑关系，并支持对录制脚本做新增节点、删除节点、编辑节点。

**Architecture:** 复用现有 [ScriptLibraryActivity](/D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt:33) 作为入口，新增一个独立的“脚本查看器 Activity”，避免把复杂图结构和编辑逻辑继续塞进当前的预览弹窗。查看器只基于 `DailyTaskPlan` 工作，因此可以打开内置脚本和录制脚本；但编辑能力只对录制脚本开放，内置 `assets/daily_scripts/*.json` 一律只读，避免把两套来源和模板目录写乱。

**Tech Stack:** Kotlin、Android View 系统、`DailyTaskPlan`/`TaskParams` 现有模型、`UserDailyScriptStore` 现有读写能力、Gson。

---

## File Structure

**Create**
- `app/src/main/java/com/example/yuanassist/ui/RecordedDailyScriptViewerActivity.kt`
  责任：查看器页面入口，负责加载脚本、切换只读/可编辑模式、弹出详情/编辑/新增/删除交互、保存脚本。
- `app/src/main/java/com/example/yuanassist/ui/view/DailyPlanGraphView.kt`
  责任：绘制节点卡片和连接线，处理节点点击，并承载“按层布局后的图结构”渲染。
- `app/src/main/java/com/example/yuanassist/core/DailyPlanGraphBuilder.kt`
  责任：把 `DailyTaskPlan` 转成图节点、边、层级和节点摘要，统一给查看器和图视图使用。
- `app/src/main/java/com/example/yuanassist/model/DailyPlanGraphModels.kt`
  责任：定义查看器专用的轻量模型，如 `GraphNodeUiModel`、`GraphEdgeUiModel`、`GraphNodeSummary`、`NodeIncomingReference`。

**Modify**
- `app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt`
  责任：把当前 `showDailyPlanPreview()` 的文本弹窗替换为跳转查看器；录制脚本列表页仍保留“导入/删除”按钮。
- `app/src/main/java/com/example/yuanassist/utils/UserDailyScriptStore.kt`
  责任：补充录制脚本保存辅助能力，例如覆盖保存计划、模板文件重命名、列出 bundle 内模板文件。
- `app/src/main/java/com/example/yuanassist/model/DailyTaskModels.kt`
  责任：只在确有必要时补充查看器所需的只读解析字段；不改现有字段语义。

## Product Rules

- 查看器必须能打开复杂脚本，例如 [tu_fa_qing_kuang.json](/D:/YuanAssist-master/app/src/main/assets/daily_scripts/tu_fa_qing_kuang.json:1) 这种带 `on_success`、`on_fail`、`branch_routes`、`SET_VAR` 的脚本。
- 节点卡片只显示“部分信息”：`id`、`action`、`delay`、主参数摘要、`success/fail` 去向。
- 逻辑关系必须是结构化图，而不是纯文本列表。
- 节点点击后必须能看全部细节。
- 编辑、新增、删除只对“录制脚本 bundle”开放；内置脚本只读。
- 删除被其它节点通过 `on_success` 或 `on_fail` 引用的节点时，不做隐式重连，直接禁止删除并提示用户先修改引用；这是最短路径里逻辑最安全的做法。
- 不引入“兼容旧错数据”的补丁逻辑；脚本图只按当前 `DailyTaskPlan` 真实字段解析。

## Graph Structure

- 主流程边：
  - `on_success` 用主线实线表示。
  - `on_fail` 用失败分支实线表示。
- 分支边：
  - `branch_routes` 用带标签的分支线表示，标签直接显示 key，例如 `0-1`、`1-2`、`0`。
- 引用关系：
  - `ref_task_id` 不画成主流程线，避免图爆炸；只在节点卡片和详情里显示“引用节点 #id”。
- 终止去向：
  - `-1 / -2 / -3 / -4` 不当成普通节点创建，直接渲染为终止标签。
- 布局方式：
  - 从 `start_task_id` 做 BFS 分层。
  - 同层按 `id` 升序排。
  - 节点位置固定在网格上，外层用纵向 + 横向滚动容器承载，不做首版缩放手势。

## Task 1: Replace Text Preview With A Real Viewer Entry

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/RecordedDailyScriptViewerActivity.kt`

- [ ] 新增查看器 Activity 的启动协议。
  传入字段至少包含：脚本名、脚本 JSON、模板目录路径、是否可编辑、来源是否为 assets。

- [ ] 把 `showDailyPlanPreview()` 的弹窗预览替换为“打开查看器”。
  具体规则：
  - 录制脚本卡片点击：进入查看器，可编辑。
  - 内置日常脚本点击：进入查看器，只读。
  - 录制脚本卡片上的“导入”“删除”按钮继续保留现有行为。

- [ ] 查看器首屏先做基础信息区。
  顶部显示：
  - 脚本名
  - 起始任务 ID
  - 任务总数
  - 是否只读
  - 模板目录路径（只对录制脚本显示）

- [ ] 保留一个兜底文本入口，但只作为开发期调试视图，不再作为主预览。
  这个文本入口只在查看器内做“原始 JSON”折叠区，不再用系统弹窗展示全脚本。

## Task 2: Build A Deterministic Graph Model For DailyTaskPlan

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/core/DailyPlanGraphBuilder.kt`
- Create: `app/src/main/java/com/example/yuanassist/model/DailyPlanGraphModels.kt`
- Modify: `app/src/main/java/com/example/yuanassist/model/DailyTaskModels.kt`

- [ ] 定义查看器内部模型。
  至少包含：
  - `GraphNodeUiModel`
  - `GraphEdgeUiModel`
  - `GraphTerminalUiModel`
  - `GraphLayoutNode`
  - `NodeDetailUiModel`
  - `NodeIncomingReference`

- [ ] 在图构建器里统一收集所有“逻辑边”。
  只收集：
  - `on_success`
  - `on_fail`
  - `branch_routes`
  不把 `ref_task_id` 当流程边。

- [ ] 生成节点摘要文案。
  按 action 输出不同摘要：
  - `CLICK`：显示坐标或 `ref_task_id`
  - `MATCH_TEMPLATE`：显示模板名、阈值、是否点击、ROI
  - `OCR`：显示识别文字、最少命中数、是否点击、ROI
  - `SET_VAR`：显示 `var_name=var_value`
  - `BACK`：显示返回动作
  - 未知 action：显示原始 `action`

- [ ] 生成节点详情模型。
  详情层必须能展示 `TaskParams` 里实际出现的全部字段，而不是只展示录制器会生成的子集。

- [ ] 生成 incoming reference 索引。
  用于：
  - 详情中显示“哪些节点跳到我”
  - 删除前判断是否允许删

## Task 3: Render The Graph Viewer

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/view/DailyPlanGraphView.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/RecordedDailyScriptViewerActivity.kt`

- [ ] 用 `HorizontalScrollView` + `ScrollView` + 自定义图视图渲染大图。
  不在首版引入缩放手势，先保证复杂脚本能稳定浏览。

- [ ] 在 `DailyPlanGraphView` 内实现固定网格布局。
  基本规则：
  - 每层一列
  - 每个节点一张卡片
  - 同层上下排列
  - 卡片之间留出足够连接线空间

- [ ] 节点卡片显示部分信息。
  卡片最少包含：
  - `#id`
  - `action`
  - `delay`
  - 摘要 1 行
  - 成功/失败去向 badge

- [ ] 连接线可区分类型。
  最少区分：
  - `on_success`
  - `on_fail`
  - `branch_routes`
  标签直接画在连线附近或边标签位置。

- [ ] 节点点击后弹出完整详情面板。
  详情内容包含：
  - 基础字段
  - 全量 `TaskParams`
  - incoming 引用
  - 原始节点 JSON

## Task 4: Support Edit / Add / Delete For Recorded Scripts

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/RecordedDailyScriptViewerActivity.kt`
- Modify: `app/src/main/java/com/example/yuanassist/utils/UserDailyScriptStore.kt`

- [ ] 先在查看器内维护一份可变的 `MutableList<DailyTask>` 工作副本。
  所有编辑先改工作副本，再统一保存回 bundle。

- [ ] 编辑节点只做“当前录制器动作集合”的强表单支持。
  首版表单支持：
  - `CLICK`
  - `MATCH_TEMPLATE`
  - `OCR`
  - `BACK`
  这和当前录制器 [DailyScriptRecorderManager.kt](/D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/core/DailyScriptRecorderManager.kt:56) 输出保持一致。

- [ ] 对非录制器动作只读展示，不开放编辑。
  例如 `SET_VAR`、复杂 `branch_routes` 节点，在内置脚本里只看不改。

- [ ] 新增节点时使用“完整节点表单”，但不做隐式接线。
  默认值：
  - `id = maxId + 1`
  - `delay = 2500`
  - `on_success = -1`
  - `on_fail = -1`
  - action 默认 `CLICK`
  新节点创建后由用户明确编辑其它节点把流向指过去。

- [ ] 删除节点前做引用校验。
  只检查这两类引用：
  - 其它节点的 `on_success`
  - 其它节点的 `on_fail`
  只要存在上述引用就禁止删除，并提示用户先修改这些引用。

- [ ] 保存逻辑只改录制脚本 bundle。
  步骤：
  - 把工作副本封装回 `DailyTaskPlan`
  - 用 `UserDailyScriptStore.savePlan()` 覆盖 `script.json`
  - 如果编辑修改了 `MATCH_TEMPLATE.template_name`，同步重命名 bundle 里的模板文件
  - 保存完成后重新构图并刷新 UI

## Task 5: Verification Rules

**Files:**
- Verify with: `app/src/main/assets/daily_scripts/tu_fa_qing_kuang.json`
- Verify with: `app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt`
- Verify with: `app/src/main/java/com/example/yuanassist/utils/UserDailyScriptStore.kt`

- [ ] 手动验证只读查看。
  用 `tu_fa_qing_kuang.json` 打开查看器，确认：
  - 图能完整打开
  - `SET_VAR`、`branch_routes`、终止节点都能看懂
  - 点击节点能看完整详情

- [ ] 手动验证录制脚本可编辑。
  用一份本地录制脚本确认：
  - 点击进入查看器
  - 编辑 `CLICK/MATCH_TEMPLATE/OCR/BACK` 能保存
  - 新增节点后列表和图都刷新
  - 删除被 `on_success` / `on_fail` 引用的节点会被禁止，并提示先修改引用

- [ ] 手动验证模板改名。
  编辑一个 `MATCH_TEMPLATE` 节点的模板名，确认：
  - `script.json` 中模板名更新
  - `templates/` 里的对应文件也一起改名
  - 不会丢模板图

- [ ] 不主动运行编译、测试、构建或 dev server。
  这个仓库按 AGENTS.md 要求执行；实现阶段只做代码修改和必要的本地读写。

## Self-Review

- 需求覆盖：
  - 节点摘要展示：已覆盖
  - 逻辑关系图：已覆盖
  - 节点详情：已覆盖
  - 新增/删除/编辑：已覆盖
  - 复杂脚本查看：已覆盖
- 风险点：
  - “复杂内置脚本可查看”和“录制脚本可编辑”是同一查看器的两种模式，不拆两套页面。
  - 删除行为选择“禁止删被 `on_success` / `on_fail` 引用的节点”，不是自动重连，能避免业务逻辑被偷偷改坏。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-29-recorded-daily-script-viewer.md`.

Two execution options:

1. Subagent-Driven (recommended) - 我按任务分段推进并逐段校对
2. Inline Execution - 我在当前会话里直接连续实现
