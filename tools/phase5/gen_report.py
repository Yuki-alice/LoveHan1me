#!/usr/bin/env python3
"""汇总 $OUT/*.result 成 7.5 记录表行（markdown），打到 stdout。
每个 .result 是 KEY=VALUE 行：要求 ITEM / STATUS / DETAIL。
用法： PHASE5_OUT=/tmp/phase5 gen_report.py
"""
import glob
import os

out = os.environ.get("PHASE5_OUT", "/tmp/phase5")
rows = []
for path in sorted(glob.glob(os.path.join(out, "*.result"))):
    kv = {}
    for line in open(path, encoding="utf-8"):
        if "=" in line:
            k, v = line.strip().split("=", 1)
            kv[k] = v
    rows.append((path.split("/")[-1], kv.get("ITEM", path), kv.get("STATUS", "?"), kv.get("DETAIL", "")))

print("| 脚本 | 项 | 状态 | 细节 |")
print("|---|---|---|---|")
for name, item, status, detail in rows:
    print(f"| {name} | {item} | {status} | {detail} |")
