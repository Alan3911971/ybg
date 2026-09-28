#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""运行所有 YBG 冒烟脚本，带超时。"""
import subprocess
import os
import sys
from pathlib import Path

BASE = Path("/Users/suguohua/tmp/nas/ybg/ibigou-blindbox/scripts")
SCRIPTS = [
    "smoke_test.py",
    "smoke_v15_p0.py",
    "smoke_v15_p1.py",
    "smoke_v15_p2.py",
    "smoke_v15_deduct.py",
    "smoke_v15_manualverify.py",
    "smoke_v15_member.py",
    "smoke_v15_announce.py",
    "smoke_v15_modeb.py",
    "smoke_v15_return.py",
    "smoke_v15_revoke.py",
    "smoke_job.py",
]

results = []
for script in SCRIPTS:
    path = BASE / script
    if not path.exists():
        results.append((script, "SKIP", "文件不存在"))
        continue
    print(f"[RUN] {script} ...", flush=True)
    try:
        proc = subprocess.run(
            [sys.executable, str(path)],
            cwd=str(BASE),
            capture_output=True,
            text=True,
            timeout=120,
        )
        output = (proc.stdout or "") + (proc.stderr or "")
        status = "PASS" if proc.returncode == 0 else "FAIL"
        if "全部通过" in output:
            status = "PASS"
        elif "失败" in output or "FAIL" in output:
            status = "FAIL"
        results.append((script, status, output[-500:]))
    except subprocess.TimeoutExpired as e:
        results.append((script, "TIMEOUT", (e.stdout or "")[-500:] + (e.stderr or "")))
    except Exception as e:
        results.append((script, "ERROR", str(e)))

# print summary
print("\n===== YBG 冒烟脚本汇总 =====")
for script, status, output in results:
    print(f"[{status}] {script}")
    print(output.strip()[-200:])
    print()

# write report
report_path = "/Users/suguohua/tmp/nas/workspace-java/inbox/ibigou/reports/YBG-TEST-001-SMOKE-RUN-20260826.md"
os.makedirs(os.path.dirname(report_path), exist_ok=True)
with open(report_path, "w", encoding="utf-8") as f:
    f.write("# YBG-TEST-001 冒烟脚本批量执行报告\n\n")
    for script, status, output in results:
        f.write(f"## {script} -> {status}\n\n")
        f.write(f"```\n{output.strip()}\n```\n\n")
print(f"report written: {report_path}")
