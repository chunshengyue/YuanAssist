# BBQ TestActivity Demand Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `TestActivity` 中接入烧烤模式客人需求模板测试，支持单模板验证和单气泡内多需求汇总识别。

**Architecture:** 新增一个纯 Kotlin 的烧烤气泡需求汇总器，把“某个模板在哪个气泡命中”的结果收敛成按气泡分组的需求列表；`TestActivity` 只负责注册烧烤测试任务、ROI 和日志输出。客人需求模板统一使用阈值 `0.8` 和 `250x250` ROI。

**Tech Stack:** Kotlin, Android `TestActivity`, OpenCV 模板匹配, JUnit4

---

### Task 1: 提取可测试的需求汇总逻辑

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/BbqBubbleDemandDetector.kt`
- Test: `app/src/test/java/com/example/yuanassist/ui/BbqBubbleDemandDetectorTest.kt`

- [ ] **Step 1: 先写汇总逻辑测试**

- [ ] **Step 2: 实现最小汇总器**

- [ ] **Step 3: 保持汇总器只依赖纯数据结构，方便后续继续补规则**

### Task 2: 把烧烤模板测试接入 TestActivity

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] **Step 1: 新增烧烤测试任务与显示名称**

- [ ] **Step 2: 注册烧烤模式三个气泡 ROI，统一阈值 `0.8` 和 `250x250`**

- [ ] **Step 3: 增加烧烤模板选项**

- [ ] **Step 4: 增加“客人需求汇总”测试入口**

- [ ] **Step 5: 保留单模板测试能力，便于逐个看素材是否命中**

### Task 3: 交付约束

**Files:**
- Modify: `docs/superpowers/specs/2026-04-30-bbq-mode-knowns.md`

- [ ] **Step 1: 若实现中补充了稳定的测试入口名称或约束，同步更新已知文档**

- [ ] **Step 2: 不主动运行测试或编译，遵守仓库 AGENTS 约束**
