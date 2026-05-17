# Daily Import Recorded Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在日常页面新增“运行录制脚本”入口，把录制脚本导入现有日常悬浮窗，后续由悬浮窗开始按钮执行。

**Architecture:** 复用现有 `ScriptLibraryActivity + DailyScriptLibraryBridge + DailyWindowManager` 链路，不新建悬浮窗。入口负责选择和导入，运行仍由 `DailyWindowManager.toggleExecution()` 处理。

**Tech Stack:** Kotlin, Android Fragment/Service, Intent extras, existing daily floating window flow

---

### Task 1: 日常页入口与选择模式

**Files:**
- Modify: `app/src/main/res/layout/fragment_daily.xml`
- Modify: `app/src/main/java/com/example/yuanassist/ui/DailyFragment.kt`

- [ ] 新增“运行录制脚本”卡片
- [ ] 点击后进入脚本库录制脚本选择模式
- [ ] 选中脚本后把选择结果交给服务导入

### Task 2: 脚本库过滤录制脚本

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt`

- [ ] 新增只显示 `[录制]` 日常脚本的 pick mode
- [ ] 保持原 `PICK_MODE_DAILY_PLAN` 行为不变

### Task 3: 服务与悬浮窗导入

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/core/YuanAssistService.kt`
- Modify: `app/src/main/java/com/example/yuanassist/core/DailyWindowManager.kt`

- [ ] 新增导入录制日常脚本 action
- [ ] 收到后打开/复用现有日常悬浮窗
- [ ] 把 `fileName + jsonContent + templateDirPath` 导入为当前待运行任务
