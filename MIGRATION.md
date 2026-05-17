# table-ocr → Android (PaddleOCR Mobile) 迁移指南

## 数据流总览

```
Input Image (jpg/png)
    │
    ▼
┌─────────────────────┐
│  preprocess.py      │  图像预处理
│  (extract_table_    │  二值化 → 形态学闭运算 → 最大轮廓 → 裁切
│   region)           │
└─────────┬───────────┘
          │ table region crop
          ▼
┌─────────────────────┐
│  grid.py            │  表格结构检测
│  (detect_table_     │  水平/垂直形态学开运算 → 投影聚类 → 行列线
│   structure +       │  → 生成 CellBox (row, col, x, y, w, h)
│   build_cell_boxes) │
└─────────┬───────────┘
          │ list of CellBox
          ▼
┌─────────────────────┐
│  ocr.py             │  OCR 调度
│  (ocr_cells →       │  第0列 → ocr_round_cell (回合数)
│   ocr_action_cell)  │  其余列 → ocr_action_cell (动作格)
│                     │     ├─ layout.py 判断是否多行复杂格
│                     │     │   ├─ 是 → 逐行裁切 → 逐行 OCR → action_parser 解析
│                     │     │   └─ 否 → 整格 OCR → 组件补救 → 输出
│                     │     └─ postprocess.py 做最终归一化
└─────────┬───────────┘
          │ 每格识别文本
          ▼
┌─────────────────────┐
│  pipeline.py        │  流程编排
│  (group_text_rows)  │  按行分组 → 去掉空行 → 写 CSV
└─────────┬───────────┘
          │
          ▼
       CSV Output
```

---

## 各文件职责 & 迁移要点

### 1. `preprocess.py` — 图像预处理

**算法：**
1. 读图（`fromfile + imdecode`，支持中文路径）
2. `to_binary`: 灰度化 → GaussianBlur(5×5) → AdaptiveThreshold(block=31, C=15)
3. `extract_table_region`: 二值图 → 闭运算(大核 ≈ 图宽/30 × 图高/30) → 找最大外轮廓 → 裁切

**Android 迁移：** 全部 OpenCV 操作可直接用 Java/NDK 实现。`AdaptiveThreshold` 是标准 API。

### 2. `grid.py` — 表格结构检测（0 OpenCV 调用，纯投影聚类）

**算法：**
1. `detect_table_structure(binary_image)`:
   - 水平 MorphOpen 核 (width/8 × 1) → 提取水平线 → 水平投影 → 聚类 → 行线 Y 坐标
   - 垂直 MorphOpen 核 (1 × height/8) → 提取竖直线 → 垂直投影 → 聚类 → 列线 X 坐标
2. `build_cell_boxes`: 行线两两配对 → 列线两两配对 → 生成 (row, col, x, y, w, h)
   - 只取前 6 列（1 列回合号 + 5 列动作格）

**算法核心：** `_cluster_positions(mask)` — 对投影直方图做 1D 聚类，相邻距离 ≤ 1 的归为一组，取组均值作为线位置。

**Android 迁移：** 全部是 numpy 操作，Android 端需写等价逻辑。OpenCV 提供了 `reduce(SUM)` 做投影，聚类逻辑需要手写（但很简单：等差+均值）。

### 3. `layout.py` — 单元格内子行切分（CellLayoutAnalyzer）

**算法：**
1. 单元格图片 → OTSU 二值化 → 反转（文字白，背景黑）
2. 水平投影（row_sums） → 找到连续非零行作为文本带
3. 过滤 < 4px 的短带
4. 合并相距 ≤ 3px 的相邻带
5. 计算每条 `pixel_density = 像素和 / (宽 × 高)`，< 1% 标记为噪声
6. `is_complex_cell`: 有效段 ≥ 2 即为复杂格
7. `split_lines`: 对每段 crop 出来（±2px padding）

**判断复杂格的启发式（`ocr_action_cell` 中）：**
- 布局检测到 ≥ 2 条文本带 → 复杂
- 或整格 OCR 结果含 `圈` 或 ≥ 2 个数字 → 复杂

**Android 迁移：** 核心是水平投影 + 阈值 + 聚类，全部是像素操作，无特殊依赖。

### 4. `action_parser.py` — 动作串 Token 解析（ActionSequenceParser）

**算法：**
1. `_clean(text)`: 去空格 → 字符修复映射表
2. 单动作匹配 `^(10|[1-9])([A↑↓])(/A)?$`
3. 复杂串匹配：`findall` 找 `(?:10|[1-9])(?:↑/A|↓/A|↑A|↓A|圈|A|↑|↓)`
4. 完整匹配 → HIGH 置信；需字符修复 → MEDIUM；部分匹配 → LOW（保留 fragment）

**置信度规则：**

| 等级 | 条件 | 输出 |
|---|---|---|
| HIGH | token 完整匹配，无字符修复 | 直接输出 |
| MEDIUM | token 完整，但经过字符纠正 | 输出纠正串 |
| LOW | 部分解析，存在残片 | 已确认 token + fragment |

**支持的 token 类型：**
- `\d+A` → 4A, 10A
- `\d+↑` → 3↑, 1↑
- `\d+↓` → 2↓, 5↓
- `\d+圈` → 7圈
- `\d+↓A` → 6↓A
- `\d+↑A` → 4↑A
- `\d+↑/A` → 4↑/A
- `\d+↓/A` → 6↓/A

**字符修复映射（OCR 常见误读）：**
- 数字: I/l→1, O→0
- 上箭头: T/t→↑, {→↑
- 下箭头: V/v/Y/y→↓, 」/』→↓, \\→↓, √→↓

**Android 迁移：** 纯字符串解析，无第三方依赖。直接用 Java/Kotlin 的 `Regex` 实现。

### 5. `ocr.py` — OCR 调度

**职责：**
- `ocr_round_cell` — 第一列回合号 OCR（只走整格识别）
- `ocr_action_cell` — 动作格 OCR（含复杂/简单格分流逻辑）
- `_assemble_action_text` — 简单格组件补救（连通域分析：分离数字和箭头 → 分别 OCR → 组合）

**`ocr_action_cell` 分流逻辑（`src/table_ocr/ocr.py:210`）：**

```
cell crop
  │
  ├─ layout分析 → 多条文本带？
  │    ├─ 是 → 逐行裁切 → OCR → parser.parse(每行) → join
  │    └─ 否 → 整格 OCR
  │              │
  │              ├─ 含圈/多数字？ → parser.parse(全文)
  │              │    ├─ 完整 → 输出
  │              │    └─ 部分 → text + fragment
  │              │
  │              └─ 简单格 → _assemble_action_text(组件补救)
  │                          （连通域分析 → 数字 OCR + 箭头分类 → 组合）
```

**`_assemble_action_text` 算法（简单格组件补救）：**
1. 二值化 → 连通域分析 → 过滤边框组件（触边 + 细/长）
2. 第一连通域 → 数字 → 高分辨率 OCR
3. 第二连通域 → 箭头分类（上下半区像素质量对比）
4. 组合为 `数字 + 箭头/A` 格式

**Android 迁移关键：**
- 本文件里 `_recognize_text` **需要替换为 PaddleOCR Mobile SDK 调用**
- `_prepare_crop`（resize + padding）用 Android `Bitmap` 或 NDK 实现
- `crop_cell` 就是图片裁剪，标准操作
- 连通域分析 `connectedComponentsWithStats` → OpenCV for Android 可用

### 6. `postprocess.py` — 最终归一化

**两个函数：**
- `normalize_round_text`: 处理回合号（"第3回合" → "3回合"，"IA回合" → "1回合"）
- `normalize_action_text`: 处理动作串（和 `action_parser.py` 逻辑重叠，但 `action_parser` 在 OCR 时先跑，这个是 pipeline 层的兜底）

**Android 迁移：** 纯字符串替换 + 正则匹配。

### 7. `pipeline.py` — 流程编排

**步骤：**
1. 加载图片
2. 提取表格区域
3. 二值化
4. 检测表格结构 → 切格
5. OCR 每格
6. 归一化
7. 按行分组（跳过空行）
8. 导出 CSV

### 8. `models.py` — 数据结构

| 类 | 字段 | 用途 |
|---|---|---|
| `CellBox` | row, col, x, y, w, h | 单个单元格位置 |
| `LineSegment` | top, bottom, height, pixel_density, is_noise | 子行切分段 |
| `RowResult` | round_label, actions | 输出的一行结果 |
| `ParseResult` | text, is_complete, fragment, confidence, was_fixed | parser 产出 |
| `TableStructure` | row_lines, col_lines | 行列线位置 |

### 9. `exporter.py` — CSV 导出

简单的 CSV 写入，Android 端多半不需要（直接输出结构体）。

### 10. `cli.py` — CLI 解析

Android 迁移：不需要。

---

## Android 集成建议

### 核心迁移清单（需手动移植）

| 优先级 | 模块 | 算法复杂度 | 说明 |
|---|---|---|---|
| P0 | `grid.py` | ★★☆ | 表格线检测 + 聚类，OpenCV 实现 |
| P0 | `layout.py` | ★☆☆ | 水平投影 + 阈值，纯像素操作 |
| P0 | `ocr.py:ocr_action_cell` 分流逻辑 | ★☆☆ | 状态判断，替换 OCR 调用为 Mobile SDK |
| P0 | `action_parser.py` | ★☆☆ | 正则 + 字符串处理 |
| P1 | `preprocess.py` | ★☆☆ | 二值化 + 形态学 |
| P1 | `ocr.py:_assemble_action_text` | ★★☆ | 连通域 + 箭头分类 |
| P2 | `postprocess.py` | ★☆☆ | 字符串归一化 |
| P3 | debug 输出 | ★☆☆ | 仅开发调试用 |

### PaddleOCR Mobile 集成点

唯一需要替换的地方是 `ocr.py` 中的 `_recognize_text(image) → str`。

```kotlin
// Android 侧等价签名
fun recognizeText(bitmap: Bitmap): String {
    // 调用 PaddleOCR Mobile SDK
    // 输入：放大 4x + 8px 白边 padding 后的 Bitmap
    // 输出：识别文本字符串
}
```

### 数据结构对应

```kotlin
// Android 端
data class CellBox(val row: Int, val col: Int, val x: Int, val y: Int, val w: Int, val h: Int)
data class LineSegment(val top: Int, val bottom: Int, val height: Int, val pixelDensity: Float, val isNoise: Boolean)
data class ParseResult(val text: String, val isComplete: Boolean, val fragment: String, val confidence: ConfidenceLevel)
enum class ConfidenceLevel { HIGH, MEDIUM, LOW }
```

---

## 关键算法速查

| 算法 | 文件:行号 | 核心思想 |
|---|---|---|
| 表格区域提取 | `preprocess.py:38` | 大核闭运算 → 最大外轮廓 |
| 表格行列线检测 | `grid.py:41` | 水平/垂直 MorphOpen → 1D 投影聚类 |
| 子行切分 | `layout.py:36` | 水平投影 → 找连续文本带 → 过滤 + 合并 |
| 动作串 Token 解析 | `action_parser.py:57` | 正则 findall → 完整/部分匹配判定 |
| 简单格组件补救 | `ocr.py:154` | 连通域 → 数字 OCR + 箭头质量分类 |
| 箭头方向分类 | `ocr.py:133` | 上下 1/3 像素质量对比 |
| 置信度判定 | `action_parser.py:68` | 有无字符修复 + 是否完整匹配 |
