from __future__ import annotations

import json
import locale
from pathlib import Path
import subprocess
import tempfile
from typing import Any

from .cases import StrategyCase


DEFAULT_SUPABASE_BIN = Path("node_modules/@supabase/cli-windows-x64/bin/supabase.exe")


class SupabaseCliError(RuntimeError):
    pass


def fetch_strategy_rows(
    *,
    limit: int = 50,
    supabase_bin: Path = DEFAULT_SUPABASE_BIN,
    project_root: Path = Path("."),
    visible_only: bool = True,
) -> list[dict[str, Any]]:
    sql = build_strategy_query(limit=limit, visible_only=visible_only)
    return run_linked_json_query(sql, supabase_bin=supabase_bin, project_root=project_root)


def fetch_strategy_cases(
    *,
    limit: int = 50,
    supabase_bin: Path = DEFAULT_SUPABASE_BIN,
    project_root: Path = Path("."),
    visible_only: bool = True,
) -> list[StrategyCase]:
    rows = fetch_strategy_rows(
        limit=limit,
        supabase_bin=supabase_bin,
        project_root=project_root,
        visible_only=visible_only,
    )
    return [StrategyCase.from_row(row) for row in rows]


def build_strategy_query(*, limit: int, visible_only: bool = True) -> str:
    bounded_limit = max(1, min(limit, 1000))
    visible_clause = 'and "visible" = 1' if visible_only else ""
    return f"""
select
  "objectId",
  "title",
  "scriptContent",
  "instructions",
  "visible",
  "createdAt",
  "updatedAt"
from public.strategy_detail
where coalesce("scriptContent", '') <> ''
  {visible_clause}
order by "createdAt" desc nulls last
limit {bounded_limit};
""".strip()


def run_linked_json_query(
    sql: str,
    *,
    supabase_bin: Path = DEFAULT_SUPABASE_BIN,
    project_root: Path = Path("."),
) -> list[dict[str, Any]]:
    resolved_root = project_root.resolve()
    resolved_bin = (resolved_root / supabase_bin).resolve() if not supabase_bin.is_absolute() else supabase_bin
    if not resolved_bin.exists():
        raise SupabaseCliError(f"未找到 Supabase CLI：{resolved_bin}")

    sql_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False, encoding="utf-8") as handle:
            handle.write(sql)
            sql_path = Path(handle.name)

        completed = subprocess.run(
            [
                str(resolved_bin),
                "db",
                "query",
                "--linked",
                "-f",
                str(sql_path),
                "-o",
                "json",
            ],
            cwd=resolved_root,
            capture_output=True,
            check=False,
        )
    finally:
        if sql_path and sql_path.exists():
            sql_path.unlink()

    if completed.returncode != 0:
        detail = (decode_process_output(completed.stderr) or decode_process_output(completed.stdout)).strip()
        raise SupabaseCliError(f"Supabase CLI 查询失败：{detail}")

    raw = decode_process_output(completed.stdout).strip()
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SupabaseCliError(f"Supabase CLI JSON 输出解析失败：{exc}\n{raw[:500]}") from exc

    if isinstance(parsed, list):
        return ensure_dict_rows(parsed)
    if isinstance(parsed, dict):
        for key in ("data", "rows", "result"):
            value = parsed.get(key)
            if isinstance(value, list):
                return ensure_dict_rows(value)
    raise SupabaseCliError(f"Supabase CLI 输出格式不符合预期：{type(parsed).__name__}")


def ensure_dict_rows(rows: list[Any]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for row in rows:
        if isinstance(row, dict):
            result.append(row)
    return result


def decode_process_output(data: bytes) -> str:
    if not data:
        return ""
    encodings = ["utf-8-sig", "utf-8", locale.getpreferredencoding(False), "gb18030"]
    seen: set[str] = set()
    for encoding in encodings:
        normalized = encoding.lower()
        if normalized in seen:
            continue
        seen.add(normalized)
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="replace")
