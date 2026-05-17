# BBQ Template Replacement Bubble Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `TestActivity` 中把 BBQ 需求素材替换改成先选气泡、再在局部放大预览里拖动固定 `40x40` 红框。

**Architecture:** 复用现有替换素材弹窗与保存逻辑，只在 BBQ 需求素材入口增加气泡选择分支，并让 `ReplacementPreviewView` 支持只显示选中的 ROI。这样其他模板替换链路保持不变，BBQ 需求素材获得更易操作的放大预览。

**Tech Stack:** Kotlin, Android `AlertDialog`, `TestActivity`, `Bitmap` 裁剪

---

### Task 1: 调整 BBQ 替换素材入口

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] **Step 1: 识别 BBQ 需求素材替换场景**

- [ ] **Step 2: 弹出气泡1/2/3选择框**

- [ ] **Step 3: 选择后直接进入对应气泡 ROI 的替换预览，不再走通用 ROI 来源选择**

### Task 2: 支持局部放大预览

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] **Step 1: 给 `ReplacementPreviewView` 增加显示源区域参数**

- [ ] **Step 2: 让绘制、测量、触摸映射都基于显示源区域工作**

- [ ] **Step 3: BBQ 气泡替换时仅显示所选 ROI，但实际保存仍裁原图 `40x40`**

### Task 3: 交付约束

**Files:**
- Create: `docs/superpowers/specs/2026-05-02-bbq-template-replacement-bubble-picker-design.md`
- Create: `docs/superpowers/plans/2026-05-02-bbq-template-replacement-bubble-picker.md`

- [ ] **Step 1: 保持非 BBQ 替换素材链路不变**

- [ ] **Step 2: 不主动运行编译、测试或构建，遵守仓库 AGENTS 约束**
