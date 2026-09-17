# UI 规范与可复用组件

> 当前 UI 风格、修改准则，以及 Compose / 传统 View 的可复用组件清单。
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [modules](modules.md)

---

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
  - 首页紧凑按钮标签会限制系统字体放大到原设计比例，并对极端窄屏使用单行省略保护；不要把这条局部适配扩展为全局字体策略。

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
