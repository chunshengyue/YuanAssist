# 反馈中心与鸟食调试冷却日志 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为鸟食任务增加仅调试模式可见的冷却状态日志，并在 Mine 页面新增反馈中心，支持提交反馈、上传图片、附带运行日志以及查看当前设备历史反馈与回复。

**Architecture:** 继续沿用 BirdFoodRuntimeManager 作为鸟食调度与日志出口，在其内部维护冷却日志轮询与状态去重。反馈中心复用现有深色页面样式、Bmob 用户体系和图床上传流程，新建一个独立 Activity 承载提交流与历史列表。

**Tech Stack:** Kotlin、Android XML、Bmob SDK、RecyclerView、OkHttp、现有 RunLogger

---

### Task 1: 鸟食调试冷却日志

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/core/BirdFoodRuntimeManager.kt`

- [ ] 新增调试模式下的 30 秒冷却状态轮询调度
- [ ] 新增冷却开始、结束日志文本前缀
- [ ] 为已耗尽任务补充状态识别，避免误报为已结束冷却
- [ ] 在启动、停止、冷却更新节点同步清理或刷新轮询状态

### Task 2: 反馈数据模型与清单注册

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/model/Feedback.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] 将旧 Feedback 模型扩展为 `issue_feedback` 所需字段
- [ ] 新增反馈状态常量
- [ ] 注册 `FeedbackCenterActivity`

### Task 3: 反馈中心 UI

**Files:**
- Modify: `app/src/main/res/layout/fragment_mine.xml`
- Create: `app/src/main/res/layout/activity_feedback_center.xml`
- Create: `app/src/main/res/layout/item_feedback_record.xml`
- Create: `app/src/main/java/com/example/yuanassist/ui/FeedbackRecordAdapter.kt`

- [ ] 在 Mine 页面增加“问题反馈”入口，样式与现有条目一致
- [ ] 新建反馈中心页面布局，包含提交卡片与历史列表
- [ ] 新建反馈记录列表项布局与适配器

### Task 4: 反馈中心逻辑

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/MineFragment.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/FeedbackCenterActivity.kt`

- [ ] 在 Mine 页面接入跳转逻辑
- [ ] 在反馈中心实现登录校验、设备 ID 读取、图片选择、日志开关、提交流程
- [ ] 复用现有图床上传方式
- [ ] 实现按 `deviceId` 查询历史反馈并展示回复

### Task 5: 自检

**Files:**
- Review only

- [ ] 检查新增字段名、Bmob 查询字段名、页面控件 ID 一致
- [ ] 检查调试模式开关与默认模式行为一致
- [ ] 检查未登录提示和空列表文案
