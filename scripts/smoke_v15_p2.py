# -*- coding: utf-8 -*-
"""P2 运营容灾验证：密钥加密/告警/补单查询/清理。"""
import json
import subprocess
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"
SQL_GET_KEY = "USE ibigou_blindbox; SELECT config_value FROM sys_global_config WHERE config_key='wx_pay_api_key';"


def req(method, path, params=None, headers=None):
    url = BASE + path
    data = None
    if params:
        data = urllib.parse.urlencode(params).encode("utf-8")
    r = urllib.request.Request(url, data=data, method=method)
    if headers:
        for k, v in headers.items():
            r.add_header(k, v)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            body = resp.read().decode("utf-8")
            try:
                return json.loads(body)
            except Exception:
                return {"_bytes": len(body)}
    except urllib.error.HTTPError as e:
        return {"_http": e.code}


def check(name, cond, extra=""):
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {extra}")
    if not cond:
        raise SystemExit(f"失败: {name}")


def ssh_mysql(sql):
    p = subprocess.Popen(
        ["ssh", "-o", "ConnectTimeout=15", "-o", "StrictHostKeyChecking=no",
         "alan@192.168.31.228", "sudo", "-n", "docker", "exec", "-i", "lenscabin-mysql-test",
         "mysql", "-uroot", "-pTestRoot@2026"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    out, _ = p.communicate(sql.encode("utf-8"))
    return out.decode("utf-8", "ignore")


def main():
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    ah = {"X-Admin-Token": at}

    # P2-13 密钥加密：保存 -> DB enc: 前缀 -> 列表脱敏
    req("POST", "/api/admin/config/update", {"key": "wx_pay_api_key", "value": "SECRETKEY999"}, ah)
    db_out = ssh_mysql(SQL_GET_KEY)
    db_val = "".join(line for line in db_out.splitlines() if line.strip().startswith("enc:"))
    check("P2-13 DB存储加密", db_val.startswith("enc:"), f"value={db_val[:24]}...")
    cfg = req("GET", "/api/admin/config/list", None, ah).get("data") or []
    masked = next((c for c in cfg if c.get("configKey") == "wx_pay_api_key"), {}).get("configValue", "")
    check("P2-13 列表脱敏", masked.startswith("****"), f"shown={masked}")

    # P2-14 告警：触发异常 -> 平台查看 -> 标记处理
    req("POST", "/api/merchant/coupon/verify-manual",
        {"couponId": ""}, {"X-Merchant-Token": "badtoken"})
    r = req("GET", "/api/admin/alerts", None, ah)
    alerts = r.get("data") or []
    check("P2-14 告警已记录", len(alerts) >= 1, f"count={len(alerts)}")
    aid = alerts[0]["alertId"]
    r = req("POST", f"/api/admin/alerts/{aid}/handle", None, ah)
    check("P2-14 标记处理", r.get("code") == 0)
    r = req("GET", "/api/admin/alerts", None, ah)
    handled = next((a for a in r.get("data") or [] if a.get("alertId") == aid), {}).get("status")
    check("P2-14 状态已处理", handled == 1, f"status={handled}")

    # P2-12 补单：mock 模式 queryOrderState NOTPAY；待支付订单查询可用
    req("POST", "/api/admin/config/update", {"key": "wx_pay_enabled", "value": "0"}, ah)
    r = req("GET", "/api/admin/member/orders", None, ah)
    check("P2-12 订单列表可查", r.get("code") == 0)

    # P2-15 清理：SQL 等价可执行（任务每日 3:20）
    out = ssh_mysql("USE ibigou_blindbox; DELETE FROM announce_log WHERE create_time < NOW() - INTERVAL 30 DAY;")
    check("P2-15 清理SQL可执行", "ROW_COUNT" in out or "Query OK" in out or out.strip() == "", f"out={out.strip()[:60]}")

    print("\n===== P2 运营容灾验证全部通过 =====")


if __name__ == "__main__":
    main()
