# -*- coding: utf-8 -*-
"""生成 docs/context/modules.md —— Kotlin 类/文件索引。

用法（在仓库根目录执行）：
    python tools/gen_modules_index.py

扫描 app/src/main/java/com/example/yuanassist 下的 .kt 文件，
按包目录列出每个文件的顶层「类型 + 函数」；只有顶层常量、没有任何类型/函数的
文件（如纯常量表）回退为列出其常量名，避免文件在索引里消失。

顶层判定用花括号深度，不依赖缩进，因此整文件被统一缩进也能正确识别。
"""
import io, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC  = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "yuanassist")
OUT  = os.path.join(ROOT, "docs", "context", "modules.md")

MOD = (r"(?:public|internal|private|protected|abstract|open|sealed|data|enum|annotation"
       r"|value|inline|suspend|operator|infix|external|expect|actual|lateinit|const"
       r"|tailrec|override|companion)\s+")
DECL = re.compile(r"^(?:@[A-Za-z_][\w.]*\s*)*(?:(private)\s+)?(?:" + MOD + r")*"
                  r"(class|object|interface|fun|typealias|val|var)\s+([A-Za-z_]\w*)")

TYPE_KINDS = ("class", "object", "interface", "typealias")


def code_only(line, in_block):
    """去掉行注释、块注释与字符串字面量，返回 (剩余代码, 新的 in_block 状态)。"""
    out, i = [], 0
    while i < len(line):
        if in_block:
            j = line.find("*/", i)
            if j < 0:
                return "".join(out), True
            i, in_block = j + 2, False
            continue
        if line.startswith("//", i):
            break
        if line.startswith("/*", i):
            in_block, i = True, i + 2
            continue
        c = line[i]
        if c in "\"'":
            i += 1
            while i < len(line):
                if line[i] == "\\":
                    i += 2
                    continue
                if line[i] == c:
                    break
                i += 1
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out), in_block


rows = {}
for dirpath, dirnames, filenames in os.walk(SRC):
    dirnames[:] = sorted(d for d in dirnames if d != "build")
    for fn in sorted(filenames):
        if not fn.endswith(".kt"):
            continue
        path = os.path.join(dirpath, fn)
        rel = os.path.relpath(dirpath, SRC).replace("\\", "/") or "."
        main, consts, depth, in_block = [], [], 0, False
        with io.open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                code, in_block = code_only(line, in_block)
                if depth == 0:
                    m = DECL.match(line.strip())
                    if m:
                        is_priv, kind, name = m.group(1), m.group(2), m.group(3)
                        if kind in TYPE_KINDS or kind == "fun":
                            main.append(name)
                        elif not is_priv:
                            consts.append(name)
                depth += code.count("{") - code.count("}")
                if depth < 0:
                    depth = 0
        rows.setdefault(rel, []).append((fn, main, consts))

HEAD = """# 类与文件索引

> 自动生成的符号索引：按包目录列出每个 Kotlin 文件的顶层类型与函数，用于「已知类名，找文件路径」。
>
> - 只列顶层「类型 + 函数」；顶层 `val`/`var` 常量只在文件没有类型/函数时才列出
> - 用 `rg <类名>` 定位通常更快；本文件适合不知道确切类名、只知道大致归属时浏览
> - **按需查，不必通读**
> - 重新生成（在仓库根目录）：`python tools/gen_modules_index.py`
>
> 导航：[README](README.md) · [entrypoints](entrypoints.md) · [architecture](architecture.md)

---

"""

out = [HEAD]
n_files = n_decls = 0
for d in sorted(rows, key=lambda x: (x == ".", x)):
    items = rows[d]
    n_files += len(items)
    n_decls += sum(len(m) for _, m, _ in items) + sum(len(c) for _, m, c in items if not m)
    out.append("## `%s/`  (%d 个文件)\n" % (d, len(items)))
    for fn, main, consts in items:
        names = main if main else consts
        if names:
            shown = ", ".join("`%s`" % n for n in names[:40])
            if len(names) > 40:
                shown += " …（另 %d 项）" % (len(names) - 40)
            suffix = "（仅常量）" if not main else ""
            out.append("- `%s`%s — %s" % (fn, suffix, shown))
        else:
            out.append("- `%s` — （无顶层声明）" % fn)
    out.append("")

txt = "\n".join(out).rstrip("\n") + "\n"
with io.open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write(txt)

print("packages=%d files=%d decls=%d chars=%d" % (len(rows), n_files, n_decls, len(txt)))
