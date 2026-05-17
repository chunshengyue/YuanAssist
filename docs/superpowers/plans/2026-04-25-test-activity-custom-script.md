# TestActivity Custom Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让功能测试页并列显示用户录制脚本，并使用该脚本包内的模板图与 ROI 执行局部测试。

**Architecture:** 在 `TestActivity` 内新增任务来源元数据，统一驱动任务列表、素材列表、ROI 索引和模板图读取。内置任务维持原行为，用户脚本任务只接入测试主链路，不扩散到替换素材等内置专用逻辑。

**Tech Stack:** Kotlin, Android Activity/UI, Gson, BitmapFactory

---

### Task 1: 任务元数据与素材列表

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] 引入用户脚本 bundle 元数据和任务来源判断
- [ ] 把任务列表扩展为“内置 + 用户脚本并列显示”
- [ ] 选中用户脚本任务时，从 `script.json` 解析出素材下拉项

### Task 2: ROI 与延迟索引接入

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] 扩展本地 ROI/延迟索引加载逻辑，接入 `UserDailyScriptStore.listBundles()`
- [ ] 保持 `taskScopedRegions()` 和延迟面板调用方式不变，只补充数据来源

### Task 3: 模板图读取与按钮行为

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] 模板匹配时对用户脚本任务从 `templates/` 读取 Bitmap
- [ ] 用户脚本任务下禁用一键替换/还原
- [ ] 保持现有测试入口与日志输出不变
