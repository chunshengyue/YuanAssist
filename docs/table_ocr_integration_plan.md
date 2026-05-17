# 攻略表格识别集成计划

## 目标

在 `TestActivity` 功能检测页面新增"文字识别"任务，素材为"攻略表格"，点击开始测试后对游戏攻略表格截图执行完整的表格检测+OCR识别，输出结构化回合制动作表。

## 技术栈

- **OCR 引擎**：PaddleOCR PP-OCRv5_mobile_rec（Paddle Lite + JNI，仅识别模型）
- **表格分析**：OpenCV for Android（已有依赖 `org.opencv:opencv:4.9.0`）
- **文字解析**：Kotlin 正则 + 字符修复映射

## 模型需求分析

OpenCV 已精确切割出每个单元格，因此：

| 模型 | 是否需要 | 原因 |
|------|----------|------|
| `PP-OCRv5_mobile_rec.nb` | **只需要这个** | 识别裁剪后的小图 → 字符串 |
| `ppocr_keys_ocrv5.txt` | **需要** | CTC 解码字典 |
| `PP-OCRv5_mobile_det.nb` | 不需要 | 文字检测 — 单元格已精确裁剪 |
| `PP-LCNet_x0_25_textline_ori.nb` | 不需要 | 方向分类 — 数字箭头皆为水平正向 |
| `config.txt` | 不需要 | 检测阈值参数 |

### 简化后的 JNI 层

不需要完整的 det→cls→rec pipeline（无需 `ocr.zip`），只需一个轻量 JNI wrapper：

```
Kotlin: PaddleOcrNative.recognize(mat) → String
   ↓
JNI:    Mat → 预处理(resize 3x48) → rec model 推理 → CTC decode → text
```

C++ 层只需 rec 模型的预处理 + 推理 + CTC 解码逻辑，参考官方 rec demo 或自行实现。

---

## 阶段 A：PaddleOCR v5 rec 模型集成

### A1. 下载资源

| 资源 | 下载链接 | 目标位置 |
|------|----------|----------|
| `PP-OCRv5_mobile_rec.nb` | `https://paddlelite-demo.bj.bcebos.com/paddle-x/ocr/models/PP-OCRv5_mobile_rec.tar.gz` | `assets/ocr/models/` |
| `ppocr_keys_ocrv5.txt` | `https://paddlelite-demo.bj.bcebos.com/demo/ocr/labels/labels.tar.gz` | `assets/ocr/labels/` |
| `libpaddle_light_api_shared.so` | `https://paddlelite-demo.bj.bcebos.com/paddle-x/libs/android/paddle_lite_libs_v2_14_rc.tar.gz` | `jniLibs/arm64-v8a/` |
| `libc++_shared.so` | 同上 | `jniLibs/arm64-v8a/` |
| `paddle_inference_api.h` 等头文件 | 同上 | `cpp/paddlelite/` |

### A2. C++ JNI 层

```
app/src/main/cpp/
  CMakeLists.txt
  native_ocr_jni.cpp             # JNI: init / recognize / release
  rec_wrapper.h / .cc            # rec model 预处理 + 推理 + CTC decode
```

**JNI 接口：**

```cpp
JNIEXPORT jboolean JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeInit(
    JNIEnv*, jclass, jstring recModelPath, jstring labelPath);

JNIEXPORT jstring JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeRecognize(
    JNIEnv*, jclass, jobject bitmap);

JNIEXPORT void JNICALL
Java_com_example_yuanassist_tableocr_PaddleOcrNative_nativeRelease(
    JNIEnv*, jclass);
```

**rec 推理流程：**
1. Bitmap → raw pixels (BGR/GRAY)
2. Resize 到 `height=48, width` 保持比例，padding 到 320x48
3. Normalize: `(pixel / 255 - 0.5) / 0.5`
4. `PaddleLitePredictor::Run()` → output tensor
5. CTC softmax + argmax → 索引序列 → 字典查表 → text

### A3. build.gradle.kts 修改

```kotlin
defaultConfig {
    externalNativeBuild {
        cmake {
            cppFlags += listOf("-std=c++17", "-frtti", "-fexceptions")
            arguments += listOf("-DANDROID_STL=c++_shared")
        }
    }
    ndk {
        abiFilters += listOf("arm64-v8a")
    }
}

externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}

sourceSets {
    getByName("main") {
        jniLibs.srcDirs("src/main/jniLibs")
    }
}
```

### A4. Kotlin 封装层

| 文件 | 职责 |
|------|------|
| `tableocr/PaddleOcrNative.kt` | JNI wrapper: `nativeInit()`, `nativeRecognize(bitmap)`, `nativeRelease()` |
| `tableocr/AssetCopier.kt` | `copyOcrAssetsToFilesDir(context)` — 首次启动复制 `assets/ocr/` 到 `filesDir/ocr/` |

---

## 阶段 B：表格分析 Kotlin 层

所有文件放在 `tableocr/` package 下。

### B1. `tableocr/TableOcrModels.kt`

```kotlin
data class CellBox(val row: Int, val col: Int, val x: Int, val y: Int, val w: Int, val h: Int)
data class TableStructure(val rowLines: List<Int>, val colLines: List<Int>)
data class LineSegment(val top: Int, val bottom: Int, val height: Int, val pixelDensity: Float, val isNoise: Boolean)
data class RowResult(val roundLabel: String, val actions: List<String>)
enum class ConfidenceLevel { HIGH, MEDIUM, LOW }
data class ParseResult(val text: String, val isComplete: Boolean, val fragment: String = "", val confidence: ConfidenceLevel = ConfidenceLevel.HIGH, val wasFixed: Boolean = false)
```

### B2. `tableocr/TableDetector.kt` — 移植 `preprocess.py`

| 函数 | 算法 |
|------|------|
| `toBinary(mat)` | 灰度化 → GaussianBlur(5x5) → AdaptiveThreshold(block=31, C=15, BINARY_INV) |
| `extractTableRegion(mat)` | 二值图 → 闭运算(kernel ≈ w/30 × h/30, iterations=2) → findContours → 最大外轮廓 boundingRect → 裁切 |

### B3. `tableocr/GridDetector.kt` — 移植 `grid.py`

| 函数 | 算法 |
|------|------|
| `detectTableStructure(binary)` | 水平 MorphOpen(w/8 × 1) → 投影 → cluster → rowLines；垂直 MorphOpen(1 × h/8) → 投影 → cluster → colLines |
| `clusterPositions(projection)` | 1D 聚类：相邻 ≤1 归一组，取组均值 |
| `buildCellBoxes(structure)` | 行线两两配对 + 列线两两配对 → 生成 CellBox 列表，前 6 列 |

投影用 OpenCV `Core.reduce(src, dst, dim, Core.REDUCE_SUM)`。

### B4. `tableocr/CellLayoutAnalyzer.kt` — 移植 `layout.py`

| 函数 | 算法 |
|------|------|
| `analyze(crop)` | OTSU 二值化 + 反转 → 水平投影 → 连续非零行 → 过滤 <4px → 合并 ≤3px 间距 → pixelDensity → 标记噪声 |
| `isComplexCell(crop)` | 有效段 ≥ 2 |
| `splitLines(crop)` | 对每段 crop，±2px padding |

### B5. `tableocr/ActionParser.kt` — 移植 `action_parser.py`

| 功能 | 实现 |
|------|------|
| 字符修复映射 | `I/l→1, O→0, T/t→↑, V/v/Y/y→↓, √/」/』→↓, {→↑, \\→↓` |
| 单动作匹配 | Regex `^(10\|[1-9])([A↑↓])(/A)?$` |
| 复杂串匹配 | Regex `(?:10\|[1-9])(?:↑/A\|↓/A\|↑A\|↓A\|圈\|A\|↑\|↓)` findAll |
| 置信度 | 完整无修复→HIGH；完整有修复→MEDIUM；部分→LOW+fragment |

### B6. `tableocr/PostProcessor.kt` — 移植 `postprocess.py`

| 函数 | 算法 |
|------|------|
| `normalizeRoundText(text)` | 去空格 → 数字修复 → 提取数字 → `"N回合"` |
| `normalizeActionText(text)` | 去空格 → 字符修复 → 单动作/复杂串匹配 → 归一化输出 |

---

## 阶段 C：OCR 调度引擎

### C1. `tableocr/OcrScheduler.kt` — 移植 `ocr.py` 调度逻辑

| 函数 | 流程 |
|------|------|
| `ocrRoundCell(crop)` | 放大 4x + 白边 8px → PaddleOCR 识别 → 返回文本 |
| `ocrActionCell(crop)` | layout 分析 → 多文本带？→ 逐行 OCR + parser.parse → join；否则整格 OCR → 含圈/多数字？→ parser.parse；否则 assembleActionText |
| `assembleActionText(crop, fullText)` | OTSU 二值化+反转 → connectedComponentsWithStats → 过滤边框 → 数字OCR + 箭头分类(上下1/3像素质量对比) → 组合 |

**箭头分类算法：**
```kotlin
fun classifyArrow(binary: Mat, component: Rect): String {
    val rowSums = IntArray(component.height)
    Core.reduce(Mat(binary, component), rowSums, 1, Core.REDUCE_SUM, CvType.CV_32S)
    val window = maxOf(1, rowSums.size / 3)
    val topMass = rowSums.take(window).sum()
    val bottomMass = rowSums.takeLast(window).sum()
    return when {
        topMass >= bottomMass + 2 -> "↑"
        bottomMass >= topMass + 2 -> "↓"
        else -> ""
    }
}
```

### C2. `core/TableOcrEngine.kt` — 全流程编排

```
1. loadImage(Bitmap) → Mat
2. extractTableRegion() → 裁切表格
3. toBinary() → 二值化
4. detectTableStructure() + buildCellBoxes() → 单元格列表
5. init PaddleOCR（首次调用）
6. 每格 ocrRoundCell / ocrActionCell → raw text
7. normalizeRoundText / normalizeActionText → 归一化
8. groupTextRows() → List<RowResult>
9. 返回结果
```

---

## 阶段 D：TestActivity 接入

### D1. 常量定义

```kotlin
private const val TASK_TABLE_OCR = "TASK_TABLE_OCR"
private const val TABLE_OCR_TEMPLATE = "TABLE_OCR_STRATEGY"
```

### D2. 注册到 TASK_ORDER / displayName / templateOptions

```kotlin
// TASK_ORDER 添加 TASK_TABLE_OCR
// TASK_DISPLAY_NAME_MAP[TASK_TABLE_OCR] = "文字识别"
// templateOptionsByTask: buildTableOcrTemplateOptions() → listOf(TABLE_OCR_TEMPLATE)
// displayName: TABLE_OCR_TEMPLATE → "攻略表格"
// scopeHint: "对截图执行表格定位→切格→逐格OCR→结构化输出"
```

### D3. runCurrentTest() 分支

```kotlin
selectedTemplate == TABLE_OCR_TEMPLATE -> runTableOcrTest()
```

### D4. `runTableOcrTest()` 实现

```kotlin
private fun runTableOcrTest() {
    val bitmap = currentBitmap ?: run {
        Toast.makeText(this, "请先上传截图", Toast.LENGTH_SHORT).show()
        return
    }
    log("======== 攻略表格识别 ========")
    try {
        val startMs = System.currentTimeMillis()
        val rows = TableOcrEngine.recognize(this, bitmap)
        val elapsed = System.currentTimeMillis() - startMs

        // 输出表格
        log("回合   | 动作1 | 动作2 | 动作3 | 动作4 | 动作5")
        log("-------|-------|-------|-------|-------|------")
        for (row in rows) {
            val paddedRound = row.roundLabel.padEnd(6)
            val actions = row.actions.map { it.padEnd(6) }.joinToString("|")
            log("$paddedRound|$actions")
        }
        log("总行数: ${rows.size}  耗时: ${elapsed}ms")
    } catch (e: Exception) {
        log("识别失败: ${e.message}")
    }
}
```

### D5. 例图放置

将 `MyApplication/例图1.jpg`、`例图2.jpg`、`例图3.jpg` 复制到 `app/src/main/assets/ocr/test_images/`。

---

## 文件清单

### 新建文件

| # | 文件路径 | 阶段 |
|---|----------|------|
| 1 | `app/src/main/assets/ocr/models/PP-OCRv5_mobile_rec.nb` | A1 |
| 2 | `app/src/main/assets/ocr/labels/ppocr_keys_ocrv5.txt` | A1 |
| 3 | `app/src/main/assets/ocr/test_images/例图1.jpg` | D5 |
| 4 | `app/src/main/assets/ocr/test_images/例图2.jpg` | D5 |
| 5 | `app/src/main/assets/ocr/test_images/例图3.jpg` | D5 |
| 6 | `app/src/main/cpp/CMakeLists.txt` | A2 |
| 7 | `app/src/main/cpp/native_ocr_jni.cpp` | A2 |
| 8 | `app/src/main/cpp/rec_wrapper.h` | A2 |
| 9 | `app/src/main/cpp/rec_wrapper.cc` | A2 |
| 10 | `.../yuanassist/tableocr/PaddleOcrNative.kt` | A4 |
| 11 | `.../yuanassist/tableocr/AssetCopier.kt` | A4 |
| 12 | `.../yuanassist/tableocr/TableOcrModels.kt` | B1 |
| 13 | `.../yuanassist/tableocr/TableDetector.kt` | B2 |
| 14 | `.../yuanassist/tableocr/GridDetector.kt` | B3 |
| 15 | `.../yuanassist/tableocr/CellLayoutAnalyzer.kt` | B4 |
| 16 | `.../yuanassist/tableocr/ActionParser.kt` | B5 |
| 17 | `.../yuanassist/tableocr/PostProcessor.kt` | B6 |
| 18 | `.../yuanassist/tableocr/OcrScheduler.kt` | C1 |
| 19 | `.../yuanassist/core/TableOcrEngine.kt` | C2 |

### 修改文件

| # | 文件路径 | 阶段 |
|---|----------|------|
| 1 | `app/build.gradle.kts` | A3 |
| 2 | `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt` | D1-D4 |

### 资源下载（需网络）

| # | 资源 | 下载来源 |
|---|------|----------|
| 1 | 模型 + 字典 | Paddle Lite demo CDN |
| 2 | Paddle Lite .so + headers | Paddle Lite demo CDN |
