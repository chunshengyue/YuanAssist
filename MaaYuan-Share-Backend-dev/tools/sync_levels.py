"""
同步飞书中的“关卡数据”到本地的 levels.json。

- 数据源：飞书多维表（默认使用 feishu_common.TABLES 中的 levels 表，可通过
  环境变量 FEISHU_LEVELS_TABLE_KEY 覆盖为其它 table key）。
- 输出：resource/levels.json
"""

import os
import re
from typing import Any

import feishu_common as fc

TABLE_KEY = os.getenv("FEISHU_LEVELS_TABLE_KEY", "levels")


def _get_text(value: Any) -> str:
    """宽松获取字段文本，兼容 str / list[dict[text]] / list[str] / None。"""
    if isinstance(value, list):
        if value and isinstance(value[0], dict):
            return str(value[0].get("text", "")).strip()
        return ", ".join(str(v) for v in value if v is not None).strip()
    if isinstance(value, dict):
        return str(value.get("text", "")).strip()
    return str(value or "").strip()


def _pick(fields: dict, *names: str) -> str:
    """按候选字段名依次取第一个非空值。"""
    for n in names:
        t = _get_text(fields.get(n))
        if t:
            return t
    return ""


def _to_int(value: Any) -> int:
    try:
        return int(str(value).strip())
    except Exception:
        return 0


def _normalize_title(value: str) -> str:
    """
    复刻 resource/levels.json 的命名规则：
    - YYYY年M月 -> YYYY年MM月（M<10 时补零）
    - M月D日 -> M-D（不补零）
    """
    s = (value or "").strip()
    if not s:
        return ""

    m = re.fullmatch(r"(\d{4})年(\d{1,2})月", s)
    if m:
        year, month = m.groups()
        return f"{year}年{int(month):02d}月"

    m = re.fullmatch(r"(\d{1,2})月(\d{1,2})日", s)
    if m:
        month, day = m.groups()
        return f"{int(month)}-{int(day)}"

    return s


def transform_levels(records: list) -> list[dict[str, str]]:
    """将飞书记录转换为 levels.json 的列表结构。"""
    # 先按“文本”(序号)字段排序，确保输出顺序与现有文件一致
    ordered = sorted(
        records,
        key=lambda r: _to_int(_get_text((r.get("fields", {}) or {}).get("文本"))),
    )

    data: list[dict[str, str]] = []
    for r in ordered:
        f = r.get("fields", {}) or {}

        cat_one = _pick(f, "关卡分类", "catOne", "分类1", "一级分类", "类别1")
        raw_name = _pick(f, "关卡名", "name", "名称")
        display_name = _pick(f, "显示名称", "name", "关卡名", "名称") or raw_name
        note = _pick(f, "备注(洞窟用)", "备注", "catThree", "分类3", "三级分类", "类别3")

        stage_id = _pick(f, "stageId", "StageId", "关卡StageId", "stage_id")
        level_id = _pick(f, "levelId", "LevelId", "关卡ID", "level_id") or stage_id

        # 兰台：catTwo 用期数（关卡名），catThree 用备注（阵型）
        if cat_one == "兰台":
            cat_two = _normalize_title(raw_name or display_name)
        else:
            cat_two = _normalize_title(display_name or raw_name)

        name = _normalize_title(display_name or raw_name or stage_id)
        cat_three = note or "无"

        data.append(
            {
                "catOne": cat_one,
                "catTwo": cat_two,
                "catThree": cat_three,
                "name": name,
                "levelId": level_id,
                "stageId": stage_id,
            }
        )

    return data


def main():
    """主执行函数"""
    token = fc.get_tenant_token()
    records = fc.fetch_records(TABLE_KEY, token)
    levels_data = transform_levels(records)
    fc.write_json_file(levels_data, "levels.json")


if __name__ == "__main__":
    main()
