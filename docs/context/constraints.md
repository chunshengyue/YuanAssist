# 硬约束与易忽略点

> 不能违反的硬约束、容易忽略的事实，以及改动前的检查建议。
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [modules](modules.md)

---

## 关键硬约束
### 1. 不要混用坐标基准
- 日常脚本、模板匹配、OCR、角色导入这条主线，核心基准是 `1080x1920`。
- `AutoTaskEngine` 和 `CharacterImportEngine` 都有 `BASE_W = 1080f`、`BASE_H = 1920f`。
- 模板 ROI、识别中心点、替换模板时的选区语义，都应默认按这套基准理解。
- 这意味着很多素材、坐标点、ROI 都是以标准宽 1080 的截图/界面定位出来的。

### 2. 项目里还保留一套老战斗坐标体系
- `CoordinateManager` 使用的是 `1440x2560` 设计图逻辑。
- 它更偏老战斗列位/动作分发坐标，不等同于当前日常脚本/OCR 的 `1080x1920` 体系。
- 修改坐标逻辑时，必须先确认你碰的是哪条链路。

### 3. UI 对齐状态不是随便填的
- 日常任务和识别链路大量使用 `align = top / center / bottom`。
- 这不是普通布局语义，而是映射到游戏画面在屏幕中的不同锚点状态。
- 脚本编辑器 `RecordedDailyScriptViewerActivity` 也只提供这三个选项。
- 处理 Unity 游戏界面时，要优先沿用这套状态，而不是引入新的对齐枚举。

### 4. 调试工作台是模板/OCR 的标准验证入口
- 主界面存在 `DEBUG` 页，核心协调器是 `DebugWorkbenchCoordinator`。
- 它负责：
  - 加载截图
  - 选择任务与模板节点
  - 查看 ROI/范围说明
  - 替换模板
  - 恢复模板
  - 调整识别前后延时
  - 测 OCR、模板命中、角色识别等
- 理论上，新增或调整模板匹配时，优先考虑在这里接入调试能力，而不是只改运行时逻辑不留验证入口。

### 5. 用户模板与内置模板是两层来源
- 内置模板在 `assets` 下。
- 调试替换后的覆盖模板通过 `TemplateOverrideStore` 保存在应用私有目录 `files/template_overrides`。
- 读取模板时，默认优先读 override，再回退到 asset。
- 所以“替换模板”通常不是直接改 asset，而是先走 override 机制。

### 6. 用户录制脚本有独立存储结构
- 用户脚本不直接写回 `assets`。
- `UserDailyScriptStore` 将其保存在应用私有目录 `files/user_daily_scripts/<scriptId>/`。
- 每个 bundle 里至少有：
  - `script.json`
  - `templates/`
- 录制脚本改模板名时，`syncTemplateFilesForPlanUpdate` 会尝试同步模板文件改名或复制。

### 7. 日常模块依赖安卓版本和权限
- `HomeActionHandler` 中 `supportsDailyModule()` 要求 Android R 及以上。
- 日常工具、悬浮窗、坐标选点、脚本录制等功能依赖悬浮窗权限与无障碍服务开启。
- 排查“功能点不开/没反应”时，先确认权限与系统版本，再看业务逻辑。

## 容易忽略的点
- 编写、审查或修复 PowerShell 语法时，优先使用用户级已安装的 `powershell-command-runner` skill；具体命令格式仍以根目录 `AGENTS.md` 为准。
- 很多关键常量直接写在协调器或引擎里，不一定抽到统一配置层。
- `DebugWorkbenchCoordinator` 里维护了大量任务、模板、ROI、阈值和角色特殊点位，是识别规则的重要事实来源。
- 角色导入不仅依赖 OCR，还叠加了名字纠错、命盘匹配、候选打分；不要把它当成简单 OCR 页面。
- `tableocr/PaddleOcrNative.kt` 对外仍保持 `init / recognize / detect` 接口，但底层已从 Paddle Lite/JNI 迁移为 Android ONNX Runtime；背包拼接的文字行识别也复用此链路，不再依赖 ML Kit；不要再把 Paddle 3 的 v6 PIR 模型直接转换为旧 `.nb` 格式。
- `RecordedDailyScriptViewerActivity` 不只是查看器，它也是脚本结构编辑器，支持改起始任务、增删节点、分支查看、导出。
- 模板替换、脚本编辑、延时覆盖三者是分开的持久化层；延时覆盖保存到偏好设置，运行时通过 plan 覆盖应用，不会改写脚本 JSON。

## 修改建议
- 涉及模板匹配、OCR、ROI、点位的改动，先确认所属链路是 `1080x1920` 还是 `1440x2560`。
- 涉及日常脚本节点的改动，优先保持和 `RecordedDailyScriptViewerActivity`、`DailyScriptDebugIndex`、调试页链路一致。
- 涉及模板素材调整，优先保留调试页验证与 override 能力。
- 涉及新识别点时，优先考虑是否需要在 `DebugWorkbenchCoordinator` 增加对应测试入口。
