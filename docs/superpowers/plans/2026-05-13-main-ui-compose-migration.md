# Main UI Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `myapplication2_ui_bundle` 的四页 Compose 原型直接落为 YuanAssist 新主界面，并把现有首页、作业、调试、我的业务能力正确接入这套 UI。

**Architecture:** 保留现有业务能力和二级页面，不保留旧的主界面承载方式。主入口改成 Compose Shell，由 `MainActivity` 直接渲染四页主界面；首页、作业、我的先做“Compose 外壳 + 复用现有业务入口”，调试页不允许只跳旧 `TestActivity`，而是要把 `TestActivity` 的业务逻辑抽出后接入新的 Compose 调试页。

**Tech Stack:** Kotlin, Android View system, Jetpack Compose, Material 3, existing `Activity`/`Fragment`, existing `YuanAssistService`, existing local assets and runtime repositories.

---

## Current Mapping

### 现有主入口

- `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
  当前为 `AppCompatActivity + activity_main_dashboard.xml + Fragment` 切页。
- `app/src/main/res/layout/activity_main_dashboard.xml`
  当前主壳为背景图 + `FrameLayout` + `BottomNavigationView`。
- `app/src/main/res/menu/bottom_nav_menu.xml`
  当前底栏为 `运行 / 作业 / 日常 / 我的`。

### 新原型页面对应关系

- 新 `首页`
  承接旧 `HomeFragment.kt` 的战斗入口、设置、脚本库、日志、FAQ、更新等入口。
  同时吸收旧 `DailyFragment.kt` 的日常快捷入口，因为新底栏不再保留“日常”tab。
- 新 `作业`
  承接旧 `JobStationEntryFragment.kt`，入口仍然进入 `JobStationListActivity`。
- 新 `调试`
  承接旧 `TestActivity.kt` 的截图上传、任务选择、局部识别、素材替换、延迟增量、日志输出等能力。
- 新 `我的`
  承接旧 `MineFragment.kt` 的登录态、头像昵称编辑、收藏/发布/消息/反馈/官网/排除密探等入口。

### 需要先确认的唯一业务缺口

- 原型首页按钮里有 `无月卡观星`，当前代码中没有同名现成入口。
  执行前必须先确认它对应现有哪个能力；如果当前仓库确实没有，就不能擅自映射成别的业务。

---

## File Structure

### 修改

- `app/build.gradle.kts`
- `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`

### 新增

- `app/src/main/java/com/example/yuanassist/ui/main/MainShellScreen.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/MainShellModels.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/HomeTabScreen.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/JobTabScreen.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/DebugTabScreen.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/MineTabScreen.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/components/GufengDecorActionButton.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/components/GufengFeatureCard.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/theme/MainShellTheme.kt`
- `app/src/main/java/com/example/yuanassist/ui/main/HomeActionHandler.kt`
- `app/src/main/java/com/example/yuanassist/ui/debug/DebugWorkbenchState.kt`
- `app/src/main/java/com/example/yuanassist/ui/debug/DebugWorkbenchViewModel.kt`
- `app/src/main/java/com/example/yuanassist/ui/debug/DebugWorkbenchCoordinator.kt`

### 复用但要抽逻辑

- `app/src/main/java/com/example/yuanassist/ui/HomeFragment.kt`
- `app/src/main/java/com/example/yuanassist/ui/DailyFragment.kt`
- `app/src/main/java/com/example/yuanassist/ui/JobStationEntryFragment.kt`
- `app/src/main/java/com/example/yuanassist/ui/MineFragment.kt`
- `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

### 资源迁移来源

- `myapplication2_ui_bundle/MainActivity.kt`
- `myapplication2_ui_bundle/ui/GufengDecorActionButton.kt`
- `myapplication2_ui_bundle/ui/GufengFeatureCard.kt`
- `myapplication2_ui_bundle/res/drawable/*`
- `myapplication2_ui_bundle/res/drawable-nodpi/*`

---

## Task 1: 接入 Compose 主壳基础设施

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/theme/MainShellTheme.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/components/GufengDecorActionButton.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/components/GufengFeatureCard.kt`
- Copy assets into: `app/src/main/res/drawable/` and `app/src/main/res/drawable-nodpi/`

- [ ] 在 `app/build.gradle.kts` 打开 Compose：`buildFeatures.compose = true`，补齐 `activity-compose`、Compose BOM、`ui`、`ui-tooling-preview`、`material3`、`foundation` 依赖。
- [ ] 不把 bundle 的 `theme` 原样照搬；只提取这套 UI 真正使用到的颜色、字体、背景和组件样式，避免引入一套没被原型实际使用的模板主题。
- [ ] 把 bundle 素材迁到 app 正式资源目录，保留现有资源命名风格；同名冲突必须先比对用途，不能直接覆盖旧资源。
- [ ] 把 `GufengDecorActionButton` 和 `GufengFeatureCard` 迁成 app 内正式组件，包名改到 `com.example.yuanassist`。

## Task 2: 用 Compose 重建 MainActivity 根导航

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/MainShellScreen.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/MainShellModels.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/MainShellActions.kt`

- [ ] 删除 `MainActivity` 对 `activity_main_dashboard.xml`、`BottomNavigationView`、`Fragment` 根切页的依赖，改成 `setContent { ... }` 直接渲染 Compose 主壳。
- [ ] 保留 `EXTRA_TARGET_TAB` 协议，但重映射目标：
  `TARGET_TAB_HOME -> 首页`，
  `TARGET_TAB_STRATEGY -> 作业`，
  `TARGET_TAB_PROFILE -> 我的`，
  `TARGET_TAB_DAILY -> 首页日常分区`。
- [ ] 新底栏按原型固定为 `首页 / 作业 / 调试 / 我的`，不再保留旧 `日常` 根 tab。
- [ ] `MainShellScreen` 只负责状态和路由，不直接写业务；所有点击行为通过 `MainShellActions` 从 `MainActivity` 注入。

## Task 3: 首页接入现有运行与日常业务

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/main/HomeTabScreen.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/main/HomeActionHandler.kt`
- Read/extract from: `app/src/main/java/com/example/yuanassist/ui/HomeFragment.kt`
- Read/extract from: `app/src/main/java/com/example/yuanassist/ui/DailyFragment.kt`
- Reuse: `app/src/main/java/com/example/yuanassist/ui/SettingsActivity.kt`
- Reuse: `app/src/main/java/com/example/yuanassist/ui/RunLogActivity.kt`
- Reuse: `app/src/main/java/com/example/yuanassist/ui/FaqActivity.kt`
- Reuse: `app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt`

- [ ] 先把 `HomeFragment` 里的悬浮窗权限检查、无障碍检查、`ACTION_START_COMBAT_WINDOW`、`ACTION_CLOSE_COMBAT_WINDOW` 抽成可复用 handler，不能把 Fragment 代码直接复制进 Compose。
- [ ] 把 `DailyFragment` 里的日常能力入口拆成首页按钮动作：
  `刷鸟食` 对应鸟食流程，
  `刷6-24` 对应 `DailyMainline624Fragment` 现有入口，
  `星石拼图` 对应 `DailyInventoryStitchFragment` 现有入口，
  `屏幕选点` 对应 `ACTION_START_COORDINATE_PICKER`，
  `脚本录制` 对应 `ACTION_START_DAILY_SCRIPT_RECORDER`。
- [ ] `常用入口` 区直接复用旧业务：
  `运行日志 -> RunLogActivity`，
  `常见问题 -> FaqActivity`，
  `问题反馈 -> FeedbackCenterActivity`，
  `脚本库 -> ScriptLibraryActivity`，
  `检查更新 -> HomeFragment` 现有更新逻辑。
- [ ] 首页里的 `调试` 按钮不再打开旧按钮页，而是切到底栏 `调试` tab。
- [ ] `设置` 先复用 `SettingsActivity`；这是现成业务页，不需要在本轮主界面迁移时一并重写。

## Task 4: 作业页接入现有作业站入口

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/main/JobTabScreen.kt`
- Reuse: `app/src/main/java/com/example/yuanassist/ui/JobStationEntryFragment.kt`
- Reuse: `app/src/main/java/com/example/yuanassist/ui/JobStationListActivity.kt`

- [ ] 不迁移旧 `JobStationEntryFragment` 的 XML 外观，只保留它的业务跳转语义。
- [ ] 新作业页两张卡片直接对应旧入口：
  `本站攻略 -> JobStationListActivity.SourceMode.COMMUNITY`，
  `MaaYuan Share -> JobStationListActivity.SourceMode.MAA`。
- [ ] 进入列表页、详情页、上传页后仍可继续复用旧 `Activity` 体系，不在本轮把整个作业站二级页面一口气改成 Compose。

## Task 5: 调试页迁入 TestActivity 业务，而不是只保留跳转

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/main/DebugTabScreen.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/debug/DebugWorkbenchState.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/debug/DebugWorkbenchViewModel.kt`
- Create: `app/src/main/java/com/example/yuanassist/ui/debug/DebugWorkbenchCoordinator.kt`
- Extract from: `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`

- [ ] 先按职责拆 `TestActivity`：截图输入、任务/模板选项装载、局部识别范围、测试执行、素材替换/还原、延迟增量、日志输出，至少拆成“状态 + 协调器”，不能继续维持一个超大 Activity 承载所有 UI 和逻辑。
- [ ] 新调试页的 Compose 布局必须直接承接 bundle 原型里的三个核心区：
  `测试截图`，
  `测试配置`，
  `测试日志`。
- [ ] `上传截图` 继续复用当前文档选择能力；
  `开始测试` 对应旧 `runCurrentTest()` 语义；
  `一键替换`、`还原素材` 对应旧模板替换/还原语义；
  `保存增量`、`清除增量` 对应旧模板延迟增量逻辑。
- [ ] `识别范围` 的 `全屏识别 / 局部识别` 要直接驱动旧 `isLocalScopeEnabled()` 相关能力，不能只改 UI 文案。
- [ ] 旧 `TestActivity` 最终只允许保留为同一套调试状态和组件的宿主，或者在主界面接管后删除；不允许主界面和旧页面各自维护一套调试逻辑。

## Task 6: 我的页接入账户与个人入口

**Files:**
- Create: `app/src/main/java/com/example/yuanassist/ui/main/MineTabScreen.kt`
- Reuse/extract from: `app/src/main/java/com/example/yuanassist/ui/MineFragment.kt`

- [ ] 先把 `MineFragment` 的“读登录态 / 打开登录 / 打开头像昵称编辑 / 打开同步”动作抽成 Compose 可调用的 handler。
- [ ] 新我的页首卡显示当前用户缓存信息；未登录时保留现有“绑定当前设备/一键登录”逻辑。
- [ ] 六个入口先复用旧业务：
  `我的发布 -> MyPublishedActivity`，
  `我的星石 -> MyStoneActivity`，
  `我的收藏 -> MyFavoriteActivity`，
  `消息 -> MyMessageActivity`，
  `排除密探 -> MineFragment` 当前对话框逻辑，
  `官网和教程 -> 官方站点`。
- [ ] `问题反馈` 仍走现有登录校验逻辑，不能因为迁 UI 丢掉登录前置判断。

## Task 7: 切换主入口并清理旧主壳

**Files:**
- Modify: `app/src/main/java/com/example/yuanassist/ui/MainActivity.kt`
- Review for deletion or dead code:
  `app/src/main/res/layout/activity_main_dashboard.xml`
  `app/src/main/res/menu/bottom_nav_menu.xml`
  `app/src/main/java/com/example/yuanassist/ui/HomeFragment.kt`
  `app/src/main/java/com/example/yuanassist/ui/DailyFragment.kt`
  `app/src/main/java/com/example/yuanassist/ui/JobStationEntryFragment.kt`
  `app/src/main/java/com/example/yuanassist/ui/MineFragment.kt`

- [ ] 当新 Compose 主壳四页都接通后，移除旧根 Fragment 切页代码，避免主入口同时维护两套导航。
- [ ] 旧根页面专用 XML、底栏 menu、仅服务于旧主壳的辅助样式全部做一次死代码审查；确认无引用后再删除。
- [ ] 保留二级 `Activity` 没问题，但旧根 `Fragment` 如果已经不再被任何入口使用，就应当收敛或删除，避免业务入口双写。

---

## Verification Checklist

- [ ] 首页战斗版按钮能正确开关战斗悬浮窗，权限提示与旧行为一致。
- [ ] 首页日常版按钮能正确拉起对应日常能力，不出现“新按钮文案和旧业务不一致”。
- [ ] 作业页两张卡进入的列表源正确。
- [ ] 调试页能完整走通：上传截图、切任务、切模板、切识别范围、开始测试、替换素材、还原素材、复制日志。
- [ ] 我的页登录态、入口跳转、反馈前置校验、排除密探设置都与旧行为一致。
- [ ] `TARGET_TAB_*` 外部入口仍能落到正确页面。

## Notes

- 本次计划只做主界面及其一级入口迁移，不主动扩展到所有二级页面重写。
- 这不是“先包一层再说”的兼容方案；最终主入口只保留 Compose Shell 一套。
- 当前没有运行构建、测试或安装验证；本文件只是后续实施参考。
