# YuanAssist Test Tool Context

## 文档目标
- 记录 YuanAssist 测试工具当前能测什么、使用什么技术栈、后续计划测什么。
- 这份文档随测试工具开发实时维护，重点保留当前有效信息，不记录一次性调试过程。

## 当前方向
- 测试工具整体优先使用 Python 编排。
- 运行环境优先面向 Windows 上的 Android 模拟器。
- 自动化路线按风险从低到高推进：
  1. 静态脚本/数据回归
  2. App 页面与导入链路自动化
  3. 战斗悬浮窗自动化
  4. 游戏画面识别与性能测试

## 技术栈草案
- Python：测试工具主控、用例编排、报告生成。
- pytest：测试组织、断言和分层执行。
- adb：安装 APK、启动 App、截图、输入、日志、设备状态检查。
- Android CLI / Gradle：构建 APK、安装包信息检查、必要时执行编译检查。
- Appium / UiAutomator2：App 页面、系统权限页、常规控件自动化。
- OpenCV / Pillow / numpy：截图、模板匹配、画面回归、局部识别。
- httpx：接口契约测试。
- pydantic / PyYAML：测试配置、脚本结构、接口响应校验。
- pytest-html 或 Allure：测试报告。

## 当前可测试范围
已新增 Python 测试工具雏形：`tools/yuanassist_test_tool/`。

当前支持离线静态检查：
- 战斗 `scriptContent` 回合表结构检查。
- 回合号、重复回合、空回合检查。
- 动作类型白名单检查：`A`、`↑`、`↓`、`圈`。
- 支持单独动作格，例如 `圈`。
- 支持同一步连续动作符号，例如 `4↓↓↓`。
- 动作序号基础检查。
- 附加指令 `InstructionJson` 数组结构检查。
- 指令类型白名单检查。
- 指令 `turn` / `step` / `value` 基础合法性检查。
- `STAGE_AUTO_NAV` 关卡 value 检查。

当前支持内置日常脚本资产体检：
- 读取 `app/src/main/assets/daily_scripts/*.json`。
- 检查 JSON 是否可解析、顶层结构是否是日常脚本计划。
- 检查 `start_task_id`、重复任务 ID、不可达任务。
- 仅检查会暴露到调试页的模板匹配项名称：`MATCH_TEMPLATE` 任务的 `name`，以及 `SCREENSHOT_GROUP` 中 `template` / `match_template` 子步骤的 `name`（无子步骤 name 时继承任务 name）。
- 检查这些模板调试项名称缺失；名称重复时仅在同名但 `template_name` 不同的情况下提醒，同名且指向同一模板不算问题。
- 检查 `on_success`、`on_fail`、`branch_routes`、`fail_branch_routes` 和 `SCREENSHOT_GROUP` 子步骤跳转是否指向存在任务或合法特殊退出值。
- 检查 `CLICK`、`SWIPE`、`MATCH_TEMPLATE`、`OCR`、`SCREENSHOT_GROUP`、`RUN_SCRIPT_SEGMENT`、`SET_VAR` 的关键参数是否齐全。
- 按运行时逻辑检查模板引用：有 `asset_template_dir` 时走对应目录，无 `asset_template_dir` 时走 assets 根目录，模板名带路径时按完整 asset 路径处理。
- 检查 `RUN_SCRIPT_SEGMENT` 引用的子脚本是否存在。
- 检查未被任何脚本引用的日常模板目录。

当前支持日常脚本离线视觉回归雏形：
- 命令：`python -m tools.yuanassist_test_tool daily-vision`
- 默认读取 `tools/yuanassist_test_tool/fixtures/daily_vision/**/case.json`。
- 每个 case 至少包含截图 `screenshot.png`、脚本名 `script` 和期望列表 `expectations`。
- 模板匹配支持 `MATCH_TEMPLATE`，以及 `SCREENSHOT_GROUP` 中的 `template` / `match_template` 子步骤。
- 视觉节点整理支持两类去重单位：模板按 `素材路径 + ROI`，OCR 按 `目标文字 + ROI`；同一单位下重复出现的脚本节点会合并展示。
- 期望类型支持：
  - `hit` / `match`：该节点应命中，分数需达到 `min_score` 或脚本阈值。
  - `miss` / `not_match` / `no_match`：该节点不应命中，分数需低于 `max_score` 或脚本阈值。
  - `ocr`：先保存 OCR 节点与期望文本，当前没有接入离线 OCR 后端时报告 `SKIP_OCR_BACKEND`。
- ROI 与模板缩放尽量复刻 `AutoTaskEngine` 的 `1080x1920` 日常视觉基准、`align` 和截图/显示尺寸映射；case 可通过 `display.width`、`display.height`、`display.raw_status_bar_height` 指定显示侧信息。
- 可通过 `warn_unexpected_hits: true` 让未声明期望的高分模板命中作为 warning 暴露，用于发现误识别。

当前支持 adb 基础设备接口：
- 模块：`tools/yuanassist_test_tool/adb_device.py`
- 命令：`python -m tools.yuanassist_test_tool adb-devices`
- 默认优先使用本机 Android SDK 路径：`C:\Users\17525\AppData\Local\Android\Sdk\platform-tools\adb.exe`，找不到再从 PATH 查找 `adb.exe` / `adb`。
- 默认本地模拟器目标是 `emulator-5554`，对应“ADB 调试: 本地连接 / ADB调试端口: emulator-5554”；该目标作为 `defaultTarget` 单独暴露，不混入真实设备列表。
- 通过 `adb devices -l` 列出设备，`emulator-*` 归类为模拟器，其余设备归类为 USB。
- 报告设备状态：`device`、`offline`、`unauthorized` 等。
- 已预留 `capture_screenshot` 截图 helper，供后续“Py + adb 采集当前模拟器/USB 画面并保存为视觉回归素材”使用；当前 CLI 不主动截图。

当前支持 App 冒烟体检：
- 模块：`tools/yuanassist_test_tool/app_smoke.py`
- 命令：`python -m tools.yuanassist_test_tool app-smoke`
- 默认目标仍是本地模拟器 `emulator-5554`。
- 可通过 `--apk path\to\app.apk` 提供 APK；提供 APK 时默认先执行 `adb install -r`，也可用 `--no-install` 只检查已安装应用。
- 会读取设备 Android 版本、型号、分辨率、DPI。
- 会读取设备上 `com.example.yuanassist` 的 `versionCode` / `versionName`，并与 `app/build.gradle.kts` 对比。
- 会检查无障碍服务 `YuanAssistService` 是否开启、悬浮窗 appops 是否允许。
- 默认启动前清理 logcat，冷启动 `com.example.yuanassist/.ui.MainActivity`，等待后检查进程是否存活。
- 会保存启动截图、logcat 和 `summary.json` 到 `tools/yuanassist_test_tool/reports/app_smoke/<timestamp>/`。
- 会扫描关键崩溃/异常日志：`FATAL EXCEPTION`、ANR、`SecurityException`、权限拒绝、Activity 启动失败。

当前支持真实数据入口：
- 通过 Supabase CLI 执行只读查询：`supabase db query --linked`。
- 从 `public.strategy_detail` 拉取本站攻略 payload。
- 默认只拉有 `scriptContent` 的公开攻略。
- 可保存为本地 case JSON。
- 可拉取后直接批量执行战斗静态检查。

当前支持本地可视化界面：
- 可双击项目根目录的 `start_yuanassist_test_tool.bat` 一键启动。
- 命令：`python -m tools.yuanassist_test_tool ui --port 8899`
- 地址：`http://127.0.0.1:8899`
- 新增 Tab：`基础诊断`，用于查看 adb 路径、默认本地模拟器目标 `emulator-5554`、USB / 模拟器设备列表和在线状态。
- 新增 Tab：`App 冒烟`，用于选择 adb 目标和可选 APK 路径，一键执行安装、冷启动、权限状态、截图、logcat 和崩溃扫描。
- 新增 Tab：`截图调试`，用于从前端选择 adb 目标，采集当前设备截图，并整理全部日常脚本引用过的视觉单位；模板单位会进行匹配，OCR 单位先按 `目标文字 + ROI` 展示和保存期望。
- `截图调试` 保存位置：`tools/yuanassist_test_tool/fixtures/daily_vision/<script>/<case_id>/`，包含 `screenshot.png` 和 `case.json`。
- `截图调试` 的“采集截图”只写入临时草稿目录 `tools/yuanassist_test_tool/.tmp/vision_capture/`；只有点击“保存回归素材”后才会提升到 `fixtures/daily_vision/` 并进入前端回归 case 列表。
- 测试工具 Web UI 启动或打开首页时会自动清理 `tools/yuanassist_test_tool/.tmp/vision_capture/`，避免未保存截图草稿堆积。
- `截图调试` 的首次采集预览只展示观察结果，不触发 `EXPECTATIONS_EMPTY` 或 `UNEXPECTED_TEMPLATE_HIT` 这类回归提示；保存素材后再跑 `daily-vision` 回归时才按 expectations 和 `warn_unexpected_hits` 检查。
- `截图调试` 展示视觉节点时会去重：模板按 `template_name + ROI`，OCR 按 `target_text/target_chars + ROI`；同一单位只展示一次，不同 ROI 会分别展示。合并项会保留 `duplicateCount` / `duplicateNodes` 供前端提示。
- `截图调试` 前端会按观察结果默认标记模板单位：过阈值为 `hit`，没过阈值为 `miss`；用户再手动修正误判项。
- `截图调试` 内置回归测试区：可刷新已保存 case 列表、测试全部正式 case、测试选中 case，或用当前 adb 设备截图替换选中 case 的 `screenshot.png` 后立即测试；前端列表会过滤掉没有 expectations 的历史空 case。
- `截图调试` 的“忽略”会保存为 `{"type":"ignore"}`，用于压制 `warn_unexpected_hits` 下的“未声明期望的模板也命中”提醒。
- `截图调试` 当前会展示 OCR 节点并允许保存期望文本，但真实 OCR 离线后端仍未接入，回归时继续报告 `SKIP_OCR_BACKEND`。
- 新增 Tab：`日常脚本体检`，用于检查内置日常脚本、模板资产和子脚本引用。
- 日常脚本体检卡片支持打开对应 JSON 侧边栏，侧边栏可拖动调宽、编辑、格式化、保存，并在保存前校验 JSON。
- JSON 侧边栏会把重点字段的值显示成标签样式：`name`、模板文件名、OCR 目标文字、阈值和对齐模式。
- 点击日常脚本体检中的 warning/error 会打开对应 JSON，并尽量跳转到相关任务或字段附近。
- 保留 Tab：`战斗攻略检查`，用于从 Supabase CLI 只读拉取本站攻略并检查战斗 payload。
- 能配置拉取数量、是否只看公开攻略、是否保存本地 case。
- 能展示用例数、通过/失败、警告/错误、回合/动作/指令数量。

当前运行方式：
```powershell
python -m tools.yuanassist_test_tool battle-static --script-file path\to\script.txt
```

也支持 case 文件：
```powershell
python -m tools.yuanassist_test_tool battle-static --case-file path\to\case.json --json
```

拉取本站攻略 payload：
```powershell
python -m tools.yuanassist_test_tool fetch-strategies --limit 20
```

拉取并检查：
```powershell
python -m tools.yuanassist_test_tool check-strategies --limit 20
```

检查内置日常脚本资产：
```powershell
python -m tools.yuanassist_test_tool daily-assets
```

检查日常视觉回归素材：
```powershell
python -m tools.yuanassist_test_tool daily-vision
```

检查单个日常视觉回归素材：
```powershell
python -m tools.yuanassist_test_tool daily-vision --case-file tools\yuanassist_test_tool\fixtures\daily_vision\sample\case.json
```

查看 USB / 模拟器 adb 设备：
```powershell
python -m tools.yuanassist_test_tool adb-devices
```

执行 App 冒烟体检：
```powershell
python -m tools.yuanassist_test_tool app-smoke --apk app\build\outputs\apk\debug\app-debug.apk
```

只检查已安装 App：
```powershell
python -m tools.yuanassist_test_tool app-smoke --no-install
```

## 战斗版悬浮窗测试计划池

### 入口与权限
- 无障碍服务开启后，战斗悬浮窗能正常创建。
- 悬浮窗权限未开时提示与跳转正确。
- App 重启、服务重连后状态正确。
- 导入脚本后再开启无障碍，能恢复待导入脚本。
- 悬浮窗关闭和服务销毁后无残留 overlay。

### 悬浮窗基础 UI
- 初始按钮、状态文案、回合表格、模式按钮显示正确。
- 拖动、最小化、恢复正常。
- 设置入口、键位修正入口、自动选人开关可用。
- 不同模拟器分辨率下不裁切、不重叠。

### 录制模式
- 切换录制模式后，全屏输入层出现。
- 点击屏幕能记录动作。
- A、上、下、圈等战斗动作能被正确记录。
- 录制模式额外按钮可快速写入特殊指令。
- 新增回合、下一回合、撤销、清空、编辑表格正常。
- 导出脚本格式正确。

### 跟打模式
- 导入脚本后可切换跟打模式并展示表格。
- 开始、暂停、继续、停止状态正确。
- 当前回合、当前动作状态显示正确。
- 起始回合设置生效。
- 空动作、未知动作、非法回合有提示。
- 停止或暂停后不会继续执行手势。

### 战斗动作执行
- A、上、下、圈坐标落点正确。
- 坐标使用老战斗坐标体系，避免混用日常 1080x1920 链路。
- 键位修正只保存对应动作 y，x 仍由列位算法计算。
- 点击持续时间、滑动持续时间、滑动距离配置生效。
- 录制模拟参数和跟打执行参数互不串。

### 键位修正
- 打开后显示 A、上、下、圈四个标记。
- 四个标记分别落在 1-4 号位中间。
- 拖动后保存对应动作距离底部的 y。
- 重新打开 App 或服务后修正值仍在。
- 极端拖动不产生非法坐标。

### 战斗设置
- 基础参数读取、修改、保存、重启后保持。
- 敌方回合、起始回合、回合检测开关生效。
- 自动导航配置项展示正确。
- 录制模式和跟打模式高级参数分别生效。
- 非数字、空值、过大/过小数值有兜底或提示。

### 自动选人
- 自动选人开关能开启和关闭。
- 未配置满角色时禁止启动并提示。
- Android 11 以下提示不支持。
- 角色配置保存与读取正确。
- 自动选人完成后能进入跟打。
- 自动选人失败时停止后续流程。
- 自动导航 -> 自动选人 -> 跟打链路顺序正确。

### 自动导航与战斗检测
- 开始战斗模板识别成功后点击进入。
- 未识别开始战斗时给出失败提示。
- 战斗内、前往讨伐、洞窟、主页入口等分支识别正确。
- 全灭或失败后再次挑战识别与恢复。
- 回合数 OCR 检测通过和失败分支正确。
- 暴击检测、橙星检测、紫星检测触发点正确。
- 自动导航失败后不会继续盲点。

### 攻略与作业导入
- 本站攻略详情“导入脚本”能导入战斗脚本。
- MaaYuan Share 攻略详情能解析阵容、回合、动作。
- MaaYuan 内容解析失败时提示清楚。
- 导入后悬浮窗切到跟打模式可看到表格。
- 攻略中空回合、特殊检测、备注、自动导航指令可保留。
- 收藏、评论、原帖链接等附属操作不破坏脚本数据。

### 表格 OCR 导入
- 攻略原图 OCR 能识别回合表。
- 表格列检测、动作解析、后处理正确。
- OCR 识别成功后导入跟打模式。
- OCR 失败、图片为空、图片过大有提示。
- OCR 结果中的动作别名能归一化。
- 回合数、行动序号、备注解析正确。

### 发布攻略与编辑攻略
- 从录制数据生成攻略脚本内容。
- 无攻略原图时可用录制表格导出图生成封面。
- 发布时标题、说明、游戏版本、自定义标签保存正确。
- 编辑已有攻略能回填原脚本。
- 修改脚本后再次保存不丢回合数据。
- 上传失败、保存失败、缺少攻略 ID 有提示。

### 运行日志
- 录制、导入、跟打、自动选人、自动导航都有可读日志。
- 失败日志包含用户能理解的原因。
- 日志模块能归类到战斗或作业站。
- 单条日志保持单行，不破坏运行日志展示。
- App 冷启动清空日志符合预期。

### 性能与稳定性
- 悬浮窗创建耗时。
- 导入大攻略脚本耗时。
- 跟打 100+ 动作时 UI 不明显卡顿。
- 自动导航截图和模板匹配耗时。
- OCR 回合检测耗时。
- 长时间暂停、继续、停止无泄漏、无重复执行。
- 多次导入不同攻略后旧数据不残留。

## 推荐入手顺序
1. 内置日常脚本资产体检：先稳定覆盖脚本图、模板引用、子脚本串联和调试可见性。
2. 日常视觉回归素材采集：先通过本地截图 fixture 覆盖日常脚本视觉节点、自动导航和关键恢复链路；后续基于 `adb_device.py` 加 Py + adb 的半自动采集按钮。
3. OCR 回归后端：优先保持 case 格式稳定，再评估 Python Paddle 后端或客户端导出 OCR 结果的混合方案。
4. 战斗脚本和攻略导入的静态回归测试：保留为辅助检查，不作为主线。
5. 用 Appium / UiAutomator2 覆盖日常脚本导入、调试页节点选择、模板替换/恢复。
6. 覆盖战斗悬浮窗的跟打模式展示、开始、暂停、继续、停止。
7. 做长链路性能基准：截图、模板匹配、OCR、长脚本循环稳定性。

## 关键边界
- 未经确认，不直接修改线上 Supabase 数据或配置。
- Supabase 数据入口只使用 CLI 做只读查询，不使用 Supabase skill，不执行写 SQL。
- 设备自动化优先使用模拟器，不默认连接真机。
- 战斗坐标链路使用老战斗坐标体系，不与日常脚本 1080x1920 基准混用。
- 第一阶段优先做可离线、可重复的静态和半自动测试，降低游戏画面和系统权限带来的不稳定性。
