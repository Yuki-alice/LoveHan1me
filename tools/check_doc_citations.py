#!/usr/bin/env python3
"""文档引用校验：让 AGENTS.md 里那些"必须可校验"的规矩变成可执行的。

检查三件事：
1. 文档里出现的 `path/to/file.kt:123` 引用，文件必须存在、行号必须在范围内。
2. docs/evidence/ 每条取证必须四件套齐全（取证日/重跑/环境/失效条件），过期只告警。
3. docs/plan/ 同时只允许一份活计划；docs/decisions.md 每条应带失效条件。

引用失效 / 结构违规 = 退出码 1；过期 = 只打印告警。
"""

import datetime
import os
import re
import subprocess
import sys

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                      capture_output=True, text=True).stdout.strip() or os.getcwd()

# 裸文件名也能解析（靠 git 索引），但同名多份就报错，逼你写全路径。
CITATION = re.compile(r"([\w./\-]+\.\w{1,12}):(\d{1,5})(?:-(\d{1,5}))?")
EVIDENCE_FIELDS = ["取证日", "重跑", "环境", "失效条件"]
EVIDENCE_TTL_DAYS = 180

tracked = subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split("\n")
index = {}
for path in tracked:
    if path:
        index.setdefault(os.path.basename(path), []).append(path)
by_path = {p: p for p in tracked}


def resolve(raw):
    """返回 (真实路径, 告警)。找不到返回 (None, ...)。"""
    if raw.startswith("reference/"):
        return (raw if os.path.exists(os.path.join(ROOT, raw)) else None), "本机引用（reference/ 不入库）"
    if raw in by_path or os.path.exists(os.path.join(ROOT, raw)):
        return raw, None
    hits = index.get(os.path.basename(raw))
    if hits and len(hits) == 1:
        return hits[0], None
    if hits:
        return None, "裸文件名有 %d 个同名文件，必须写全路径" % len(hits)
    return None, None


def line_count(path):
    with open(os.path.join(ROOT, path), "rb") as fh:
        return sum(1 for _ in fh)


def markdown_files():
    out = []
    for base in ("AGENTS.md", "README.md"):
        if os.path.exists(os.path.join(ROOT, base)):
            out.append(base)
    docs = os.path.join(ROOT, "docs")
    for dirpath, _dirs, files in os.walk(docs):
        for name in sorted(files):
            if name.endswith(".md") and not name.startswith("_"):
                out.append(os.path.relpath(os.path.join(dirpath, name), ROOT))
    return out


errors, warnings = [], []

# 1) 引用校验
for doc in markdown_files():
    text = open(os.path.join(ROOT, doc), encoding="utf-8").read()
    for raw, start, end in CITATION.findall(text):
        path, note = resolve(raw)
        if path is None:
            errors.append("%s: 引用 %r 找不到文件（%s）" % (doc, raw, note or "未入库"))
            continue
        if note:
            warnings.append("%s: %s → %s" % (doc, raw, note))
        try:
            total = line_count(path)
        except OSError as exc:
            errors.append("%s: 读 %s 失败 %s" % (doc, path, exc))
            continue
        hi = int(end or start)
        if int(start) > total or hi > total:
            errors.append("%s: %s 行号超出范围（文件只有 %d 行）" % (doc, raw, total))

# 2) 取证条目结构
ev_dir = os.path.join(ROOT, "docs", "evidence")
today = datetime.date.today()
if os.path.isdir(ev_dir):
    for name in sorted(os.listdir(ev_dir)):
        if not name.endswith(".md") or name.startswith("_"):
            continue
        rel = os.path.join("docs/evidence", name)
        body = open(os.path.join(ROOT, rel), encoding="utf-8").read()
        for block in re.split(r"\n## ", body)[1:]:
            title = block.split("\n", 1)[0].strip()
            missing = [f for f in EVIDENCE_FIELDS if ("%s：" % f) not in block and ("%s:" % f) not in block]
            if missing:
                errors.append("%s「%s」缺字段：%s" % (rel, title, "、".join(missing)))
            for yyyy, mm, dd in re.findall(r"取证日：(\d{4})-(\d{2})-(\d{2})", block):
                aged = (today - datetime.date(int(yyyy), int(mm), int(dd))).days
                if aged > EVIDENCE_TTL_DAYS:
                    warnings.append("%s「%s」已过期 %d 天，建议重跑" % (rel, title, aged))

# 3) 单活计划位
plan_dir = os.path.join(ROOT, "docs", "plan")
if os.path.isdir(plan_dir):
    live = [n for n in sorted(os.listdir(plan_dir)) if n.endswith(".md") and not n.startswith("_")]
    if len(live) > 1:
        errors.append("docs/plan/ 有 %d 份文件，只允许一份活计划：%s" % (len(live), "、".join(live)))

# 4) decisions.md 每条带失效条件（一条 bullet 可以折行，按 bullet 整块判断）
dec = os.path.join(ROOT, "docs", "decisions.md")
if os.path.exists(dec):
    bullets, current = [], None
    for line in open(dec, encoding="utf-8"):
        if line.startswith("- "):
            if current:
                bullets.append(current)
            current = line
        elif current is not None and line.startswith(("  ", "\t")):
            current += line
        elif current:
            bullets.append(current)
            current = None
    if current:
        bullets.append(current)
    for bullet in bullets:
        head = bullet.strip()[:60]
        if "失效条件" not in bullet:
            errors.append("decisions.md 条目缺「失效条件」：%s" % head)
        if "重认" not in bullet:
            errors.append("decisions.md 条目缺「重认日期」：%s" % head)

for w in warnings:
    print("warn  %s" % w)
for e in errors:
    print("ERROR %s" % e)
print("checked %d docs: %d errors, %d warnings" % (len(markdown_files()), len(errors), len(warnings)))
sys.exit(1 if errors else 0)
