# 鸢报26次 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在披荆斩棘第一模块新增一个“鸢报26次”任务，并通过新的总控 JSON 顺序调度现有鸢报子脚本。

**Architecture:** UI 层新增独立勾选状态并写入 `PiJingZhanJiConfig.tasks`。运行时沿用现有 `PiJingZhanJiTaskType -> scriptFileName` 机制，通过一个新的 `RUN_SCRIPT_SEGMENT` 总脚本串联“突发情况 / 小道消息 / 他的传闻 / 待办公务”。

**Tech Stack:** Kotlin, Android Fragment + Compose, Gson JSON task plan, AutoTaskEngine

---

### Task 1: 接入新任务类型

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/model/PiJingZhanJiModels.kt`
- Modify: `app/src/main/java/com/example/yuanassist/ui/PiJingZhanJiFragment.kt`

- [ ] 新增 `PiJingZhanJiTaskType.YUAN_BAO_26`
- [ ] 新增第一模块勾选状态、持久化 key、配置写入和展示文案

### Task 2: 新增总控脚本

**Files:**
- Create: `app/src/main/assets/daily_scripts/pi_jing_zhan_ji_yuan_bao_26.json`

- [ ] 先进入披荆斩棘活动页
- [ ] 再定位到鸢报主界面
- [ ] 使用 `RUN_SCRIPT_SEGMENT` 顺序执行 26 次子流程

### Task 3: 同步项目文档

**Files:**
- Modify: `docs/project_context.md`

- [ ] 在披荆斩棘相关说明里补充“第一模块包含鸢报26次总控脚本”这一当前有效信息
