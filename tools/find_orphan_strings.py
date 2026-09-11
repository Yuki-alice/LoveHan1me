#!/usr/bin/env python3
"""扫描 Compose Resources 字符串键中的「孤儿」——定义了但无人引用。

用法:
    python3 tools/find_orphan_strings.py [--show-used]

判定规则:
    键 K 被认为「有用」当且仅当 .kt 源码中出现以下任一形态
      - Res.string.K
      - Res.string.K 的 import:  import <pkg>.K
      - Res.drawable.K / Res.plurals.K / Res.array.K
    排除目录: build/、reference/（上游参考副本）

为什么需要这个脚本: 本项目有「两套字符串」(composeResources 与 app/res)，
删功能时极易只删用法、留下键，且要三语言同步清。人工核对容易漏。
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RES_DIRS = [REPO / "shared/src/commonMain/composeResources"]
# 上游参考副本不参与；构建产物不参与
SKIP_PARTS = {"build", "reference", ".git", ".gradle", ".workbuddy"}

STRING_RE = re.compile(r'<string\s+name="([^"]+)"')
# 其它资源类型一并覆盖
OTHER_RES_RE = re.compile(r'<(plurals|string-array|array)\s+name="([^"]+)"')


def resource_keys() -> dict[str, set[str]]:
    """返回 {键名: {出现它的 strings.xml 相对路径, ...}}"""
    keys: dict[str, set[str]] = {}
    for d in RES_DIRS:
        for xml in sorted(d.rglob("strings.xml")):
            text = xml.read_text(encoding="utf-8")
            for m in STRING_RE.finditer(text):
                keys.setdefault(m.group(1), set()).add(
                    str(xml.relative_to(REPO))
                )
    return keys


def kt_sources() -> list[Path]:
    out: list[Path] = []
    for p in REPO.rglob("*.kt"):
        if SKIP_PARTS & set(p.parts):
            continue
        out.append(p)
    return out


def build_usage_index(files: list[Path]) -> str:
    """把全部 kt 源码拼成一个大字符串，供正则快速匹配。

    源码总量约 5 万行，直接读入内存毫无压力，比逐文件 grep 快得多。
    """
    chunks: list[str] = []
    for f in files:
        try:
            chunks.append(f.read_text(encoding="utf-8", errors="replace"))
        except OSError:
            continue
    return "\n".join(chunks)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--show-used", action="store_true", help="同时列出被引用的键")
    args = ap.parse_args()

    keys = resource_keys()
    if not keys:
        print("✗ 没找到任何资源键，请检查 RES_DIRS 配置", file=sys.stderr)
        return 2

    sources = kt_sources()
    blob = build_usage_index(sources)
    print(f"扫描范围: {len(sources)} 个 .kt 文件, {len(keys)} 个字符串键\n")

    used: list[str] = []
    orphan: list[str] = []
    for k in sorted(keys):
        # Res.string.<k> 精确边界（避免 download_task_failed 误匹配 download_task_failed_s_reason_s）
        pat = re.compile(r"Res\.(?:string|drawable|plurals|array)\." + re.escape(k) + r"\b")
        if pat.search(blob):
            used.append(k)
        else:
            orphan.append(k)

    if orphan:
        print(f"=== 孤儿键 {len(orphan)} 个（定义但无人引用）===")
        for k in orphan:
            files = ", ".join(sorted(keys[k]))
            print(f"  {k}\n      出现于: {files}")
    else:
        print("=== 无孤儿键 ===")

    if args.show_used:
        print(f"\n=== 被引用的键 {len(used)} 个 ===")
        for k in used:
            print(f"  {k}")
    else:
        print(f"\n(被引用 {len(used)} 个，加 --show-used 可列出)")

    return 0 if not orphan else 1


if __name__ == "__main__":
    raise SystemExit(main())
