# YuanAssist Project Context

## 文档目标
- 这份文档不是完整设计说明，而是给新 agent 的快速导航。
- 重点回答 3 个问题：这个项目主要由哪些模块组成、遇到某类需求该先去哪里找、有哪些硬约束不能忽略。

## 项目概览
- 这是一个 Android 单模块应用，主入口是 `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`。
- App 既有常规页面，也有基于无障碍服务的自动化执行能力。
- 自动化能力主要服务于游戏场景，核心运行时服务是 `app/src/main/java/com/example/yuanassist/core/YuanAssistService.kt`。
- 主界面是 Compose 壳，包含 Home、Job、Debug、Mine 四个 Tab；其中 Debug 是调试工作台入口。

## 先看哪里
- 想找主界面入口与功能跳转：`app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- 想找首页按钮怎么进入各功能：`app/src/main/java/com/example/yuanassist/ui/main/HomeActionHandler.kt`
- 想找无障碍服务、悬浮窗、服务 action 分发：`app/src/main/java/com/example/yuanassist/core/YuanAssistService.kt`
- 想找日常脚本执行引擎：`app/src/main/java/com/example/yuanassist/core/AutoTaskEngine.kt`
- 想找调试页/模板调试/OCR 调试：`app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt`
- 想找录制脚本的查看与编辑：`app/src/main/java/com/example/yuanassist/ui/RecordedDailyScriptViewerActivity.kt`
- 想找用户脚本与模板存储：`app/src/main/java/com/example/yuanassist/utils/UserDailyScriptStore.kt`
- 想找模板替换覆盖逻辑：`app/src/main/java/com/example/yuanassist/utils/TemplateOverrideStore.kt`
- 想找角色导入识别链路：`app/src/main/java/com/example/yuanassist/core/CharacterImportEngine.kt`

## 主要目录
- `app/src/main/java/com/example/yuanassist/core`
  - 运行时引擎、无障碍服务、自动任务、录制器、角色导入、桥接层。
- `app/src/main/java/com/example/yuanassist/ui`
  - Activity、Fragment、悬浮 UI、脚本编辑器、各功能页面。
- `app/src/main/java/com/example/yuanassist/ui/main`
  - Compose 主壳、Home/Job/Debug/Mine 页面、调试工作台。
- `app/src/main/java/com/example/yuanassist/model`
  - 任务 plan、task、参数结构和业务模型。
- `app/src/main/java/com/example/yuanassist/utils`
  - 配置、日志、模板覆盖、用户脚本持久化、各种共享工具。
- `app/src/main/assets/daily_scripts`
  - 内置日常脚本 JSON。
- `app/src/main/assets/daily_script_templates`
  - 与日常脚本配套的模板图片。
- `app/src/main/assets/ocr`
  - OCR 模型与标签。
- `app/src/main/assets/pi_jing_zhan_ji`
  - 披荆斩棘题库等专用资源。

## 功能结构
- 主壳页面
  - `MainActivity` + `ui/main/*` 负责主导航、状态同步、Debug 页接入。
  - 首页检查更新由 `HomeActionHandler` 处理：发现新版本后优先走 Android `DownloadManager` 应用内下载，下载完成拉起系统安装器；同时保留浏览器下载作为手动入口和兜底。
- 无障碍自动化
  - `YuanAssistService` 是核心服务，负责悬浮窗、服务 action、引擎生命周期。
- 日常脚本系统
  - `AutoTaskEngine` 按 `DailyTaskPlan` 执行 CLICK、MATCH_TEMPLATE、OCR、SET_VAR、BACK 等动作。
  - 首页日常入口包含“哀牢15min”：入口页是 `Ailao15MinFragment`，导入 `assets/script(1).json` 到日常版悬浮窗；脚本用于每 15 分钟刷一次哀牢幻境难度，底部确定 OCR 会最多等待约 60 秒。
  - 现已支持 `SCREENSHOT_GROUP`：
    - 只用于视觉识别候选组，共用一次截图
    - 组内子项当前支持 `ocr`、`template` / `match_template`
    - 组配置入口在 `TaskParams.screenshot_steps`
    - 组级 `roi` 默认共享，子项可单独覆盖 `roi`
    - 子项命中后使用自己的 `on_success` 跳转；全部未命中仍走任务级 `on_fail`
  - 当前脚本退出语义统一为：
    - `on_success = -1` 表示正常完成
    - `on_fail = -1` 也按正常结束处理
    - `on_fail = -2` 表示异常失败
  - 项目已移除旧的 `treatFailMinusOneAsSuccess` 分叉语义；不要再依赖额外布尔参数改变 `-1` 的含义。
- 脚本录制与编辑
  - `DailyScriptRecorderManager` 负责录制。
  - `RecordedDailyScriptViewerActivity` 负责查看、分支切换、编辑、导出。
- 调试工作台
  - `DebugWorkbenchCoordinator` 负责从图片中测试模板/OCR、替换模板、调延时、查看命中范围。
- 角色导入
  - `CharacterImportEngine` 负责截图、OCR、命盘/练度/名称推断。
- 特定业务运行时
  - `BirdFoodRuntimeManager`、`Mainline624RuntimeManager`、`PiJingZhanJiRuntimeManager`、`StargazingRuntimeManager` 等是按具体功能封装的运行时管理器。
  - `PiJingZhanJiRuntimeManager` 的第一模块任务除了 624、赠礼、行囊、家具、材料、观星外，还支持通过总控 JSON 串联鸢报子流程的“鸢报26次”。
  - `PiJingZhanJiRuntimeManager` 的第一模块前置任务链现在对单项脚本失败更宽容：
    - 单个前置任务返回失败时会记录失败项并继续执行后续已勾选任务
    - 第一模块全部结束后，若存在未完成任务，会先弹出 5 秒提示，再进入活动模块或结束

## 两个主要悬浮窗
### 1. 战斗版悬浮窗
- 主要实现位于：
  - `app/src/main/java/com/example/yuanassist/core/YuanAssistService.kt`
  - `app/src/main/java/com/example/yuanassist/ui/FloatingUIManager.kt`
  - 布局：`app/src/main/res/layout/layout_control_window.xml`
- 它服务的是战斗脚本链路，不是日常任务链路。
- 核心上有两种模式：
  - 录制模式
    - 通过全屏输入层采集点击/操作
    - 记录回合、步骤和动作
    - 支持新增回合
    - 支持撤销、清空、编辑表格中的动作
    - 支持导出为脚本
  - 跟打模式
    - 基于录制结果或导入脚本执行战斗动作
    - 支持开始、暂停、继续、停止
    - 会显示当前执行状态
- 战斗版悬浮窗还承载这些辅助功能：
  - 自动选人开关与角色配置
  - 战斗锚点/定位相关调节入口
  - 小窗最小化与恢复
  - 设置入口
  - 表格式回合/指令查看与编辑
  - 录制模式下的额外悬浮按钮，例如“圈”和目标切换按钮，用于快速记录特殊战斗指令
- 简单理解：
  - 战斗版悬浮窗 = 战斗录制器 + 跟打执行器 + 战斗脚本辅助工具面板

### 2. 日常版悬浮窗
- 主要实现位于：
  - `app/src/main/java/com/example/yuanassist/core/DailyWindowManager.kt`
  - 布局：`app/src/main/res/layout/layout_daily_window.xml`
- 它本体很轻，包含拖动入口、动作按钮和关闭按钮，但背后挂载的是整条“日常自动化/工具”链路。
- 这个悬浮窗主要负责：
  - 启动/停止通用 `DailyTaskPlan` 日常脚本
  - 启动/停止鸟食相关运行时
  - 启动/停止主线 6-24 运行时
  - 启动/停止观星运行时
  - 启动/停止披荆斩棘运行时
  - 启动/停止角色导入
  - 启动星石拼图
  - 启动框选OCR模式
  - 进入坐标选点模式
  - 进入脚本录制模式
- 它也是“当前选中的日常任务/配置”的统一执行按钮：
  - 页面先提交 config 或 plan 给 `DailyWindowManager`
  - 悬浮窗再负责开始/停止当前选中的那一项
- 日常版悬浮窗的图标点击会回到对应日常页面，动作按钮负责执行或停止当前工作，关闭按钮负责停止当前工作并移除悬浮窗。
- `DailyWindowManager.hideWindow()` 会停止当前工作、移除悬浮窗并标记关闭；无障碍服务最终销毁时应走 `DailyWindowManager.release()`，同时取消内部 `uiScope`，避免 OCR/统计等异步任务继续持有旧 service。
- “框选OCR”属于日常版悬浮窗挂载的一种轻工具模式：
  - 首页快捷按钮负责把模式导入到 `DailyWindowManager`
  - 悬浮窗开始按钮负责弹出可拖动/缩放的选区
  - 确认后走本地 PaddleOCR，并以悬浮对话框展示和复制识别结果
- 简单理解：
  - 日常版悬浮窗 = 日常任务启动器 + 工具入口 + 当前日常任务状态控制器

## 当前 UI 风格
- 当前项目不是通用 Material 默认风格，而是偏暖色、纸感、轻古风的视觉语言。
- 主色来自 `ui/main/theme/MainShellTheme.kt`：
  - 标题色偏棕红：`TitleInk`
  - 正文色偏暖棕：`BodyInk`
  - 分割/描边偏金棕：`PaperLine`
  - 点缀偏暖粉棕：`WarmRose`
  - 面板底色偏奶油纸张：`GlassPanel` / `ConsolePanel`
- 背景常用浅暖底色或整张背景图，不是纯白极简风。
- 标题大量使用 Serif 风格字体、较重字重、较少字距，强调“古风题签”的感觉。
- 页面标题区常带细线、圆点、描边等装饰性分隔，不是单纯一行标题文字。
- 面板、按钮、导航项大量使用浅底 + 金棕描边 + 轻阴影/轻发光的处理，属于轻拟物，不是纯扁平设计。
- 主界面底部导航是定制视觉，不应随意替换成默认的 Material `NavigationBar` 观感。
- 子页面与主壳风格统一，共享标题、返回按钮、装饰分割线和暖色纸面卡片语言。

## UI 修改准则
- 新页面或改现有页面时，优先复用现有主壳/子页面风格，不要引入突兀的新视觉体系。
- 优先复用：
  - `ui/main/theme/MainShellTheme.kt`
  - `ui/subpage/SubpageScaffold.kt`
  - `ui/subpage/*`
  - `ui/main/components/*`
- 如果做 Compose 页面：
  - 优先沿用当前暖色系、Serif 标题、纸面卡片、金棕描边
  - 避免直接落回默认蓝色系、纯白卡片、通用 Material3 模板观感
- 如果做传统 View 页面：
  - 也应尽量向当前主壳的暖色纸感靠拢，避免出现完全不同的工业风/极简风页面
- Debug 页面虽然偏工具页，但也已经接入主壳视觉体系；新增调试能力时优先延续该风格，而不是单独做一套工具后台风

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

## 调试与开发时优先检查的入口
- 模板命中不准
  - 先看 `DebugWorkbenchCoordinator`
  - 再看 `TemplateOverrideStore`
  - 再看对应脚本 JSON 和 `asset_template_dir`
- OCR 识别不准
  - 先看 `DebugWorkbenchCoordinator`
  - 再看 `AutoTaskEngine` 的 OCR 流程
  - 角色导入相关再看 `CharacterImportEngine`
- 星石本地 OCR / 划分预览
  - 先看 `StonePaddleLocalRecognizer`
  - 当前本地链路已改为：`Paddle 检测框驱动划分 + Paddle 单格识别`
  - `MyStoneActivity` 的“划分”按钮和 Debug 页星石划分测试共用这套结果，并会画出 Paddle 检测框、候选行、横线和四等分列线
  - 星石名字解析使用固定字典兜底：完整命中优先，单字仅在唯一归属时补全，两字错一字仅在唯一可修正时接受；歧义返回空
  - 星石等级解析使用 `级` 作为强制分隔锚点，单独 `级` 补为 `1级`，合法范围限定为 `1级` 到 `60级`
  - 星石拼图完成后只提示前往 `我的星石`，统计入口收口到 `MyStoneActivity`
  - `MyStoneActivity` 里 `结果散图` 继续走本地 OCR，完成后会记录本地完成状态
  - `结果长图` 改为云端 OCR，且必须先完成 `结果散图` 的本地 OCR 才允许执行，否则直接提示先做散图本地识别
- 脚本节点跳转/分支不对
  - 先看 `RecordedDailyScriptViewerActivity`
  - 再看 `DailyTaskPlan` / `DailyTask`
  - 再看 `AutoTaskEngine.finishTask`
  - 排查时注意：
    - `-1` 是结束节点，不再区分“失败的 -1”与“成功的 -1”
    - 需要异常中断时，应显式使用 `-2`
- 悬浮窗/服务没起来
  - 先看 `HomeActionHandler`
  - 再看 `YuanAssistService`
  - 再查权限、无障碍和 pending action
- 战斗悬浮窗行为不对
  - 先看 `YuanAssistService`
  - 再看 `FloatingUIManager`
  - 再看 `layout_control_window.xml`
- 日常悬浮窗行为不对
  - 先看 `DailyWindowManager`
  - 再看对应 runtime manager
  - 再看 `layout_daily_window.xml`

## 可复用模块
- `AutoTaskEngine`
  - 通用日常脚本执行引擎，支持模板匹配、OCR、点击、变量与分支跳转。
- `DebugWorkbenchCoordinator`
  - 通用调试工作台协调器，适合给模板/OCR/延时调优接入口。
- `UserDailyScriptStore`
  - 用户脚本 bundle 的创建、读取、导出、模板文件同步。
- `TemplateOverrideStore`
  - 模板覆盖与恢复机制。
- `DailyScriptDebugIndex`
  - 将脚本 plan 映射成 debug 页可选节点，适合把脚本节点暴露给调试 UI。
- `RunLogActivity` / `RunLogger`
  - 运行日志查看与输出。
  - App 冷启动时会清空运行日志；运行中日志会同步写入 `files/run_logger.log`，关闭后再打开会从当前会话重新开始。
- `SubpageScaffold`
  - 子页面统一骨架，包含标题、返回按钮、间距和装饰风格。

## 资产与脚本组织规则
- 内置脚本主要放在 `app/src/main/assets/daily_scripts`。
- 脚本模板主要放在 `app/src/main/assets/daily_script_templates/<script-name>/`。
- 调试页对脚本节点的展示，依赖脚本内容本身和 `DailyScriptDebugIndex` 的映射。
- 若新增一类日常脚本或模板节点，最好同时考虑：
  - 脚本 JSON 是否能被调试页索引
  - 模板名是否需要在人类可读层做展示映射
  - 是否需要接入模板替换/恢复

## 容易忽略的点
- 很多关键常量直接写在协调器或引擎里，不一定抽到统一配置层。
- `DebugWorkbenchCoordinator` 里维护了大量任务、模板、ROI、阈值和角色特殊点位，是识别规则的重要事实来源。
- 角色导入不仅依赖 OCR，还叠加了名字纠错、命盘匹配、候选打分；不要把它当成简单 OCR 页面。
- `RecordedDailyScriptViewerActivity` 不只是查看器，它也是脚本结构编辑器，支持改起始任务、增删节点、分支查看、导出。
- 模板替换、脚本编辑、延时覆盖三者是分开的持久化层，不要误以为都写在一个地方。

## 修改建议
- 涉及模板匹配、OCR、ROI、点位的改动，先确认所属链路是 `1080x1920` 还是 `1440x2560`。
- 涉及日常脚本节点的改动，优先保持和 `RecordedDailyScriptViewerActivity`、`DailyScriptDebugIndex`、调试页链路一致。
- 涉及模板素材调整，优先保留调试页验证与 override 能力。
- 涉及新识别点时，优先考虑是否需要在 `DebugWorkbenchCoordinator` 增加对应测试入口。
