# 星石本地 Paddle 网格识别设计

## 目标

- 只优化“星石统计功能”的本地 OCR 识别效果。
- 不修改拼图逻辑，不影响云端 OCR。
- 本地 OCR 从当前整图 `ML Kit Chinese OCR` 改为基于现有 `PaddleTextRecognizer` 的网格切块识别。
- 输出继续兼容现有 `StoneOcrParser`、`MyStoneStore.saveOcrResult` 和“我的星石”修正流程。

## 非目标

- 不修改 `InventoryStitchEngine` 的拼图、裁剪、分图、保存流程。
- 不修改云端 OCR 接口、策略或返回结构。
- 不在第一版接入新的 Debug 页面或调试面板。
- 不在第一版引入训练模型、目标检测模型或云端/本地融合识别。

## 现状

- 当前本地星石 OCR 位于 `StoneOcrCoordinator`。
- 本地策略实际使用 `ML Kit Chinese OCR` 对整张拼图直接识别。
- 当前实现没有利用星石列表稳定的“四列网格 + 金色圆盘 + 名称区/等级区固定分布”结构。
- 对很长的拼图图像，整图 OCR 会导致单个“名称/等级”目标过小，误识别和漏识别明显。

## 核心思路

将本地 OCR 流程改为：

`整图 -> 圆盘候选检测 -> 行列定位 -> 生成单卡片 ROI -> 切名称/等级小 ROI -> Paddle 识别 -> 组装为现有 parser 可消费的 token`

关键原则：

- 先做版式定位，再做 OCR。
- 先识别单个卡片的小区域，不再对整张长图直接 OCR。
- 复用项目已有 Paddle 能力，不改拼图链路。

## 总体方案

### 1. 入口保持不变

- `DailyWindowManager`、`MyStoneActivity` 继续调用 `StoneOcrCoordinator`。
- `StoneOcrCoordinator` 的 `LOCAL` 分支改为调用新的本地 Paddle 星石识别器。
- `CLOUD` 分支保持不变。

### 2. 新增本地识别器

新增文件：

- `app/src/main/java/com/example/yuanassist/utils/StonePaddleLocalRecognizer.kt`

职责：

- 从整张拼图中定位每颗星石的大致位置
- 为每颗星石生成卡片 ROI
- 切出等级 ROI 和名称 ROI
- 调用 `PaddleTextRecognizer`
- 产出 `StoneOcrCoordinator` 可直接写入的 `wordGroups/rawLogLines`

### 3. 输出继续兼容现有 parser

- 仍然输出按“行”组织的 token 列表
- 每一行优先输出：`4 个等级 token + 4 个名称 token`
- 继续复用 `StoneOcrParser.buildRows()`、`aggregate()`、`format()`
- 识别失败的格子保留为空，让现有红色待修正机制兜底

## 单个星石区域如何确定

### 1. 先找金色圆盘，而不是先找文字

星石卡片中最稳定的视觉特征是中心金色圆盘，而不是文字。

第一版通过 OpenCV 在整图上找圆盘候选：

- `Bitmap -> Mat`
- 转 HSV
- 用金色区间做 `inRange`
- 做闭运算和轻度膨胀，连通碎片
- 找轮廓或连通域框

候选过滤条件：

- 面积占整图比例不能太小
- 宽高比接近 1
- 宽高都要大于最小阈值
- 位置不能明显越界

这一步的目标不是做精确分割，而是拿到每个圆盘的大致中心点和尺寸。

### 2. 用圆盘中心聚成 4 列

星石列表是稳定四列布局。

处理方式：

- 取所有圆盘候选的 `centerX`
- 通过排序后聚类得到 4 组列中心
- 如果候选多于 4 列，按聚类后取稳定的 4 个主列
- 如果候选不足或聚类异常，回退为按整图宽度均分 4 列

这样横向列位置可以稳定下来，不依赖每一张图都完全干净。

### 3. 用圆盘中心聚成多行

处理方式：

- 取所有候选的 `centerY`
- 以一个固定容差做纵向聚类
- 每个聚类中心对应一行星石

如果某行只识别到 2 到 3 个圆盘，也仍然保留该行，后续按列中心补齐卡片 ROI。

### 4. 由“行中心 + 列中心 + 圆盘尺寸”反推卡片 ROI

不直接使用检测框作为最终卡片区域，而是统一用固定比例反推：

- 卡片中心：`(columnCenterX, rowCenterY)`
- 卡片宽度：由相邻列间距推导
- 卡片高度：由相邻行间距或圆盘平均尺寸推导

卡片 ROI 需要覆盖：

- 右上角等级
- 中部金色圆盘
- 下方名称

这样即使单个圆盘检测框略有偏移，也不会影响最终 OCR 区域太多。

## 卡片内部 ROI 设计

### 1. 等级 ROI

位置特征：

- 卡片右上
- 文字颜色偏白
- 通常是 `60级`、`57级`、`1级`

第一版策略：

- 从卡片上部偏右裁一块固定比例区域
- 识别后仅保留数字、`级` 以及常见误识别替换
- 结果交给 `StoneOcrParser.normalizeLevel()`

### 2. 名称 ROI

位置特征：

- 金色圆盘下方
- 文本通常为 2 个汉字，少数更长

第一版策略：

- 从卡片下半部居中裁一块固定比例区域
- Paddle 识别后做标准化
- 在 `StoneOcrParser.validStoneNamesForType(stoneType)` 中做合法名称匹配

### 3. ROI 预处理

名称和等级的图像特征不同，第一版允许做不同预处理：

- 等级 ROI：
  - 放大
  - 灰度化
  - 轻阈值增强
- 名称 ROI：
  - 放大
  - 保持灰度或轻对比度增强

第一版不追求参数自动搜索，先采用固定参数，确保链路可跑且便于调试。

## Paddle 识别策略

### 1. 不对整图调用 Paddle

整图过长时，小字占比过低，本地识别收益有限。

因此第一版只对以下小图块调用 `PaddleTextRecognizer`：

- 单卡片等级 ROI
- 单卡片名称 ROI

### 2. 结果后处理

等级后处理：

- 去空白
- 统一替换 `I/l/L/|` 为 `1`
- 提取数字和 `级`
- 交给 `StoneOcrParser.normalizeLevel()`

名称后处理：

- 去空白和标点
- 过滤非中文字符
- 与允许名称集合做匹配

名称匹配规则按以下优先级：

1. 完全匹配
2. 合法名称包含识别结果
3. 识别结果包含合法名称
4. 极短结果时不强猜，直接留空

第一版不引入复杂编辑距离，避免误修正过头。

## 与现有 parser 的衔接

现有 `StoneOcrParser` 已假设每行是 4 列结构。

因此本地识别器直接按每一行生成：

- `levels = [cell1, cell2, cell3, cell4]`
- `names = [cell1, cell2, cell3, cell4]`
- `wordGroups += levels + names`

这样可以继续走：

- `StoneOcrParser.buildRows()`
- `StoneOcrParser.aggregate()`
- `MyStoneStore.saveOcrResult()`

无需改“我的星石”存储模型。

## 容错与回退

### 1. 单格失败不拖垮整行

- 某个格子的名称或等级识别失败时，该 token 记为空
- 仍然保留该行其余格子
- 交给现有红色待修正机制

### 2. 列检测异常时的回退

- 如果圆盘 `centerX` 聚不出稳定 4 列
- 回退为整图宽度均分 4 列

### 3. 行检测异常时的回退

- 如果某些行候选不足
- 仍然用已识别的行中心生成 ROI
- 不再退回整图 ML Kit

### 4. 整图定位严重失败时

- 如果整张图有效候选数过少，直接抛出本地 OCR 失败
- 不自动切云端
- 保持当前用户自己选择云端 OCR 的交互方式

## 代码改动范围

### 1. `StoneOcrCoordinator.kt`

需要改动：

- 替换 `LOCAL` 分支实现
- 去掉当前整图 `ML Kit` 识别主路径
- 继续保留结果写回和日志输出逻辑

可保留：

- `StoneOcrImportResult`
- `StoneFileOcrResult`
- 云端 OCR 分支

### 2. 新增 `StonePaddleLocalRecognizer.kt`

建议包含：

- 圆盘候选检测
- 行列聚类
- 卡片 ROI 生成
- 名称/等级 ROI 裁剪
- Paddle 调用与后处理
- 最终 token 组装

### 3. `StoneOcrParser.kt`

只允许做小幅增强：

- 抽出或补充名称匹配辅助函数
- 保持现有行解析与聚合模型不变

### 4. 不改动文件

- `InventoryStitchEngine.kt`
- `DailyWindowManager.kt`
- 云端 OCR 网络层

## 建议的实现顺序

1. 新增 `StonePaddleLocalRecognizer`，先打通固定 ROI 到 token 的最小链路
2. 接入 `StoneOcrCoordinator.LOCAL`
3. 完成圆盘检测与行列聚类
4. 接入名称/等级后处理
5. 对失败场景补充容错
6. 最后再清理旧的 `ML Kit` 星石本地 OCR 逻辑

## 测试与验证范围

第一版验证重点：

- 单张正常拼图能输出可解析行
- 长拼图能识别多行且顺序正确
- 主星和辅星都能限制在各自名称集合内
- 局部失败时会保留红色待修正行，不写出完全空结果

暂不要求：

- 所有截图 100% 无需人工修正
- 对异常截图自动恢复到云端结果

## 风险

- 金色圆盘阈值对不同截图亮度可能有波动
- 某些拼图接缝附近可能出现半个卡片，影响行聚类
- 等级角标被头像、红点或 UI 遮挡时，仍可能识别失败
- 纯包含关系的名称匹配在极少数情况下可能误命中

## 后续可扩展项

- 给本地星石 OCR 增加调试日志或可视化 ROI 导出
- 在 Debug 页面增加“星石本地 Paddle OCR”调试入口
- 如果固定规则仍不够稳，再考虑引入轻量编辑距离匹配或更强的列/行校正

## 实施结论

第一版采用：

- 不改拼图
- 不改云端
- 本地 OCR 改为 `Paddle + 圆盘定位 + 四列网格切块 + 名称/等级分区识别`

这是当前约束下改动最可控、收益最高、与现有代码兼容性最好的方案。
