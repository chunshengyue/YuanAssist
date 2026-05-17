# TestActivity 自定义脚本接入设计

## 目标

把 `DailyScriptRecorderManager` 生成的用户脚本包接入功能测试页：

- 任务下拉与内置任务并列显示用户脚本
- 选中用户脚本后，素材下拉显示该脚本包 `templates/` 下的图片
- 局部识别直接使用该脚本 `script.json` 中对应节点的 `roi` 和 `threshold`
- 模板图优先从该脚本包目录读取，而不是固定从 `assets` 读取

## 数据来源

### 内置任务

- 继续使用 `TestActivity` 现有常量、`assets/daily_scripts/*.json`、`assets` 模板图

### 用户脚本任务

- 通过 `UserDailyScriptStore.listBundles(context)` 获取脚本包列表
- 每个脚本包包含：
  - `script.json`
  - `templates/*.png`

## 设计

### 1. 任务列表

测试页内部引入“任务来源”概念，但 UI 仍然只显示一套并列任务列表。

- 内置任务：保留原 key
- 用户任务：使用脚本包 `scriptId` 作为唯一 key

同时维护任务元数据，区分：

- 展示名
- 是否内置
- 对应 `UserDailyScriptBundle`

### 2. 素材列表

选中任务后：

- 内置任务：沿用当前素材构建逻辑
- 用户任务：读取该 bundle 的 `script.json`，收集其中 `MATCH_TEMPLATE` 节点的 `template_name`

素材列表只展示当前脚本实际存在的模板名，不追加内置专用测试项。

### 3. 局部识别区域

为用户脚本建立与现有 `localSearchRegionsByTaskTemplate` 同结构的数据：

- key: `taskTemplateKey(scriptId, templateName)`
- value: 来自 `script.json` 中匹配节点的 `roi`、`threshold`、`taskId`

局部测试时无需分支改算法，只要让现有 `taskScopedRegions()` 能取到用户脚本的区域数据即可。

### 4. 模板图片读取

模板测试执行时按任务来源读取模板图：

- 内置任务：沿用 `TemplateOverrideStore.loadBitmap(...)`
- 用户任务：直接从 `bundle.templatesDir/<templateName>` 读取 Bitmap

这样局部测试、全屏测试都能复用现有匹配流程。

### 5. 一键替换/还原/延迟面板

本次只接入“任务选择、素材选择、局部测试”主链路。

- 内置任务：保留现有行为
- 用户任务：
  - 一键替换/还原按钮禁用
  - 延迟增量面板按 `script.json` 中匹配到的任务节点正常展示

不新增补丁式兼容逻辑，不改录制脚本格式。

## 逻辑校验

完整链路：

1. 录制器生成脚本包
2. 功能测试页读取脚本包列表并显示在任务下拉
3. 用户选择某个脚本包后，素材下拉显示该包模板图
4. 用户开启局部识别时，测试页从该包 `script.json` 里取对应素材的 ROI 与阈值
5. 测试页从该包 `templates/` 里读取模板图并执行匹配

链路中任务来源、素材来源、ROI 来源保持一致，避免“任务选自定义但素材/ROI 仍走内置”的错配。
