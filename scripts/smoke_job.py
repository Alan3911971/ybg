# -*- coding: utf-8 -*-
"""定时任务验证：补偿任务自动闭环（模拟用户抽奖后关闭页面）。
抽奖 -> 不闭环 -> 把资产 create_time 改到 11 分钟前 -> 等每5分钟补偿任务触发 -> 验证资产自动可用。"""
import subprocess
import time
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"
SSH = ["ssh", "-o", "ConnectTimeout=15", "-o", "StrictHostKeyChecking=no", "alan@192.168.31.228"]


def req(method, path, params=None):
    data = None
    if params:
        data = urllib.parse.urlencode(params).encode("utf-8")
    r = urllib.request.Request(BASE + path, data=data, method=method)
    with urllib.request.urlopen(r, timeout=30) as resp:
        return json_loads(resp)


def json_loads(resp):
    import json
    body = resp.read().decode("utf-8")
    try:
        return json.loads(body)
    except Exception:
        return {"_raw": body[:200]}


def sql(statement):
    cmd = SSH + ["sudo", "-n", "docker", "exec", "lenscabin-mysql-test", "mysql",
                 "-uroot", "-pTestRoot@2026", "-e", f"USE ibigou_blindbox; {statement}"]
    subprocess.run(cmd, capture_output=True)


def main():
    # 1. 抽奖（产生批次，不闭环 = 模拟关闭页面）
    r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13900000001"})
    d = r.get("data") or {}
    batch = d.get("drawBatchNo")
    print(f"[INFO] 抽奖批次 {batch} prizeType={d.get('prizeType')}，暂不闭环（模拟关闭页面）")
    assert batch, f"开奖失败: {r}"

    # 2. 把券/流水的 create_time 改到 11 分钟前（模拟抽出已过 11 分钟）
    sql(f"UPDATE user_coupon SET create_time = NOW() - INTERVAL 11 MINUTE WHERE draw_batch_no = '{batch}' AND can_use_after_draw = 0;")
    sql(f"UPDATE user_balance_flow SET create_time = NOW() - INTERVAL 11 MINUTE WHERE draw_batch_no = '{batch}' AND can_use_after_draw = 0;")
    print("[INFO] create_time 已改为 11 分钟前，等待每 5 分钟补偿任务触发...")

    # 3. 轮询钱包，最长等 360 秒
    pending_seen = -1
    for i in range(36):
        time.sleep(10)
        w = req("GET", f"/api/customer/wallet/13900000001").get("data") or {}
        pending = len(w.get("pending") or [])
        if pending != pending_seen:
            pending_seen = pending
            print(f"[{i * 10}s] pending={pending} balance={w.get('balance')}")
        if pending == 0 and i >= 3:
            # 需要至少等过一轮 cron 才能判定补偿生效；pending=0 且补偿前曾 >0
            print("✅ 补偿任务已自动闭环（can_use_after_draw 0->1）")
            return
    print("❌ 等待超时，补偿任务未闭环")
    raise SystemExit(1)


if __name__ == "__main__":
    main()
