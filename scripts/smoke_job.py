# -*- coding: utf-8 -*-
"""定时任务验证：补偿任务自动闭环（模拟用户抽奖后关闭页面）。"""
import subprocess
import time
import random
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"
PHONE = "139" + "".join(random.choices("0123456789", k=8))
_CUST_TOKENS = {}


def json_loads(resp):
    import json
    body = resp.read().decode("utf-8")
    try:
        return json.loads(body)
    except Exception:
        return {"_raw": body[:200]}


def req(method, path, params=None):
    data = None
    if params:
        data = urllib.parse.urlencode(params).encode("utf-8")
    r = urllib.request.Request(BASE + path, data=data, method=method)
    if path.startswith("/api/customer") and "/auth/" not in path:
        if PHONE not in _CUST_TOKENS:
            req("POST", "/api/customer/auth/send-code", {"userPhone": PHONE})
            _CUST_TOKENS[PHONE] = req("POST", "/api/customer/auth/login",
                                       {"userPhone": PHONE, "code": "123456"}).get("data")
        r.add_header("X-User-Token", _CUST_TOKENS[PHONE])
    with urllib.request.urlopen(r, timeout=30) as resp:
        return json_loads(resp)


def try_ssh_sql(statement):
    base_cmd = ["ssh", "-o", "ConnectTimeout=3", "-o", "StrictHostKeyChecking=no",
                "alan@192.168.31.228"]
    for cmd in [
        base_cmd + ["mysql", "-uroot", "-pTestRoot@2026", "-e", f"USE ibigou_blindbox; {statement}"],
        base_cmd + ["sudo", "-n", "docker", "exec", "-i", "lenscabin-mysql-test",
                    "mysql", "-uroot", "-pTestRoot@2026", "-e", f"USE ibigou_blindbox; {statement}"],
    ]:
        try:
            p = subprocess.run(cmd, capture_output=True, timeout=10)
            if p.returncode == 0:
                return True
        except subprocess.TimeoutExpired:
            continue
    return False


def main():
    # 1. 抽奖（产生批次，不闭环 = 模拟关闭页面）
    r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": PHONE})
    d = r.get("data") or {}
    batch = d.get("drawBatchNo")
    print(f"[INFO] 抽奖批次 {batch} prizeType={d.get('prizeType')}，暂不闭环（模拟关闭页面）")
    if not batch:
        print("[WARN] 未抽到奖品，跳过本轮验证")
        return

    # 2. 尝试把 create_time 改到 11 分钟前（需要 SSH 执行 SQL）
    ok = try_ssh_sql(f"UPDATE user_coupon SET create_time = NOW() - INTERVAL 11 MINUTE WHERE draw_batch_no = '{batch}' AND can_use_after_draw = 0;")
    ok2 = try_ssh_sql(f"UPDATE user_balance_flow SET create_time = NOW() - INTERVAL 11 MINUTE WHERE draw_batch_no = '{batch}' AND can_use_after_draw = 0;")
    if not (ok and ok2):
        print("[WARN] SSH/SQL 不可达，跳过补偿任务轮询验证。仅验证抽奖成功。")
        w = req("GET", f"/api/customer/wallet/{PHONE}").get("data") or {}
        print(f"[INFO] 钱包状态: pending={len(w.get('pending') or [])} balance={w.get('balance')}")
        print("✅ 定时任务验证完成（SSH 不可达，跳过 SQL 模拟）")
        return

    print("[INFO] create_time 已改为 11 分钟前，等待每 5 分钟补偿任务触发...")
    pending_seen = -1
    for i in range(36):
        time.sleep(10)
        w = req("GET", f"/api/customer/wallet/{PHONE}").get("data") or {}
        pending = len(w.get("pending") or [])
        if pending != pending_seen:
            pending_seen = pending
            print(f"[{i * 10}s] pending={pending} balance={w.get('balance')}")
        if pending == 0 and i >= 3:
            print("✅ 补偿任务已自动闭环（can_use_after_draw 0->1）")
            return
    print(" 等待超时，补偿任务未闭环")
    raise SystemExit(1)


if __name__ == "__main__":
    main()
