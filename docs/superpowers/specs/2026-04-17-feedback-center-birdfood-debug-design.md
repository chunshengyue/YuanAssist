# 反馈中心与鸟食调试冷却日志设计

**目标**

在不改变现有鸟食调度逻辑的前提下，为调试模式补充冷却状态日志；同时在 Mine 页面增加一个与现有风格一致的问题反馈入口，提供提交反馈、上传截图、附带运行日志，以及查看当前设备历史反馈与官方回复的能力。

**范围**

- 鸟食任务仅在 `debugModeEnabled=true` 时打印 30 秒周期冷却日志。
- Mine 页面新增“问题反馈”入口。
- 新增独立 `FeedbackCenterActivity` 页面。
- 复用现有 Bmob 用户体系，以 `deviceId` 作为反馈归属主键。
- Bmob 使用单表承载反馈内容与后台回复。

**不做**

- 不修改鸟食冷却时长和调度策略。
- 不新增消息系统或多表联动。
- 不做未登录状态下的反馈提交或浏览。

## 一、鸟食调试冷却日志

### 1. 行为

- 鸟食任务启动时，如果 `debugModeEnabled=false`，保持现状，不输出额外冷却轮询日志。
- 如果 `debugModeEnabled=true`，启动一个 30 秒周期任务：
  - 枚举当前已选择的鸟食任务。
  - 按任务状态输出：
    - `就绪`
    - `冷却中，剩余 mm:ss`
    - `已耗尽`
- 某个任务进入冷却时输出显眼日志：
  - `【冷却开始】待办公务，预计 310 秒后重试`
- 某个任务结束冷却并重新变为可执行时输出显眼日志：
  - `【冷却结束】待办公务，已恢复可执行`

### 2. 状态来源

- `nextReadyAt[taskType]`：冷却结束时间。
- `activeTasks`：仍可参与调度的任务集合。
- 若任务不在 `activeTasks` 中，表示已耗尽，不参与“冷却结束”日志。

### 3. 颜色策略

- `RunLogger` 仍保存纯文本，避免侵入式改动。
- 显眼效果通过统一前缀实现，便于运行日志页面后续识别：
  - `【冷却开始】`
  - `【冷却结束】`
  - `【冷却状态】`

## 二、反馈中心

### 1. 页面结构

新增 `FeedbackCenterActivity`，整体风格对齐现有 `MyMessageActivity`：

- 顶部玻璃态 Header + 返回按钮 + 标题“问题反馈”
- 中间为提交表单卡片
- 下方为“我的反馈”列表

### 2. 提交表单

表单包含：

- 多行文字输入框：问题描述
- 图片上传区域：最多上传当前选择的一张截图
- 开关：是否附带运行日志
- 提交按钮

提交规则：

- 必须已登录
- 以当前设备 `ANDROID_ID` 写入 `deviceId`
- 图片先上传图床，成功后保存 URL 到 Bmob
- 若勾选附带日志，提交 `RunLogger.getAllLogs()`

### 3. 历史反馈列表

列表只展示当前设备 `deviceId` 对应的数据，按 `createdAt` 倒序。

每条反馈展示：

- 提交时间
- 问题描述摘要
- 是否附带图片/日志
- 官方回复

无回复时展示 `暂未回复`。

## 三、Bmob 表设计

表名：`issue_feedback`

字段：

- `deviceId: String` 当前设备唯一标识，查询主键
- `user: Pointer<_User>` 当前登录用户，可选辅助字段
- `description: String` 问题描述
- `logContent: String` 可为空，附带日志全文
- `imageUrls: String` 可为空，先按单图 URL 存储；字段名保留复数，便于后续扩展
- `reply: String` 后台填写回复
- `status: Int` 状态，建议 0=待处理，1=已回复

## 四、代码落点

- 鸟食日志：
  - `app/src/main/java/com/example/yuanassist/core/BirdFoodRuntimeManager.kt`
  - `app/src/main/java/com/example/yuanassist/utils/RunLogger.kt`

- 反馈中心：
  - `app/src/main/java/com/example/yuanassist/ui/MineFragment.kt`
  - `app/src/main/java/com/example/yuanassist/ui/FeedbackCenterActivity.kt`
  - `app/src/main/java/com/example/yuanassist/ui/FeedbackRecordAdapter.kt`
  - `app/src/main/java/com/example/yuanassist/model/Feedback.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/layout/fragment_mine.xml`
  - `app/src/main/res/layout/activity_feedback_center.xml`
  - `app/src/main/res/layout/item_feedback_record.xml`

## 五、验证重点

- 非调试模式运行鸟食时，不应出现额外冷却轮询日志。
- 调试模式下，开始冷却、周期状态、结束冷却日志应按条件输出。
- 未登录进入反馈中心或提交反馈时，应提示先登录。
- 反馈提交后，本机历史列表能立即看到新纪录。
- Bmob 后台填写 `reply` 后，列表能显示到对应条目。
