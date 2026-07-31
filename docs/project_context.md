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
- 想找测试工具当前能力、技术栈和计划：`docs/test_tool_context.md`；其中一键回归入口位于 `tools/yuanassist_test_tool/regression.py`，App 冒烟体检实现位于 `tools/yuanassist_test_tool/app_smoke.py`
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

## 功能结构
- 主壳页面
  - `MainActivity` + `ui/main/*` 负责主导航、状态同步、Debug 页接入。
- 首页 `日常版` 现包含「一键日常」入口：只读取 `assets/daily_scripts/daily/` 下的内置脚本，多选后导入现有日常悬浮窗，开始执行时按列表顺序逐个运行，单项失败不中断，全部结束后统一汇总失败项；运行日志使用 `一键日常` 模块，队列汇总写入 `总流程` 板块，每个小任务按脚本 `display_name` 单独写入自己的板块；页面会记住上次勾选的脚本、`历练` / `观星` 选项以及一键日常专用的“调试模式 / 保存调试截图”开关，退出后再次进入不清空；当前内置项已包含领取体力、领取月卡、行囊派遣、送礼一次、历练、观星、白鹄扫荡、相见、鸢报一轮、密探升级、家具互动、密探特训、家具历险、家具打造、材料打造等；`家具打造` / `材料打造` 开头会先定位当前页面，识别不到时有限次返回后调用 `home_page_one_recover.json` 回到首页第一页；这两个脚本的开局深层定位只允许分别通过 `家具打造` / `材料合成` OCR 跳到对应打造流程，不允许通过加号等后续控件直接跳转；其中 `历练` 支持铜钱/经验/风火/地水/阴阳五入口单选，默认经验；`观星` 支持「观星一次 / 有月卡观星 / 无月卡观星」三种模式，后两者默认 30 次且可改；调试模式开启后，一键日常运行时会按脚本节点在屏幕上绘制 ROI 红框，若同时开启“保存调试截图”则继续沿用 `AutoTaskEngine` 的截图保存链路；`领取月卡` 在原“福利”识别前会先执行一段“月卡补充”流程；若导入时跳去开启无障碍或悬浮窗权限，待导入脚本列表会先持久化，权限补齐后可继续恢复导入。
  - 首页检查更新由 `HomeActionHandler` 处理：发现新版本后优先走 Android `DownloadManager` 应用内下载，下载完成拉起系统安装器；同时保留浏览器下载作为手动入口和兜底。
  - 首页「相关链接」板块位于常用入口之后，以两列卡片展示作者主页、maayuan、biubiu 三个推荐入口；入口图标使用 `assets/author_home.png`、`assets/maayuan.png`、`assets/biubiu.jpg`，点击由 `MainActivity` 打开外链。
- 无障碍自动化
  - `YuanAssistService` 是核心服务，负责悬浮窗、服务 action、引擎生命周期。
- 日常脚本系统
  - `AutoTaskEngine` 按 `DailyTaskPlan` 执行 CLICK、MATCH_TEMPLATE、OCR、SET_VAR、BACK 等动作。
  - 云端脚本共享入口位于首页「常用入口」的「脚本库」后面；只共享日常录制脚本 bundle，战斗脚本仍归 JobStation。脚本整包通过 Supabase Storage 保存为 zip，元数据由 `SupabaseRepository` / `yuanassist-api-v3` 管理；详情页的「图片指引」使用图床 URL，并有独立于攻略评论的云端脚本评论区。管理员设备发布的云端脚本由后端返回 `isAdminPublished`，列表显示“管理员发布”标签；首页「云端脚本」入口红点与消息未读数共用 `get-home-badges` 请求，并按本地已读记录判断，进入列表页后清除。云端脚本详情页只提供“保存本地”，不要绕过本地 bundle 存储直接导入日常悬浮窗，否则运行时可能缺少模板素材。
  - 现已支持 `SCREENSHOT_GROUP`：
    - 只用于视觉识别候选组，共用一次截图
    - 组内子项当前支持 `ocr`、`template` / `match_template`
    - 组配置入口在 `TaskParams.screenshot_steps`
    - 组级 `roi` 默认共享，子项可单独覆盖 `roi`
    - 子项命中后默认使用自己的 `on_success` 跳转；如果子项 `on_success` 与任务级 `on_success` 相同，则会复用任务级 `branch_var` / `branch_routes` 分支
    - 全部未命中仍走任务级 `on_fail`
  - 当前脚本退出语义统一为：
    - `on_success = -1` 表示正常完成
    - `on_fail = -1` 也按正常结束处理
    - `on_fail = -2` 表示异常失败
  - 项目已移除旧的 `treatFailMinusOneAsSuccess` 分叉语义；不要再依赖额外布尔参数改变 `-1` 的含义。
- 脚本录制与编辑
  - `DailyScriptRecorderManager` 负责录制。
  - 录制器支持滑动节点：第一次点屏幕作为起点，节点弹窗里的「获取结束坐标」用于再次点屏幕采集终点，保存为 `SWIPE` 的 `startX/startY/endX/endY/duration/align`。
  - 录制点若落在居中游戏区域外，会自动推荐 `top` 或 `bottom` 位置类型；滑动节点切换位置类型时会用原始屏幕点重算起点和已采集终点。
  - `RecordedDailyScriptViewerActivity` 负责查看、分支切换、编辑、导出。
- 作业站/攻略发布
  - `UploadStrategyActivity` 负责发布和编辑本站攻略，基础信息包含游戏版本（`ruyuan`：1=如鸢、0=代号鸢）和空格分隔的自定义标签 `tags`；`JobStationAssetRepository` 展示标签时会合并游戏标签、自定义标签和标题推断标签。
  - 发布攻略在“选择密探”模式下若没有上传攻略原图，会复用 `ImageExportUtils` 的录制模式导出图生成表格封面，上传图床后只写入 `coverUrl`，不写入 `strategyImage`。
- 调试工作台
  - `DebugWorkbenchCoordinator` 负责从图片中测试模板/OCR、替换模板、调延时、查看命中范围。
  - 调试页会自动索引 `assets/daily_scripts` 中的脚本视觉节点，包括 `MATCH_TEMPLATE`、`OCR` 以及 `SCREENSHOT_GROUP` 子步骤。
  - 调试页对内置脚本的索引现支持递归读取 `assets/daily_scripts` 下的 `.json`，因此 `daily/*.json` 这类一键日常脚本也会进入任务列表并支持模板/OCR 调试与替换。
  - OCR 节点即使 JSON 未配置 `template_name`，调试页也会生成稳定派生模板名：`<scriptBaseName>_task_<taskId>_ocr.png`，保存位置是 App 私有 `files/template_overrides/`。
  - 运行时 OCR 节点优先查对应 override 模板；存在则走模板匹配，不存在则回落原 OCR。
  - `SCREENSHOT_GROUP` 里的 OCR 子步骤也遵循同一套 override 语义；无 `template_name` 时使用 `<scriptBaseName>_task_<taskId>_step_<index>_ocr.png`。
  - 脚本 JSON 可配置脚本级 `display_name` 作为调试页/用户可见名称；节点级 `name` 用于调试选项和运行日志中的任务标识。
  - 调试页延时增量由 `TemplateDelayOverrideStore` 持久化，运行时会在普通日常脚本开始前和 `RUN_SCRIPT_SEGMENT` 子脚本加载时应用；支持 `MATCH_TEMPLATE`、`OCR`、`SCREENSHOT_GROUP` 子步骤，脚本 key 会兼容 `.json`/无后缀以及 `user:`/裸脚本 id。
- 角色导入
  - `CharacterImportEngine` 负责截图、OCR、命盘/练度/名称推断。
- 特定业务运行时
  - `BirdFoodRuntimeManager`、`Mainline624RuntimeManager`、`StargazingRuntimeManager` 等是按具体功能封装的运行时管理器。
  - `BirdFoodRuntimeManager` 现在是薄调度层：鸟食流程主体由 `assets/daily_scripts/bird_food_controller.json` 串联 `bird_food_ensure_yuan_bao.json` 和具体鸟食子脚本，manager 只负责配置变量、停止条件、启停和最终提示。
  - 刷鸟食的小道消息子脚本使用 `bird_food_xiao_dao_xiao_xi.json`；一键日常“鸢报一轮”使用旧 `xiao_dao_xiao_xi.json`，保留其中“最多/前往收集”的一次性收取逻辑，二者不要混用。
  - `Mainline624RuntimeManager` 已收敛为薄调度层：主体仍执行 `zhu_xian_6_24.json`，manager 只负责次数停止、`game_variant` 变量和开始战斗延时覆盖；首次入口和后续循环都从脚本头部定位组开始，不再维护单独循环入口。
  - `zhu_xian_6_24.json` 开头用 `SCREENSHOT_GROUP` 判断当前界面：可直接识别 6-24 战斗页、6-24 入口、第六章入口、首页故事入口；多次未命中会先返回重试，再调用 `home_page_one_recover.json` 回到首页后从故事入口流程继续。
  - 披荆斩棘功能已从主项目移除并备份到公开仓库：`https://github.com/chunshengyue/yuanassist-pi-jing-zhan-ji`。主项目保留部分共享脚本及其依赖素材，避免影响鸟食、首页恢复、观星等现有链路。

## Supabase 维护指南
- 当前 Supabase 项目：
  - Project URL：`https://ftryfykwzsadgiayquvz.supabase.co`
  - Project ref：`ftryfykwzsadgiayquvz`
  - 本地 CLI：`.\node_modules\@supabase\cli-windows-x64\bin\supabase.exe`
  - 维护脚本说明：`SUPABASE_DATA_MAINTENANCE.md`
- 处理 Supabase 任务前：
  - 先查看当前 CLI 能力，不要凭记忆猜命令：`supabase --help`、`supabase db --help`、`supabase db query --help`
  - 涉及新表、Storage、RLS、Data API 暴露时，先看 Supabase changelog/docs；Supabase 近期有“新表不一定自动暴露到 Data API”的 breaking change。
  - 优先用 `supabase db query --linked -f <sql-file>` 执行远端 SQL；复杂 SQL 放临时文件，避免 PowerShell 引号转义出错。
  - 执行后必须查回验证；临时 SQL 文件完成后删除。
- 新增表流程：
  - 与用户确认表名、字段、主键、唯一约束、外键、索引、默认值、是否要客户端访问。
  - 在 `public` schema 新建表时默认执行 `alter table ... enable row level security;`。
  - 不清楚访问策略时，不要创建开放 policy，也不要随手 `grant` 给 `anon` / `authenticated`。
  - 如果用户需要 App 直接读写，再明确 Data API 暴露、`GRANT`、RLS policy 三件事；RLS 控制行可见性，`GRANT`/Data API 暴露控制表是否能被 API 访问。
  - 建表、建索引用 `if not exists`，Storage bucket 用 `insert ... on conflict`，方便重复执行。
  - 验证至少查：`pg_class.relrowsecurity`、`pg_indexes`、必要的外键/唯一约束。
- Storage bucket 流程：
  - 可通过 SQL 写入 `storage.buckets` 创建 bucket，例如 `insert into storage.buckets (id, name, public) values (...)`。
  - 未明确要求公开时，bucket 默认 `public = false`。
  - 不要把 service role / secret key 写进客户端或文档正文。
  - 如果要允许客户端上传或覆盖文件，确认并创建 Storage policy；upsert 需要 INSERT、SELECT、UPDATE 权限配套。
- 更新版本信息：
  - 版本更新表是 `public."update"`，常用字段是 `"versionCode"`、`"versionName"`、`"apkUrl"`、`"releaseNotes"`。
  - 可直接用 CLI SQL 更新，也可用 `tools/update_supabase_update_record.mjs`；该脚本需要环境变量 `SUPABASE_SECRET_KEY`。
  - `versionCode` 要与 App `app/build.gradle.kts` 里的 `versionCode` 保持一致；客户端实际比较的是 `versionCode`，不是 `versionName`。
  - 更新后查回 `public."update"` 确认版本号、下载地址、更新说明换行都正确。
- 更新公告/公告类数据：
  - 先确认目标表名、主键/唯一键和字段，不要假设“公告”一定是某张表。
  - 公告类更新优先使用 SQL 的 `insert ... on conflict ... do update` 或明确 `where` 的 `update`，避免误改多行。
  - 更新后按业务关键字段查回确认，并把返回结果摘要给用户。
- 当前云端日常脚本相关表/桶：
  - `public.cloud_daily_scripts` 保存日常脚本 bundle 元数据，已启用 RLS。
  - `public.cloud_daily_script_comment` 保存云端脚本评论，`public.cloud_daily_script_message` 保存云端脚本评论/回复消息，二者独立于攻略的 `strategy_comment` / `strategy_message`。
  - `daily-script-bundles` 是私有 Storage bucket，用于保存脚本 zip bundle。
  - 这套云端脚本只服务“日常录制脚本共享”，战斗脚本仍归 JobStation。
  - 官方覆盖脚本使用 `public.cloud_daily_scripts.override_asset_script text null` 标记目标内置脚本；字段为空表示普通云端录制脚本，非空表示该 bundle 是官方覆盖脚本，字段值必须是 `assets/daily_scripts` 下的内置脚本文件名，例如 `zhu_xian_6_24.json`。
  - 官方覆盖脚本由维护者本地改好 JSON 后，通过 Supabase CLI/SQL 直接新增或更新 `cloud_daily_scripts` 行并上传 zip 到 `daily-script-bundles`；不要走 App 内“脚本库上传”入口。客户端发布的普通脚本不得写入 `override_asset_script`，也不提供覆盖能力。
  - 官方覆盖脚本 zip 可以只包含 `script.json`，不包含模板素材；运行时模板仍复用脚本内 `asset_template_dir` 指向的内置素材。客户端保存时应与普通用户脚本分流，保存到专门的覆盖脚本目录，运行特定内置任务时优先读取本地覆盖 JSON；用户删除覆盖脚本后自然回退到原 `assets/daily_scripts` 内置脚本。

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
  - “键位修正”入口会显示 A、↑、↓、圈 四个动作标记，分别落在 1-4 号位中间；拖动标记只保存对应动作的 y（距离底部距离），x 仍由列位算法计算
  - 小窗最小化与恢复
  - 设置入口
  - 表格式回合/指令查看与编辑
  - 录制模式下的额外悬浮按钮，例如“圈”和目标切换按钮，用于快速记录特殊战斗指令
  - 战斗设置页的“高级参数”将录制模拟和跟打执行拆成两套手势参数：点击持续时间、滑动持续时间、滑动距离；A/↑/↓/圈 的距离底部仍沿用原有“战斗动作距离底部”配置。
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
- “屏幕选点”属于日常版悬浮窗的持续模式之一：每次点击悬浮窗开始按钮会重新打开一次选点遮罩，点屏复制坐标后只关闭当次遮罩，不退出选点模式，便于连续多次取点。
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
- 所有弹窗都必须使用项目现有暖色纸感风格，不要直接使用默认黑色、纯白或系统原生观感弹窗：
  - Compose 弹窗优先复用 `SubpageConfirmDialog`、`SubpageInputDialog` 或参考它们的 `GlassPanel`、Serif 标题、圆角样式
  - 传统 View / 平台 `AlertDialog` 优先复用 `DialogUtils.getThemeContext` 和 `DialogUtils.styleAlertDialog`
  - 悬浮窗/无障碍服务里的平台弹窗优先用 `DialogUtils.safeShowOverlayDialog`
- 传统平台弹窗或下拉选项不要直接使用默认 `setItems` / 系统默认 `ArrayAdapter`：
  - 优先复用 `DialogUtils.getThemeContext`
  - 列表弹窗用 `DialogUtils.fixedOptionTextAdapter`
  - Spinner / AutoComplete 下拉用 `DialogUtils.fixedDropdownTextAdapter`
  - 这样可以避免部分系统主题下选项文字与背景同色而“能点但看不见”
- Debug 页面虽然偏工具页，但也已经接入主壳视觉体系；新增调试能力时优先延续该风格，而不是单独做一套工具后台风

## 可复用 UI 组件
### Compose 主壳组件
- `ui/main/theme/MainShellTheme.kt`
  - 主壳与子页面共用主题入口，定义 `TitleInk`、`BodyInk`、`PaperLine`、`WarmRose`、`GlassPanel`、`ConsolePanel` 等暖色纸感色板。
  - 新 Compose 页面不要自行起一套蓝色/白色 Material 默认主题，优先使用这些颜色常量。
- `ui/main/components/GufengFeatureCard.kt`
  - `GufengFeatureCard`：大号古风功能卡/空状态卡，适合首页功能展示、子页空状态或强调入口。
  - `GufengCardIcon` 当前只有 `Ornament`，使用 `R.drawable.ornament_1`。
- `ui/main/components/GufengDecorActionButton.kt`
  - `GufengDecorActionButton`：带头像位、描边、装饰图的胶囊按钮，适合主界面或强视觉入口按钮。
  - 可通过 `itemRes`、`decorRes`、`showOuterSurface`、`showDecor`、`showPortraitPlate` 和尺寸参数适配不同入口。

### Compose 子页面组件
- `ui/subpage/SubpageThemeBridge.kt`
  - `SubpageThemeBridge`：给子页套 `MainShellTheme` 和 `background_stretch_9x21` 背景图；普通子页通常不直接用它，而是通过 `SubpageScaffold` 间接使用。
- `ui/subpage/SubpageScaffold.kt`
  - `SubpageScaffold`：子页面统一骨架，包含背景、状态栏内边距、标题区、返回按钮、可选 action 区、滚动开关和最大宽度约束。
  - `SubpageTopBar`：可单独复用的子页标题栏。
  - `SubpageShapes`：统一圆角形状，包含 `roundBadge`、`section`。
- `ui/subpage/SubpageCards.kt`
  - `SubpageSectionCard`：纸面金边内容卡，适合分组承载表单、说明、列表。
  - `SubpagePaperPanel`：轻量内层纸面面板，适合卡片内的弱分组。
  - `SubpageInfoStrip`：左右 label/value 信息条。
  - `SubpageBadge`：暖色小标签。
- `ui/subpage/SubpageListItems.kt`
  - `SubpageActionRow`：可点击设置/入口行，右侧默认显示“进入”。
  - `SubpageToggleRow`：带方形勾选指示的开关行。
  - `SubpageChipRow`：横向等宽单选 chip 组。
- `ui/subpage/SubpageOptionControls.kt`
  - `SubpageRadioOption` / `SubpageCheckOption`：暖色单选/多选项。
  - `SubpageCircleIndicator` / `SubpageSquareIndicator`：可独立复用的选中状态指示器。
- `ui/subpage/SubpageFormFields.kt`
  - `SubpageFieldGroup`：表单字段分组标题与说明。
  - `SubpageTextField`：已套暖色描边、纸面底色和 Serif label 的输入框。
- `ui/subpage/SubpageDialogs.kt`
  - `SubpageConfirmDialog`：Compose 确认弹窗，使用项目暖色纸感样式，避免默认黑色/纯白弹窗观感。
  - `SubpageInputDialog`：Compose 输入弹窗，内部复用 `SubpageTextField`。
- `ui/subpage/SubpageStates.kt`
  - `SubpageEmptyState`：空状态，内部复用 `GufengFeatureCard`，可附带 `StoneStyleButton` 动作。
  - `SubpageLoadingState`：暖色加载状态。
  - `SubpageErrorText`：子页错误文本。
- `ui/subpage/StoneStyleButton.kt`
  - `StoneStyleButton`：通用暖色石纹按钮，适合子页主要操作。
  - `StoneStyleChoiceButton`：单选按钮包装，内部走 `SubpageRadioOption`。

### 业务可复用 UI
- `ui/AgentSelectionComponents.kt`
  - `SharedAgentPickerDialog`：复用密探选择弹窗。
  - `SharedTalentPickerDialog`：复用天赋选择弹窗。
  - `AgentAvatar`：密探头像显示。
  - `TalentValueChip`、`buildTalentPreview`、`resolveTalentLabel`、`resolveTalentIdByLabel`、`buildSelectableAgentList`：密探天赋展示、解析和候选列表工具。
- `utils/DialogUtils.kt`
  - 传统 View / 平台 `AlertDialog` 必须优先用 `DialogUtils.getThemeContext` 创建主题 context。
  - 在悬浮窗或无障碍服务中展示平台弹窗时，用 `DialogUtils.safeShowOverlayDialog`，它会设置 overlay window type 并统一样式。
  - 平台弹窗必须经过 `DialogUtils.styleAlertDialog` 或同等项目样式处理，不要保留系统默认黑色/纯白弹窗。
  - 列表弹窗用 `fixedOptionTextAdapter`，Spinner / AutoComplete 下拉用 `fixedDropdownTextAdapter`，避免系统主题导致文字不可见。

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
- 线上 OCR 接口
  - 表格云端 OCR 与星石云端 OCR 共用 `OcrManager` / `OcrRouteManager`。
  - 当前线上 OCR 地址固定为 `https://ocr.yuanassist.space/release/ocr`，不再通过 Supabase `ocr_route` 切换，也不再保留腾讯云函数兜底地址。
  - 表格云端 OCR 使用 `force_mode = 0`、`response_mode = parsed`；星石云端 OCR 使用 `force_mode = 2`、`response_mode = raw`。
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
  - 若是一键日常队列执行问题，也先看 `DailyWindowManager` 的队列调度与失败汇总逻辑

## 可复用模块
- `AutoTaskEngine`
  - 通用日常脚本执行引擎，支持模板匹配、OCR、点击、变量与分支跳转。
  - `RUN_SCRIPT_SEGMENT` 加载子脚本时支持 `segmentPlanCustomizer` 钩子；刷鸟食用它把待办公务入口跳过、五铢钱选择区域和开始战斗延时应用到实际子脚本。
- `DebugWorkbenchCoordinator`
  - 通用调试工作台协调器，适合给模板/OCR/延时调优接入口。
  - 脚本 OCR 节点支持从自身 ROI 裁剪生成 App 私有模板，不修改 assets JSON 或 assets 模板文件。
- `TemplateDelayOverrideStore`
  - 调试页延时增量的统一持久化与运行时 plan 覆盖入口；普通日常悬浮窗在启动脚本前应用，`AutoTaskEngine` 在加载 `RUN_SCRIPT_SEGMENT` 子脚本后应用。
  - `SCREENSHOT_GROUP` 多个子步骤共享任务级 delay；若同组多个子项配置增量，运行时取最大视觉节点增量加到该组任务 delay。
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
- `DialogUtils`
  - 传统 View 弹窗、下拉和悬浮窗 overlay 弹窗的统一主题与安全展示工具。

## 资产与脚本组织规则
- 内置脚本主要放在 `app/src/main/assets/daily_scripts`。
- 「一键日常」内置脚本放在 `app/src/main/assets/daily_scripts/daily/*.json`。
- 脚本模板主要放在 `app/src/main/assets/daily_script_templates/<script-name>/`。
- 「一键日常」模板统一放在 `app/src/main/assets/daily_script_templates/daily/`，若不同任务存在同名模板，统一改成带任务名前缀的文件名，并同步更新 `template_name`。
- 当前内置脚本实际引用的模板素材已优先整理到 `app/src/main/assets/pics/<display_name>/`。
- 当 `template_name` 写成 `pics/...` 这类带斜杠路径时，运行时会按 `assets` 相对路径直接取图，不再依赖脚本里的 `asset_template_dir`。
- 调试页对脚本节点的展示，依赖脚本内容本身和 `DailyScriptDebugIndex` 的映射。
- 若新增一类日常脚本或模板节点，最好同时考虑：
  - 脚本 JSON 是否能被调试页索引
  - 脚本级 `display_name` 和节点级 `name` 是否足够让用户定位
  - 模板名是否需要在人类可读层做展示映射
  - 是否需要接入模板替换/恢复

## 容易忽略的点
- 很多关键常量直接写在协调器或引擎里，不一定抽到统一配置层。
- `DebugWorkbenchCoordinator` 里维护了大量任务、模板、ROI、阈值和角色特殊点位，是识别规则的重要事实来源。
- 角色导入不仅依赖 OCR，还叠加了名字纠错、命盘匹配、候选打分；不要把它当成简单 OCR 页面。
- `RecordedDailyScriptViewerActivity` 不只是查看器，它也是脚本结构编辑器，支持改起始任务、增删节点、分支查看、导出。
- 模板替换、脚本编辑、延时覆盖三者是分开的持久化层；延时覆盖保存到偏好设置，运行时通过 plan 覆盖应用，不会改写脚本 JSON。

## 修改建议
- 涉及模板匹配、OCR、ROI、点位的改动，先确认所属链路是 `1080x1920` 还是 `1440x2560`。
- 涉及日常脚本节点的改动，优先保持和 `RecordedDailyScriptViewerActivity`、`DailyScriptDebugIndex`、调试页链路一致。
- 涉及模板素材调整，优先保留调试页验证与 override 能力。
- 涉及新识别点时，优先考虑是否需要在 `DebugWorkbenchCoordinator` 增加对应测试入口。
