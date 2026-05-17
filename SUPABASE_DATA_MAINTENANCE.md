# Supabase 数据导入与修改

当前项目：

- Project URL: `https://ftryfykwzsadgiayquvz.supabase.co`
- 目录根路径：`D:\YuanAssist-master`
- 所有脚本都默认从环境变量 `SUPABASE_SECRET_KEY` 读取密钥

## 1. 准备

PowerShell 先设置密钥：

```powershell
$env:SUPABASE_SECRET_KEY='你的 sb_secret'
```

后面的 `node` 命令都在项目根目录 `D:\YuanAssist-master` 执行。

## 2. 导入根目录 CSV

适用场景：

- 根目录放了一批 `ce7c10b39d790e195d0f3e11*.csv`
- 需要把除 `User` 之外的表批量同步到 Supabase

命令：

```powershell
node tools/import_bmob_csvs_to_supabase.mjs --dir D:\YuanAssist-master
```

这个脚本会自动识别并导入这些表：

- `announcement`
- `issue_feedback`
- `OcrConfig`
- `OcrUsage`
- `strategy_comment`
- `strategy_detail`
- `strategy_favorite`
- `strategy_message`
- `update`

注意：

- `User` 不走这个脚本，要单独导
- 当前规则是“以库结构为准”，本地 CSV 的冗余列会忽略
- `update` 表是 `replace` 模式，会先清空再插入 1 行

## 3. 单独导入 User

适用场景：

- 新的 `__User` CSV 到了
- 只想同步 `User` 表

命令：

```powershell
node tools/import_user_csv_to_supabase.mjs --csv D:\YuanAssist-master\ce7c10b39d790e195d0f3e11bf1945ee__User_1777964656.csv
```

支持两种表头：

- 原始导出：`objectId, avatarUrl, nickname, username, createdAt`
- 整理后：`object_id, avatar_url, nickname, username, created_at`

当前脚本行为：

- 按 `object_id` upsert
- 兼容按 `username` 归并已有用户
- 默认不会乱改 `auth_id`
- `device_id` 之前已经做过一次回填，空值会尽量避免影响业务

## 4. 修改 update 表版本信息

有两种方式。

### 方式 A：先改本地 CSV，再同步远端

本地文件：

- [ce7c10b39d790e195d0f3e11bf1945ee_update_1777964814.csv](/D:/YuanAssist-master/ce7c10b39d790e195d0f3e11bf1945ee_update_1777964814.csv)

改完后执行：

```powershell
node tools/import_bmob_csvs_to_supabase.mjs --dir D:\YuanAssist-master
```

适合“本地 CSV 也要作为下次导入的真实来源”的情况。

### 方式 B：直接改远端 update 记录

命令：

```powershell
node tools/update_supabase_update_record.mjs --version-code 39 --version-name 1.1.5.3a --apk-url https://gitee.com/chunshengyue/yuan-assist-data/releases/download/v1.1.5.3a/YuanAssist1.1.5.3a.apk
```

如果要直接按本地 CSV 的内容覆盖远端：

```powershell
node tools/update_supabase_update_record.mjs --from-csv D:\YuanAssist-master\ce7c10b39d790e195d0f3e11bf1945ee_update_1777964814.csv
```

如果还要顺手改更新说明：

```powershell
node tools/update_supabase_update_record.mjs --version-code 39 --version-name 1.1.5.3a --apk-url https://... --release-notes "这里写新的更新说明"
```

## 5. App 本地版本号也要一起改

只改数据库不够，发包前还要确认 App 自己的版本配置一致：

- [app/build.gradle.kts](/D:/YuanAssist-master/app/build.gradle.kts:17)

要核对这两项：

- `versionCode`
- `versionName`

建议保持：

- 数据库 `update.versionCode` = App `versionCode`
- 数据库 `update.versionName` = App `versionName`

否则就会出现“看起来版本一样，但仍然提示更新”的情况。因为客户端实际比较的是 `versionCode`，不是 `versionName`。

## 6. 攻略 author_id 的特别说明

`strategy_detail` 当前根目录这份 CSV 不带作者指针列，所以：

- 直接导入时不会自动补 `author_id`
- 如果发现 `author_id` 为空，需要额外拿旧库截图、旧表导出或别的作者映射线索来补

也就是说，`author_id` 不是这套根目录 CSV 能完全自动恢复的字段。

## 7. 常用命令汇总

```powershell
$env:SUPABASE_SECRET_KEY='你的 sb_secret'
node tools/import_bmob_csvs_to_supabase.mjs --dir D:\YuanAssist-master
node tools/import_user_csv_to_supabase.mjs --csv D:\YuanAssist-master\ce7c10b39d790e195d0f3e11bf1945ee__User_1777964656.csv
node tools/update_supabase_update_record.mjs --from-csv D:\YuanAssist-master\ce7c10b39d790e195d0f3e11bf1945ee_update_1777964814.csv
```

## 8. 相关脚本

- [tools/import_bmob_csvs_to_supabase.mjs](/D:/YuanAssist-master/tools/import_bmob_csvs_to_supabase.mjs)
- [tools/import_user_csv_to_supabase.mjs](/D:/YuanAssist-master/tools/import_user_csv_to_supabase.mjs)
- [tools/update_supabase_update_record.mjs](/D:/YuanAssist-master/tools/update_supabase_update_record.mjs)
