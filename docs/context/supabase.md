# Supabase 维护指南

> 云端表、Storage、RLS 与数据维护流程。涉及线上变更前必读。
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [modules](modules.md)

---

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
- 云端角色补充字典：
  - `public.game_agents` 用于存放本地 `AgentRepository` 尚未覆盖的新角色；结构脚本是 `supabase/game_agents_schema.sql`。
  - 一行包含名称、别名、属性/职业、技能 JSON、命盘 JSON、扩展资料及发布信息；同一游戏版本内角色名称唯一，其中 `game_version` 为 `1=如鸢`、`0=代号鸢`。
  - 头像只保存 `game-agent-avatars` 私有 Storage bucket 的相对路径。表及 bucket 均不向 `anon` / `authenticated` 开放，客户端只能通过 Edge Function 读取。
  - `game-agent-avatars` 内路径约定为 `<游戏代号>/<拼音>.webp`，例如 `daihao/wei_yan.webp`；上传用 `supabase storage cp --experimental`，本地路径不要带中文，非 ASCII 路径会被 CLI 判为不支持的操作。
  - 发布攻略的 `SharedAgentPickerDialog` 与录制模式导出图片的 `ServiceDialogs` 均提供「新出密探」勾选项；勾选后通过 `yuanassist-api-v3` 的 `list-cloud-game-agents` 读取指定游戏版本的已启用角色，先排除本地 `AgentRepository` 已存在的名字，再将结果作为独立分组展示。无结果时显示“暂无未实装到 App 的新密探”。
  - `list-cloud-game-agents` 由 Edge Function 读取私有表，并为私有头像生成 10 分钟签名 URL；客户端不得直连 `game_agents` 或 `game-agent-avatars`。
  - 招募记录、发布攻略、社区攻略和作业站详情共用云端头像回退：本地 assets 缺失时会按角色名通过 `get-cloud-game-agents` 批量补全。资料与头像缓存于 `files/cloud_game_agents/agents.json` 和同目录头像文件；首次到达后自动刷新，后续离线可复用缓存。社区攻略的命盘、作业站阵容与表头头像均优先回退此缓存。
- 云端招募记录存档：
  - `public.gacha_archives` 存放用户招募记录；结构脚本是 `supabase/gacha_archives_schema.sql`。一行对应一个用户存档，以 `(user_id, archive_id)` 唯一定位。
  - 存档的游戏版本、名称和同步版本为固定字段；每个卡池的保底进度及绝密记录保存于 `pool_records` JSONB 数组，不保存机密或隐密记录，也不冗余存储统计值。
  - 表已启用 RLS，当前不向 `anon` / `authenticated` 开放。同步只能通过 Edge Function 根据用户身份读写；`sync_version` 会随上传递增，预留给后续多端冲突处理。
- 云端卡池目录：
  - `public.gacha_pools` 保存公共卡池元数据；结构脚本是 `supabase/gacha_pools_schema.sql`。字段包含稳定 `pool_id`、游戏版本、名称、UP 密探、封面 URL、`sort_order` 与状态，不能放入用户招募存档。
  - 表为私有且已启用 RLS；用户从招募记录右上角菜单手动“拉取新卡池”，通过 `yuanassist-api-v3` 的 `list-gacha-pools` 读取全部状态记录，写入独立本地 `files/gacha_pool_catalog.json` 缓存，不自动检查。
  - 客户端把内置卡池与本地缓存按 `pool_id` 合并；云端同 ID 更新资料，新 ID 新增，用户存档只引用稳定 ID。目录与所有汇总统计只纳入 `status=active` 的卡池；`archived` / `invalid` 保留原始记录和缓存资料，但不显示也不计入统计。封面继续复用 `GachaPoolCoverStore` 下载到 `filesDir/gacha_pool_covers/`。
  - 新增卡池封面走公共图床：`https://img.scdn.io/api/v1.php` 表单字段 `image`，返回 `img.cdn1.vip` 公开 URL，只写进 `cover_url`，不入 Storage；已上传清单见 `docs/gacha_pool_cover_urls.md`。
  - `gacha_pools` 没有 `countsTowardOffRate` 对应字段，客户端合并时也不覆盖该值；所以无 UP 密探的云端卡池会被按限定池处理（详情页显示“限定寻访”，所有绝密都计入非 UP）。需要“普池不计歪卡”语义时必须做成内置卡池。
  - `sort_order` 越大越靠前，绣衣天下固定为 `100000`；普通卡池以 10 为间隔，可用中间值插入。测试卡池和测试密探不保留在线上或 schema 中。
