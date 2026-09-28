# -*- coding: utf-8 -*-
"""定时任务验证（纯轮询版）：等待补偿任务把 11 分钟前抽出的未闭环批次自动闭环。"""
import time
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"


def wallet(phone):
    with urllib.request.urlopen(BASE + f"/api/customer/wallet/{phone}", timeout=30) as resp:
        import json
        return json.loads(resp.read().decode("utf-8")).get("data") or {}


def main():
    phone = "13900000001"
    w0 = wallet(phone)
    pending0 = len(w0.get("pending") or [])
    print(f"[INFO] 初始 pending={pending0} balance={w0.get('balance')}（补偿任务 cron 每 5 分钟触发）")
    if pending0 == 0:
        print("✅ 该批次已被补偿任务闭环（can_use_after_draw 0->1）")
        return
    for i in range(40):  # 最长等 6.5 分钟
        time.sleep(10)
        w = wallet(phone)
        pending = len(w.get("pending") or [])
        if pending != pending0:
            pending0 = pending
            print(f"[{i * 10}s] pending={pending} balance={w.get('balance')}")
        if pending == 0:
            print("✅ 补偿任务已自动闭环（关闭页面场景兜底生效）")
            return
    print("❌ 等待超时（可能错过了 cron 刻度，稍后再查）")
    raise SystemExit(1)


if __name__ == "__main__":
    main()
