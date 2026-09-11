#!/usr/bin/env python3
"""把一个 unified diff 按单个 hunk 拆成多个补丁文件。

用法: python3 tools/split_patch.py <diff文件> <输出目录>
每行输出: <序号>  <该 hunk 含有的关键标识(增删行的前几个词)>
之后可用 `git apply --cached <输出目录>/<序号>.patch` 选择性暂存某个 hunk。
"""
import os
import re
import sys

src, outdir = sys.argv[1], sys.argv[2]
os.makedirs(outdir, exist_ok=True)

lines = open(src, encoding="utf-8").read().splitlines(keepends=True)
header, hunks, cur = [], [], None
for ln in lines:
    if ln.startswith("@@"):
        if cur:
            hunks.append(cur)
        cur = [ln]
    elif cur is not None:
        cur.append(ln)
    else:
        header.append(ln)
if cur:
    hunks.append(cur)

for i, h in enumerate(hunks, 1):
    with open(os.path.join(outdir, f"{i}.patch"), "w", encoding="utf-8", newline="") as f:
        f.writelines(header)
        f.writelines(h)
    # 摘要：列出该 hunk 的 +/- 行里最有辨识度的 token
    toks = []
    for ln in h:
        if ln[:1] in "+-":
            for m in re.findall(r"[A-Za-z0-9_.\-]{6,}", ln[1:]):
                if m not in toks:
                    toks.append(m)
    print(f"{i}: {' '.join(toks[:8])}")
