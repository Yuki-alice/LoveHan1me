#!/usr/bin/env python3
"""按精确文本点 UI 节点（uiautomator dump → bounds 中心 tap）。
用法： P5_ADB=/path/to/adb tap_text.py "Get started"
找不到只打印 NOTFOUND，不报错——调用方据此决定重试或放弃。
假设英文 locale（新 AVD 默认）；其它语言请先保证文本一致，否则如实 NOTFOUND。
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = os.environ.get("P5_ADB", "adb")


def run(*cmd):
    return subprocess.run([ADB, *cmd], capture_output=True, text=True)


def main():
    text = sys.argv[1]
    run("shell", "uiautomator", "dump", "/sdcard/p5ui.xml")
    out = run("shell", "cat", "/sdcard/p5ui.xml").stdout
    start = out.find("<hierarchy")
    if start < 0:
        print("NODUMP")
        return
    try:
        root = ET.fromstring(out[start:])
    except ET.ParseError:
        print("PARSEFAIL")
        return
    for node in root.iter("node"):
        if node.get("text") == text:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds") or "")
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            x, y = (x1 + x2) // 2, (y1 + y2) // 2
            run("shell", "input", "tap", str(x), str(y))
            print(f"TAP {x} {y}")
            return
    print("NOTFOUND")


main()
