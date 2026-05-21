# 星石 OCR 模式切换设计

## 目标

把星石截图保存后的 OCR 入口改成三选项：`不OCR`、`本地OCR`、`云端OCR`，并且同时覆盖悬浮窗拼图完成后的弹窗与“我的星石”页面里的识别入口。

## 方案

- 两处入口都只负责弹出模式选择，不再各自实现 OCR 流程。
- 新增一个共用的星石 OCR 协调器，统一完成：
  - 读取已保存截图
  - 按模式执行本地或云端 OCR
  - 复用 `StoneOcrParser` 生成行数据、统计数据
  - 写回 `MyStoneStore.saveOcrResult`
- 云端 OCR 继续复用现有 `OcrManager.recognizeStoneImage(...)`。
- 本地 OCR 复用现有 `PaddleTextRecognizer`，把识别出的每一行文字按“等级 token + 星石名 token”切开，再交给 `StoneOcrParser`。

## 本地 OCR 约束

- 主星截图只匹配主星名称；辅星截图只匹配辅星名称。
- 不做本地失败自动切云端。
- 本地 OCR 如果完全没解析出可用 token，则直接报错，不写入空结果。
- 本地 OCR 如果解析出了部分结果但有未闭合行，仍按现有逻辑写入红色待修正行。

## 影响范围

- `app/src/main/java/com/example/yuanassist/core/DailyWindowManager.kt`
- `app/src/main/java/com/example/yuanassist/ui/MyStoneActivity.kt`
- `app/src/main/java/com/example/yuanassist/utils/StoneOcrParser.kt`
- 新增共用协调器文件
