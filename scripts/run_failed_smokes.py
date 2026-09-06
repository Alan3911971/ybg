#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import subprocess
import sys
from pathlib import Path
import random

BASE = Path("/Users/suguohua/tmp/nas/ybg/ibigou-blindbox/scripts")
SCRIPTS = [
    "smoke_v15_p2.py",
    "smoke_v15_deduct.py",
    "smoke_v15_member.py",
    "smoke_v15_modeb.py",
    "smoke_job.py",
]

results = []
for script in SCRIPTS:
    path = BASE / script
    if not path.exists():
        results.append((script, "SKIP", "文件不存在"))
        continue
    print(f"\n[RUN] {script} ...", flush=True)
    try:
        proc = subprocess.run(
            [sys.executable, str(path)],
            cwd=str(BASE),
            capture_output=True,
            text=True,
            timeout=180,
        )
        output = (proc.stdout or "") + (proc.stderr or "")
        status = "PASS" if proc.returncode == 0 else "FAIL"
        if "全部通过" in output:
            status = "PASS"
        elif "失败" in output or "FAIL" in output:
            status = "FAIL"
        results.append((script, status, output[-800:]))
    except subprocess.TimeoutExpired as e:
        results.append((script, "TIMEOUT", (e.stdout or "")[-500:] + (e.stderr or "")))
    except Exception as e:
        results.append((script, "ERROR", str(e)))

print("\n\n===== 修复后失败脚本汇总 =====")
for script, status, output in results:
    print(f"[{status}] {script}")
    print(output.strip()[-200:])
    print()

# write report
report_path = "/Users/suguohua/tmp/nas/workspace-java/inbox/ibigou/reports/YBG-TEST-001-FIXED-SMOKES-20260826.md"
import os
os.makedirs(os.path.dirname(report_path), exist_ok=True)
with open(report_path, "w", encoding="utf-8") as f:
    f.write("# YBG-TEST-001 失败脚本修复后重跑报告\n\n")
    for script, status, output in results:
        f.write(f"## {script} -> {status}\n\n")
        f.write(f"```\n{output.strip()}\n```\n\n")
print(f"report written: {report_path}")
