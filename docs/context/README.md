# 项目文档索引

> 这是 `docs/context/` 的入口。**先读本文件，再按路由表打开 1-2 份，不要一次性全读。**
>
> 旧版 `docs/project_context.md` 是单文件 14.3K token，每次都得整份加载；
> 现在拆成 8 份，按路由通常只需读 1-2 份（约 1-5K token）。`modules.md` 仅按需查，不必通读。

## 按需要读

| 我要做什么 | 读这份 | 体量 | 备注 |
|---|---|---|---|
| 定位某功能 / 某症状对应哪个文件 | `entrypoints.md` | ~1080 token | 改动频率最高，优先维护 |
| 已知类名找文件，或浏览某包有哪些类 | `modules.md` | ~4678 token | 自动生成，勿手改 |
| 项目结构、可复用模块、资产与脚本组织规则 | `architecture.md` | ~2500 token | 低频率 |
| 改 UI、加页面、加弹窗 | `ui-guidelines.md` | ~1793 token | 低频率 |
| 改业务功能前确认现行规则 | `features.md` | ~4897 token | 中频率 |
| 改战斗版 / 日常版悬浮窗 | `overlays.md` | ~1397 token | 中频率 |
| 动手前确认硬约束，或排查踩坑 | `constraints.md` | ~1125 token | 低频率 |
| 碰云端表 / Storage / RLS / 线上数据 | `supabase.md` | ~1978 token | 线上变更前必读 |

体量为粗略估算，仅供判断"要不要现在就打开"。

## 不在本目录的其他文档

- `docs/superpowers/plans/`、`docs/superpowers/specs/` —— 历史计划与设计稿，属于过程记录，**不是现行事实来源**
- `docs/test_tool_context.md` —— 测试工具的能力、技术栈与计划
- `docs/gacha_pool_cover_urls.md` / `docs/gacha_pool_catalog.md` —— 卡池封面 URL 与完整清单
- `docs/table_ocr_integration_plan.md` —— 表格 OCR 接入计划
- 根目录 `AGENTS.md` —— 规则与流程约束（不是项目事实）
- `app/AGENTS.md` —— app 模块的专有约定（构建命令、包结构、坐标基准提醒等）

## 维护

- 改动落在哪一类，就更新哪一份，不要往 `entrypoints.md` 里塞业务细节
- `entrypoints.md` 是使用频率最高的一份，新增入口/排查路径时优先补它
- 改了 Kotlin 顶层声明后重新生成索引：
  ```powershell
  python tools/gen_modules_index.py
  ```
- 根 `AGENTS.md` 与 `app/AGENTS.md` 写“规则”，本目录写“项目事实”，两者不要互相搬运
- 注意：两份 `AGENTS.md` 都在 `.gitignore` 里，只在本机生效，不会进版本库
- 只保留当前有效的信息，不要堆过程性记录
- 本目录各文件的行文约定：每条事实一行，尽量写成"结论 + 文件路径/类名"，不要写推理过程
