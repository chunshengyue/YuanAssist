# 云端脚本设计

## 目标
- 新增首页入口「云端脚本」，位置在「脚本库」后面，其余入口顺位后移。
- 只服务日常录制脚本共享；战斗脚本继续由 JobStation 承担。
- 所有用户都能浏览、上传、下载日常录制脚本。
- 云端脚本可保存到本地脚本库，也可直接导入日常悬浮窗。

## 范围
- 支持的脚本类型：`UserDailyScriptStore` 管理的日常脚本 bundle。
- bundle 内容：`script.json` 与 `templates/` 目录内的模板图片。
- 不接入审核流程。上传成功后默认公开可见。
- 保留 `status` / `visible` 类字段，用于后续下架、隐藏或审核扩展。

## 首页入口
- 在 `HomeTabScreen` 的「常用入口」中，在「脚本库」后追加「云端脚本」。
- `HomeTabActions` 增加 `onOpenCloudScripts`。
- `MainActivity` 绑定入口，打开云端脚本列表页。
- 入口命名固定为「云端脚本」。

## 页面结构
- 新增云端脚本列表页，视觉和交互参考 `JobStationListActivity`：
  - 顶部返回。
  - 搜索框。
  - 排序 chip：热门、最新。
  - 列表卡片展示标题、作者、更新时间、任务数量、下载数、标签摘要。
  - FAB 进入上传页。
  - 下拉刷新。
  - 空状态和加载失败提示。
- 新增云端脚本详情页，视觉参考 `JobStationActivity`：
  - 展示标题、作者、发布时间、更新时间、下载数、任务数量、说明、标签。
  - 展示「图片指引」模块。
  - 底部操作：保存到本地脚本库、导入日常悬浮窗。
- 新增上传页：
  - 选择一个本地录制日常脚本。
  - 填写标题、简介、标签。
  - 上传图片指引。
  - 提交后打包脚本 bundle 并发布到云端。

## 图片指引
- 「图片指引」是云端脚本页面的独立模块，不混入普通简介。
- 上传页允许用户选择多张图片作为图片指引。
- 图片指引上传到现有图床，复用 `UploadStrategyActivity` 的图床上传方式。
- 发布云端脚本时，将图片 URL 列表写入云端脚本元数据。
- 详情页按顺序展示图片指引。
- 如果没有图片指引，详情页隐藏该模块。

## 云端存储方案
- 采用方案 A：客户端直传 Supabase Storage，Edge Function 写元数据。
- App 将 `script.json + templates/` 打成 zip。
- App 调用 Edge Function 创建一次性上传信息。
- App 使用 OkHttp 将 zip 上传到 Supabase Storage。
- App 上传完成后调用 Edge Function 发布元数据。
- 下载时通过 Edge Function 获取短期下载 URL。
- App 下载 zip 后解压为本地 `UserDailyScriptStore` bundle。

## Supabase Storage
- 新建 bucket：`daily-script-bundles`。
- 每个脚本 bundle 存为一个 zip 文件。
- 建议路径：`daily/{authorObjectId}/{scriptObjectId}.zip`。
- bucket 不需要公开读；下载通过 Edge Function 生成短期 signed URL。
- 客户端不暴露 service role key。

## 数据模型
- 新表：`cloud_daily_scripts`。
- 字段建议：
  - `id`：uuid 主键。
  - `object_id`：短 ID，对客户端暴露。
  - `author_id`：关联 `User.id`。
  - `title`：标题，必填。
  - `description`：简介。
  - `tags`：字符串或 JSON 字符串。
  - `guide_images`：图片指引 URL 列表，JSON 字符串。
  - `bundle_path`：Storage 对象路径。
  - `bundle_size`：zip 大小。
  - `task_count`：脚本任务数量。
  - `download_count`：下载数，默认 0。
  - `status`：默认 `published`。
  - `created_at` / `updated_at`。
- 列表只返回 `status = published` 的脚本。

## Edge Function Actions
- `create-daily-script-upload`
  - 入参：`deviceId`、`title`、`bundleSize`。
  - 行为：确保用户存在，生成 `scriptObjectId`、`bundlePath`、Storage signed upload URL。
- `publish-daily-script`
  - 入参：`deviceId`、`scriptObjectId`、`title`、`description`、`tags`、`guideImages`、`bundlePath`、`bundleSize`、`taskCount`。
  - 行为：写入或更新云端脚本元数据，默认 `status = published`。
- `list-daily-scripts`
  - 入参：`sortMode`、`keyword`、`limit`。
  - 行为：返回公开脚本列表。
- `get-daily-script-detail`
  - 入参：`scriptId`。
  - 行为：返回详情。
- `create-daily-script-download-url`
  - 入参：`scriptId`。
  - 行为：返回短期 signed download URL。
- `increment-daily-script-download`
  - 入参：`scriptId`。
  - 行为：下载成功后下载数加 1。

## 本地保存与直接导入
- 保存到本地脚本库：
  - 下载 zip。
  - 解压到 `UserDailyScriptStore.createBundle(context, title)` 创建的目录。
  - 写入 `script.json` 与 `templates/`。
  - 保存后可在现有脚本库中看到。
- 直接导入日常悬浮窗：
  - 也先下载并解压成本地 bundle。
  - 复用现有 `DailyPlanSelection` / `DailyScriptLibraryBridge` 或 `ACTION_IMPORT_RECORDED_DAILY_PLAN` 链路。
  - 使用本地 bundle 的 `templateDirPath`，保证模板匹配脚本能正常运行。

## 打包与解包
- zip 根目录必须包含：
  - `script.json`
  - `templates/`
- 解压时校验：
  - 必须存在 `script.json`。
  - `script.json` 必须能解析为 `DailyTaskPlan`。
  - 禁止 zip slip：解压后的文件路径必须位于目标 bundle 目录内。
  - 只接受普通文件和目录。
- 上传前统计 `task_count`。

## 错误处理
- 上传过程中分阶段提示：准备脚本、上传图片、打包脚本、上传脚本包、发布信息。
- 图片指引上传失败时阻止发布，并提示失败原因。
- 脚本包上传成功但发布元数据失败时，提示用户重试发布。
- 下载失败、解压失败、脚本解析失败分别给出短提示。
- 如果本地已有同名脚本，使用 `UserDailyScriptStore.createBundle` 的自动后缀机制。

## 安全与权限
- 客户端只调用 Edge Function，不直接使用 service role key。
- Storage bucket 不公开，下载通过短期 URL。
- 表保留 RLS/可见性控制空间；当前第一版由 Edge Function 的 service role 访问数据库。
- 不做审核，但可通过 `status` 字段下架。

## 验收标准
- 首页「脚本库」后出现「云端脚本」入口。
- 用户能从本地录制日常脚本上传整包。
- 用户能上传图片指引，并在详情页看到图片指引模块。
- 云端列表能展示公开脚本。
- 云端详情能保存脚本到本地脚本库。
- 云端详情能直接导入日常悬浮窗。
- 下载到本地后的脚本保留模板图片，可被现有日常脚本执行链路使用。
