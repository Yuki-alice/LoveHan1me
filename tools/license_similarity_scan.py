# -*- coding: utf-8 -*-
"""
许可合规用的「派生面」量化扫描。

对比 LoveHan1me 与上游 Han1meViewer 的 Kotlin 源码，按文件名配对后计算
行级相似度，输出疑似派生的文件清单，作为"提高原创性 / 能否更换协议"的基线。

只读，不修改任何源码。结果写到 .workbuddy/license-scan-report.txt
"""
from __future__ import annotations

import difflib
import os
import re
import sys
from pathlib import Path

UPSTREAM = Path(r"E:\hanime1\references\Han1meViewer-main")
OURS = [Path(r"E:\LoveHan1me\shared\src"), Path(r"E:\LoveHan1me\app\src")]
OUT = Path(r"E:\LoveHan1me\.workbuddy\license-scan-report.txt")

# 归一化时丢掉的行：注释、package/import、纯符号行
NOISE = re.compile(r"^\s*(//|/\*|\*|package\s|import\s|@file:)")
MIN_EFFECTIVE_LINES = 15


def collect_kt(root: Path) -> list[Path]:
    if not root.exists():
        return []
    out = []
    for p in root.rglob("*.kt"):
        # 跳过 build 生成物
        if "build" + os.sep in str(p) or str(p).endswith("build" + os.sep):
            pass
        if f"{os.sep}build{os.sep}" in str(p):
            continue
        out.append(p)
    return out


def normalize(path: Path) -> list[str]:
    """归一化为可比较的代码行序列：去注释/包名/空行/多余空白。"""
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return []
    lines = []
    for raw in text.splitlines():
        s = raw.strip()
        if not s or NOISE.match(s):
            continue
        # 折叠空白
        s = re.sub(r"\s+", " ", s)
        lines.append(s)
    return lines


def similarity(a: list[str], b: list[str]) -> float:
    if not a or not b:
        return 0.0
    # 大文件用 quick_ratio 预筛，避免 O(n^2) 拖太久
    sm = difflib.SequenceMatcher(None, a, b, autojunk=True)
    return sm.ratio()


def main() -> int:
    up_files = collect_kt(UPSTREAM)
    our_files: list[Path] = []
    for root in OURS:
        our_files.extend(collect_kt(root))

    up_by_name: dict[str, list[Path]] = {}
    for f in up_files:
        up_by_name.setdefault(f.name, []).append(f)

    up_norm: dict[Path, list[str]] = {}
    for f in up_files:
        n = normalize(f)
        if len(n) >= MIN_EFFECTIVE_LINES:
            up_norm[f] = n

    results = []
    for our in our_files:
        our_n = normalize(our)
        if len(our_n) < MIN_EFFECTIVE_LINES:
            continue
        best = 0.0
        best_up = None
        for cand in up_by_name.get(our.name, []):
            up_n = up_norm.get(cand)
            if not up_n:
                continue
            r = similarity(up_n, our_n)
            if r > best:
                best, best_up = r, cand
        results.append((best, our, best_up, len(our_n)))

    results.sort(key=lambda x: -x[0])

    buckets = {"high (>=0.75)": [], "mid (0.5-0.75)": [], "low (0.3-0.5)": [], "none (<0.3)": []}
    for r, our, up, n in results:
        if r >= 0.75:
            buckets["high (>=0.75)"].append((r, our, up, n))
        elif r >= 0.5:
            buckets["mid (0.5-0.75)"].append((r, our, up, n))
        elif r >= 0.3:
            buckets["low (0.3-0.5)"].append((r, our, up, n))
        else:
            buckets["none (<0.3)"].append((r, our, up, n))

    lines: list[str] = []
    w = lines.append
    w("LoveHan1me × Han1meViewer 派生面扫描")
    w("=" * 70)
    w("")
    w(f"上游可比文件: {len(up_norm)}   我方可比文件: {len(results)}")
    w("")
    w("⚠️ 读这份报告前必读：本脚本按【文件名】配对。")
    w("   ⇒ 把 Parser.kt 拆成 HomeParser.kt / VideoParser.kt、或给文件改名，")
    w("     都会让它在上游找不到同名文件、相似度直接归零 —— 但代码内容一个字没变。")
    w("   ⇒ 所以「拆文件 / 改名」**不会**提高原创性，只有真重写才会。")
    w("     判定原创性请以内容为准，别拿拆文件刷低这个数字。")
    w("")
    w("分桶统计（按与上游同名文件的最高行级相似度）")
    w("-" * 70)
    for k, v in buckets.items():
        w(f"  {k:<18} {len(v):>4} 个文件")
    total = len(results) or 1
    hi = len(buckets["high (>=0.75)"])
    mid = len(buckets["mid (0.5-0.75)"])
    w("")
    w(f"疑似强派生(>=0.75): {hi} / {total} = {hi * 100 // total}%")
    w(f"含中等相似(>=0.5): {hi + mid} / {total} = {(hi + mid) * 100 // total}%")
    w("")
    w("Top 40 高相似文件（我方 ← 上游）")
    w("-" * 70)
    for idx, (r, our, up, n) in enumerate(results[:40], 1):
        rel_our = str(our).replace(r"E:\LoveHan1me", ".")
        w(f"{idx:>3}. {r * 100:5.1f}%  {rel_our}")
        if up is not None:
            w(f"        ← {up.name}  ({len(up_norm.get(up, []))} 行 vs 我方 {n} 行)")
        else:
            w(f"        ← 上游无同名文件")
    w("")
    w("强派生清单（>=0.75，供重写排序）")
    w("-" * 70)
    for r, our, up, n in buckets["high (>=0.75)"]:
        rel_our = str(our).replace(r"E:\LoveHan1me", ".")
        w(f"  {r * 100:5.1f}%  {rel_our}  (我方 {n} 行)")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"written: {OUT}")
    print(f"high={hi} mid={mid} total={total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
