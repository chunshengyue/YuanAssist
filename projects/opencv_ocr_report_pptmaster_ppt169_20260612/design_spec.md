# opencv_ocr_report_pptmaster - Design Spec

> Human-readable design narrative - rationale, audience, style, color choices, content outline. Read once by downstream roles for context.
>
> Machine-readable execution contract: `spec_lock.md` (color / typography / icon / image short form). Executor re-reads `spec_lock.md` before every SVG page to resist context-compression drift. Keep both in sync; on divergence, `spec_lock.md` wins.

## I. Project Information

| Item | Value |
| ---- | ----- |
| **Project Name** | opencv_ocr_report_pptmaster |
| **Canvas Format** | PPT 16:9 (1280x720) |
| **Page Count** | 15 |
| **Design Style** | General Consulting + 工程报告型课程项目说明 |
| **Target Audience** | 课程老师直接阅读 |
| **Use Case** | 图像处理课程项目说明文档，非现场答辩提词稿 |
| **Created Date** | 2026-06-12 |

---

## II. Canvas Specification

| Property | Value |
| -------- | ----- |
| **Format** | PPT 16:9 |
| **Dimensions** | 1280x720 px |
| **viewBox** | `0 0 1280 720` |
| **Margins** | left/right 56px, top 40px, bottom 42px |
| **Content Area** | x 56-1224, y 92-650 |

---

## III. Visual Theme

### Theme Style

- **Style**: 工程报告型课程项目说明
- **Theme**: Light theme
- **Tone**: 克制、清晰、可阅读，避免过强模板感和 AI 风格

### Color Scheme

| Role | HEX | Purpose |
| ---- | --- | ------- |
| **Background** | `#FAFBFC` | Page background |
| **Secondary bg** | `#EEF3F8` | Light panels and diagrams |
| **Primary** | `#1565C0` | Structural accents, section labels |
| **Accent** | `#F59E0B` | Caution and iteration emphasis |
| **Secondary accent** | `#0F766E` | Positive engineering value |
| **Body text** | `#1F2933` | Main text |
| **Secondary text** | `#566273` | Captions and annotations |
| **Tertiary text** | `#8A94A6` | Footers |
| **Border/divider** | `#CBD5E1` | Lines and table borders |
| **Success** | `#0F766E` | Stable output / value |
| **Warning** | `#B91C1C` | Risks and limitations |

---

## IV. Typography System

### Font Plan

**Typography direction**: PPT-safe CJK sans, report-like density.

| Role | Chinese | English | Fallback tail |
| ---- | ------- | ------- | ------------- |
| **Title** | `SimHei`, `"Microsoft YaHei"` | Arial | sans-serif |
| **Body** | `"Microsoft YaHei"` | Arial | sans-serif |
| **Emphasis** | `SimHei` | Arial | sans-serif |
| **Code** | - | `Consolas`, `"Courier New"` | monospace |

**Per-role font stacks**:

- Title: `SimHei, "Microsoft YaHei", Arial, sans-serif`
- Body: `"Microsoft YaHei", Arial, sans-serif`
- Emphasis: `SimHei, "Microsoft YaHei", Arial, sans-serif`
- Code: `Consolas, "Courier New", monospace`

### Font Size Hierarchy

**Baseline**: Body font size = 18px.

| Purpose | Size |
| ------- | ---- |
| Cover title | 46-58px |
| Page title | 30-36px |
| Subtitle | 22-26px |
| Body | 17-19px |
| Annotation / caption | 12-14px |
| Page number / footnote | 10-12px |

Formula policy: `text-only`; source contains no formula-heavy material.

---

## V. Layout Principles

### Page Structure

- **Header area**: 40-88px; small source label and page number.
- **Content area**: 92-650px; higher-density explanation, tables, process diagrams, or placeholders.
- **Footer area**: 660-694px; source note and thin rule.

### Layout Pattern Library

- Use report-like structures: two-column text, compact tables, process bands, issue matrices.
- Avoid oversized one-line claims and empty decorative hero pages.
- Use cards only where they help compare stages or issues; keep card radius low and typography compact.
- Placeholder figures are acceptable, but must be labelled as placeholders and tied to a concrete evidence need.

### Spacing Specification

| Element | Current Project |
| ------- | --------------- |
| Safe margin from canvas edge | 56px |
| Content block gap | 22-32px |
| Card gap | 18-24px |
| Card padding | 18-22px |
| Card border radius | 6px |

---

## VI. Icon Usage Specification

Icons are optional and sparse. If used, use only one library:

| Purpose | Icon Path | Page |
| ------- | --------- | ---- |
| Process marker | `tabler-outline/circle-number-1` to `circle-number-5` | P06-P11 if needed |

No brand logos are used.

---

## VII. Visualization Reference List

| Page | Visualization | Data / Logic | Reference |
| ---- | ------------- | ------------ | --------- |
| P04 | Three-layer requirement model | image / structure / semantics | custom SVG diagram |
| P05 | Four-stage processing table | preprocessing / localization / recovery / OCR-postprocess | native table |
| P06 | Pipeline diagram | screenshot to CSV chain | custom SVG process band |
| P08 | Grid recovery schematic | horizontal and vertical lines to cells | custom SVG diagram |
| P12 | Iteration timeline | cloud OCR to local closed loop | custom SVG timeline |
| P14 | Improvement roadmap | detection / symbol parser / quantitative evaluation | custom SVG roadmap |

---

## VIII. Image Resource List

All image rows are placeholders. No AI image generation or web image retrieval is required.

| ID | File | Type | Acquire Via | Status | Intended Use |
| -- | ---- | ---- | ----------- | ------ | ------------ |
| fig_raw_screenshot | placeholder | screenshot placeholder | placeholder | Ready | P03 / P07 原始攻略截图 |
| fig_preprocess | placeholder | process placeholder | placeholder | Ready | P07 灰度、降噪、二值化对比 |
| fig_grid | placeholder | process placeholder | placeholder | Ready | P08 网格恢复结果 |
| fig_cell_ocr | placeholder | process placeholder | placeholder | Ready | P09 单元格 OCR 与复杂动作解析 |
| fig_csv | placeholder | output placeholder | placeholder | Ready | P11 CSV 输出示例 |

---

## IX. Content Outline

| Page | Rhythm | Title | Content Role |
| ---- | ------ | ----- | ------------ |
| P01 | anchor | 基于 OpenCV 与 OCR 的游戏攻略表格识别系统设计与实现 | Cover with dense abstract |
| P02 | dense | 摘要与关键词 | Teacher-readable summary |
| P03 | dense | 项目背景：为什么不能只看截图或直接 OCR | Problem context |
| P04 | dense | 需求分析：图像、结构、语义三层任务 | Requirement model |
| P05 | dense | 总体设计：先结构化图像，再识别文本 | System architecture |
| P06 | dense | 处理流程：从攻略截图到结构化 CSV | Pipeline |
| P07 | dense | 图像预处理：改善质量，但不承担结构判断 | Technique detail |
| P08 | dense | 表格区域定位与网格恢复 | Technique detail |
| P09 | dense | 单元格 OCR 与复杂动作解析 | Technique detail |
| P10 | dense | 后处理：把 OCR 文本转成业务数据 | Technique detail |
| P11 | dense | 输出结果与可调试性 | Evidence / output |
| P12 | dense | 系统迭代过程：从云端 OCR 到本地闭环 | Iteration timeline |
| P13 | dense | 当前方案特点 | Capability summary |
| P14 | dense | 存在问题与改进方向 | Limitations and roadmap |
| P15 | anchor | 总结：结构恢复是本项目的主线 | Closing summary |

---

## X. Speaker Notes Strategy

Speaker notes are short reading notes, not a live speech script. Each page gets 2-4 sentences explaining why the page exists and what a teacher should notice.

---

## XI. Technical Constraints

- SVG pages must use `viewBox="0 0 1280 720"`.
- Do not use external images; placeholders are native SVG shapes.
- Do not use CSS classes, external stylesheets, `foreignObject`, animation, scripts, or HTML entities.
- Keep all text as editable SVG text where possible.
- Prioritize stable PowerPoint export over decorative SVG tricks.
