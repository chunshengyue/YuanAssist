# 入口与排查路由

> 「要做什么 -> 先看哪个文件」的路由表，以及按症状排查的检查顺序。改动频率最高，优先维护这份。
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [modules](modules.md)

---

## 先看哪里
- 想找测试工具当前能力、技术栈和计划：`docs/test_tool_context.md`；其中一键回归入口位于 `tools/yuanassist_test_tool/regression.py`，App 冒烟体检实现位于 `tools/yuanassist_test_tool/app_smoke.py`
- 想找主界面入口与功能跳转：`app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- 想找首页按钮怎么进入各功能：`app/src/main/java/com/example/yuanassist/ui/main/HomeActionHandler.kt`
- 想找日常全局设置页：`app/src/main/java/com/example/yuanassist/ui/GlobalSettingsActivity.kt`
- 想找无障碍服务、悬浮窗、服务 action 分发：`app/src/main/java/com/example/yuanassist/core/YuanAssistService.kt`
- 想找日常脚本执行引擎：`app/src/main/java/com/example/yuanassist/core/AutoTaskEngine.kt`
- 想找调试页/模板调试/OCR 调试：`app/src/main/java/com/example/yuanassist/ui/main/DebugWorkbenchCoordinator.kt`
- 想找录制脚本的查看与编辑：`app/src/main/java/com/example/yuanassist/ui/RecordedDailyScriptViewerActivity.kt`
- 想找用户脚本与模板存储：`app/src/main/java/com/example/yuanassist/utils/UserDailyScriptStore.kt`
- 想找模板替换覆盖逻辑：`app/src/main/java/com/example/yuanassist/utils/TemplateOverrideStore.kt`
- 想找角色导入识别链路：`app/src/main/java/com/example/yuanassist/core/CharacterImportEngine.kt`

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
