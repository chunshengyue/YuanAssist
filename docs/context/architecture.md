# 架构与模块地图

> 项目整体结构、目录职责、可复用模块与资产组织规则。改动频率低。
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [modules](modules.md)

---

## 项目概览
- 这是一个 Android 单模块应用，主入口是 `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`。
- App 既有常规页面，也有基于无障碍服务的自动化执行能力。
- 自动化能力主要服务于游戏场景，核心运行时服务是 `app/src/main/java/com/example/yuanassist/core/YuanAssistService.kt`。
- 主界面是 Compose 壳，包含 Home、Job、Debug、Mine 四个 Tab；其中 Debug 是调试工作台入口。
- 简历识别已从主 App 移除；核心代码和原运行时接入段落归档在 GitHub 私有仓库 `chunshengyue/YuanAssist-HrResume-Core`，用于未来按需恢复。

## 构建与运行

单模块应用，模块目录 `app/`。构建配置在 `app/build.gradle.kts`，版本集中在 `gradle/libs.versions.toml`。

- AGP `8.13.1`，Kotlin `2.0.21`（Compose 插件随 Kotlin 版本）
- `namespace` / `applicationId`：`com.example.yuanassist`
- `compileSdk 36`、`minSdk 24`、`targetSdk 36`，JVM target `11`
- ABI 只有 `arm64-v8a`，不支持 x86 / x86_64
- Maven 仓库优先走阿里云镜像，另含 JitPack（Bmob 遗留 SDK）
- 版本号：`versionCode = 52`、`versionName = "1.1.6.41"`

常用命令（PowerShell，仓库根目录）：

```powershell
.\gradlew.bat compileDebugKotlin   # 只做编译检查，验证首选
.\gradlew.bat assembleDebug        # 构建 debug APK
.\gradlew.bat assembleRelease      # 构建 release APK
.\gradlew.bat test                 # 单元测试
.\gradlew.bat connectedAndroidTest # 仪器化测试
.\gradlew.bat clean                # 清理
```

- 源码集：`main` 164 个 kt、`test` 13 个、`androidTest` 1 个、`legacy` 为空
- 按项目规则，未经要求不要主动跑构建或测试；用户要求验证时优先 `compileDebugKotlin`

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
  - 当前离线 OCR 使用 `PP-OCRv6_small` 的 ONNX 检测/识别模型与标签；运行时由 Android ONNX Runtime 加载。

## 可复用模块
- `AutoTaskEngine`
  - 通用日常脚本执行引擎，支持模板匹配、OCR、点击、变量与分支跳转。
  - `RUN_SCRIPT_SEGMENT` 加载子脚本时支持 `segmentPlanCustomizer` 钩子；刷鸟食用它把待办公务入口跳过、五铢钱选择区域和开始战斗延时应用到实际子脚本。待办公务入口跳过同时要改写初始 `SCREENSHOT_GROUP` 候选子项的 `on_success`，因为子项跳转优先于任务级跳转。
- `TemplateMatcher`
  - OpenCV 模板匹配共享 helper，当前由 `AutoTaskEngine` 复用；新增模板匹配逻辑时优先复用它，不要另写像素差异判断。
- `PaddleTextRecognizer`
- `OcrPreprocessor`
  - OCR 预处理共享 helper，保留 `AutoTaskEngine` 既有 `yellow_text` / `light_text`，新增的通用预处理优先放这里。
- `DebugWorkbenchCoordinator`
  - 通用调试工作台协调器，适合给模板/OCR/延时调优接入口。
  - 脚本 OCR 节点支持从自身 ROI 裁剪生成 App 私有模板，不修改 assets JSON 或 assets 模板文件。
- `TemplateDelayOverrideStore`
  - 调试页延时增量的统一持久化与运行时 plan 覆盖入口；普通日常悬浮窗在启动脚本前应用，`AutoTaskEngine` 在加载 `RUN_SCRIPT_SEGMENT` 子脚本后应用。
  - `SCREENSHOT_GROUP` 多个子步骤共享任务级 delay；若同组多个子项配置增量，运行时取最大视觉节点增量加到该组任务 delay。
- `DailyGlobalDelayStore`
  - 日常版全局延时的持久化入口，运行时由 `AutoTaskEngine.startPlan()` 统一应用到每个节点现有 `delay` 上。
- `UserDailyScriptStore`
  - 用户脚本 bundle 的创建、读取、导出、模板文件同步。
- `TemplateOverrideStore`
  - 模板覆盖与恢复机制。
- `DailyScriptDebugIndex`
  - 将脚本 plan 映射成 debug 页可选节点，适合把脚本节点暴露给调试 UI。
- `RunLogActivity` / `RunLogger`
  - 运行日志查看与输出。
  - App 冷启动时会清空运行日志；运行中日志会同步写入 `files/run_logger.log`，关闭后再打开会从当前会话重新开始。
  - 运行日志优先使用结构化模块格式：`RunLogger.i(module = "模块名", section = "小节名", message = "短结果")`；OCR 默认只写节点名、成功/失败、原文/命中，模板匹配默认只写节点名、成功/失败、模板名和分数，过程细节放到诊断日志；运行日志不要输出 ROI/区域坐标，JSON 和调试页已能查看。
  - 写入运行日志的单条消息应保持单行；OCR 原文等多行内容统一把 `\r\n` / `\r` / `\n` 显示为 `\n`，避免展示页分段时把原文截到其他日志。
- `SubpageScaffold`
  - 子页面统一骨架，包含标题、返回按钮、间距和装饰风格。
- `ui/subpage/*`
  - 子页面 Compose 组件库，包含卡片、列表行、选项、表单、弹窗、空/加载/错误状态和按钮。
- `GufengFeatureCard` / `GufengDecorActionButton`
  - 主壳古风功能卡和装饰按钮，适合首页或强视觉入口。
- `AgentSelectionComponents`
  - 密探/天赋选择相关的业务 UI 与标签解析工具。
  - 勾选「代号鸢」时展示的扩展密探名单在 `AgentSelectionComponents.kt` 与 `ExcludedAgentsDialog.kt` 各维护一份；`吕布`、`曹丕` 现在属于基础名单前置项，不再算代号鸢限定，但两处名单构造仍要同步。
  - `AgentRepository`
  - 角色基础资料与命盘映射表；`赵云`、`司马孚`、`张松`、`孙辅`、`周泰`、`陈琳` 已按 `operators.json` 补入，头像直接读取 `assets/<密探名>.png`；周泰、陈琳头像资源统一为 `60x60` PNG。代号鸢模式下，周泰、陈琳固定排在扩展密探名单最前；郭女王、庞德属于如鸢基础前置密探并固定排在如鸢模式最前，`AgentSelectionComponents.kt` 与 `ExcludedAgentsDialog.kt` 的两份名单必须保持同步。
- `DialogUtils`
  - 传统 View 弹窗、下拉和悬浮窗 overlay 弹窗的统一主题与安全展示工具。

## 资产与脚本组织规则
- 内置脚本主要放在 `app/src/main/assets/daily_scripts`。
- 「一键日常」内置脚本放在 `app/src/main/assets/daily_scripts/daily/*.json`。
- 脚本模板主要放在 `app/src/main/assets/daily_script_templates/<script-name>/`。
- 「一键日常」模板统一放在 `app/src/main/assets/daily_script_templates/daily/`，若不同任务存在同名模板，统一改成带任务名前缀的文件名，并同步更新 `template_name`。
- 当前内置脚本实际引用的模板素材已优先整理到 `app/src/main/assets/pics/<display_name>/`。
- 战斗版进图导航、主页恢复和战斗结果恢复素材统一放在 `app/src/main/assets/pics/战斗版导航/`；`BattleStageNavigationRegistry` 与 `CombatEngine` 使用带目录的 asset key，运行时仍兼容旧版按裸文件名保存的覆盖素材和延时配置；洞窟入口不再使用 `dongku.png` / `dongku2.png` 素材识别，直接点击 `center(666,313)` 对应的屏幕位置。
  - 泰山府关卡自动导航复用地宫恢复；进入地宫后在 `top(424,329)` 的局部 OCR 中命中 `泰` / `山` / `府` 任意两个字即点击入口。关卡自动导航编辑器须填写“进入泰山府后顶部第一个关卡”和“泰山府目标关卡”（均为 1-13，且目标不得小于顶部）；运行时从当前截图的首个永昼节点按 `目标 - 顶部` 的零基索引定位，因此顶部 8、目标 10 会选择第 3 个识别节点。点击目标关卡后等待，再直接通过 OCR 识别并点击「开始战斗」，不匹配或点击「进入挑战」模板。翻页上滑距离固定为屏高约 20%，翻页动作完成后固定停顿 1500ms 再截图识别，以保留上一页底部节点。翻页后必须以“上一页最底部永昼节点的相近 X 坐标”作为重叠锚点，找不到锚点即停止，避免重复或漏计。永昼标签左侧约 100px 是关卡点击位，只有第 9、10 关取右侧约 100px；所有 X 偏移按当前宽度相对 1080 换算。旧的单目标关卡配置按“顶部第 1 关”兼容解析。
- 当 `template_name` 写成 `pics/...` 这类带斜杠路径时，运行时会按 `assets` 相对路径直接取图，不再依赖脚本里的 `asset_template_dir`。
- 同一识别点若需要同时兼容简体/繁体模板，优先在现有 `SCREENSHOT_GROUP` 中新增同 ROI、同阈值、同跳转的繁体候选；原本单个 `MATCH_TEMPLATE` 若要兼容两套模板，可改为只包含简繁两个候选的 `SCREENSHOT_GROUP`。
- 内置脚本 OCR 的 `target_chars` 应按字符集合维护简繁兼容；同一含义的简体/繁体字放在同一个数组里，并保持 `min_hit_count` 语义不变，不要为了加繁体去改 ROI、跳转或点击参数。
- 繁体模式的脚本差异优先声明在节点级 `mode_overrides.traditional`；基础节点继续保留简体模式行为，繁体 override 只描述运行时需要替换的 `action` / `params`，常见用法是把带简体文字的 `MATCH_TEMPLATE` 替换成同 ROI 的 `OCR`。
- 我的页「全局设置」提供全局「繁体模式」开关，状态持久化在 `app_prefs`；`AutoTaskEngine.startPlan()` 会在每次父脚本或 `RUN_SCRIPT_SEGMENT` 子脚本启动时读取该状态，开启后将节点的 `mode_overrides.traditional` 中声明的 `action` / `params` 应用到执行 plan，节点 id、名称、延时和跳转保持原值。
- 我的页「全局设置」提供日常版全局延时，状态由 `DailyGlobalDelayStore` 持久化在 `app_prefs`；`AutoTaskEngine.startPlan()` 会在繁体模式节点替换后，把该值叠加到每个节点现有 `delay` 上，因此会与刷鸟食、刷 6-24 等页面自己的低配适应延时继续叠加，但不影响战斗版 `SettingsActivity` / `CombatEngine`。
- 调试页对脚本节点的展示，依赖脚本内容本身和 `DailyScriptDebugIndex` 的映射。
- 若新增一类日常脚本或模板节点，最好同时考虑：
  - 脚本 JSON 是否能被调试页索引
  - 脚本级 `display_name` 和节点级 `name` 是否足够让用户定位
  - 模板名是否需要在人类可读层做展示映射
  - 是否需要接入模板替换/恢复
