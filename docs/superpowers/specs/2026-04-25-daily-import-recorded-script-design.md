# 日常页导入录制脚本设计

## 目标

在日常页面新增一个“运行录制脚本”入口。

这个入口只负责：

- 打开脚本库
- 选择一个录制生成的日常脚本
- 把该脚本导入到当前日常悬浮窗

这个入口不负责：

- 自动开始执行
- 新建独立悬浮窗
- 改变现有日常悬浮窗的运行方式

用户完成导入后，仍然通过现有日常悬浮窗的“开始”按钮执行脚本。

## 当前现状

- [DailyFragment.kt](D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/ui/DailyFragment.kt) 已有“脚本录制器”入口，但没有“运行录制脚本”入口
- [ScriptLibraryActivity.kt](D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/ui/ScriptLibraryActivity.kt) 已支持 `PICK_MODE_DAILY_PLAN`
- [DailyScriptLibraryBridge.kt](D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/core/DailyScriptLibraryBridge.kt) 已支持把 `fileName + jsonContent + templateDirPath` 回传
- [DailyWindowManager.kt](D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/core/DailyWindowManager.kt) 已支持接收 `DailyTaskPlan` 和 `templateDir`

说明“脚本库选日常脚本”和“日常悬浮窗执行任务”两头都已经具备，只缺日常页正式入口与回传落点。

## 方案对比

### 方案 A：日常页新增独立入口

- 在日常页新增“运行录制脚本”卡片
- 点击后进入脚本库的日常脚本选择模式
- 选中后只导入到日常悬浮窗当前任务

优点：

- 职责清晰
- 符合用户心智
- 与“脚本录制器”并列，不混淆录制和运行

缺点：

- 需要补一个 UI 入口和回调绑定

### 方案 B：复用“脚本录制器”入口做二级菜单

优点：

- 表面入口更少

缺点：

- “录制”和“运行”职责混在一起
- 用户更容易误点

### 方案 C：只在脚本库里加导入动作

优点：

- 改动最少

缺点：

- 主路径不在日常页
- 不符合本次需求

## 推荐方案

采用方案 A。

## 详细设计

### 1. 日常页新增入口

在 [fragment_daily.xml](D:/YuanAssist-master/app/src/main/res/layout/fragment_daily.xml) 新增一个与“脚本录制器”同级的入口卡片：

- 标题：运行录制脚本
- 副标题：从脚本库选择录制脚本并导入日常悬浮窗

在 [DailyFragment.kt](D:/YuanAssist-master/app/src/main/java/com/example/yuanassist/ui/DailyFragment.kt) 绑定点击事件。

### 2. 打开脚本库选择模式

点击“运行录制脚本”后：

- 打开 `ScriptLibraryActivity`
- 传入 `EXTRA_PICK_MODE = PICK_MODE_DAILY_PLAN`

这样脚本库只展示可导入的日常脚本，包括 `[录制] xxx`。

### 3. 回传与导入

`DailyFragment` 在进入脚本库前注册 `DailyScriptLibraryBridge.onDailyPlanSelected`。

收到回传后：

- 解析 `DailyPlanSelection`
- 启动现有日常悬浮窗服务
- 把选中的 `jsonContent + templateDirPath` 提交给 `DailyWindowManager`

这里导入动作的结果应该是：

- 当前日常悬浮窗已打开
- 当前脚本已成为悬浮窗待执行任务
- 悬浮窗按钮显示“开始”

不会自动执行。

### 4. 落点位置

最合适的导入落点不是 `DailyFragment` 自己持有任务，而是交给现有日常运行链路：

- `DailyFragment` 负责 UI 选择
- `YuanAssistService` / `DailyWindowManager` 负责接收并保存当前任务
- `DailyWindowManager.toggleExecution()` 继续复用现有开始逻辑

这能保证导入后的行为与内置日常任务完全一致。

### 5. 模板目录

导入录制脚本时必须保留 `templateDirPath`，并一路传递到 `DailyWindowManager.submitTaskPlan(...)`。

否则脚本虽然导入成功，但运行时模板图无法读取。

## 逻辑校验

完整链路：

1. 用户进入日常页
2. 点击“运行录制脚本”
3. 打开脚本库日常脚本选择模式
4. 用户选择某个 `[录制]` 脚本
5. 脚本内容与模板目录回传
6. 日常悬浮窗打开并加载该脚本为当前任务
7. 用户手动点击悬浮窗“开始”
8. `DailyWindowManager` 用导入的 `plan + templateDir` 执行

这样“导入”和“运行”两个动作被明确拆开，符合你的要求。

## 范围控制

本次不做：

- 选中后自动开始
- 多脚本队列
- 脚本收藏/默认脚本
- 新的悬浮窗类型
- 修改录制脚本格式
