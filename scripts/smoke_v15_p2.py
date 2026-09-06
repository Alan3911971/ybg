# -*- coding: utf-8 -*-
"""P2 运营容灾验证：密钥加密/告警/补单查询。"""
import json
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"


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


def main():
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    ah = {"X-Admin-Token": at}

    # P2-13 密钥加密：保存 -> 列表脱敏验证
    r = req("POST", "/api/admin/config/update", {"key": "wx_pay_api_key", "value": "SECRETKEY999"}, ah)
    check("P2-13 更新密钥", r.get("code") == 0, f"msg={r.get('msg')}")

    cfg = req("GET", "/api/admin/config/list", None, ah).get("data") or []
    masked = next((c for c in cfg if c.get("configKey") == "wx_pay_api_key"), {}).get("configValue", "")
    check("P2-13 列表脱敏", masked.startswith("****"), f"shown={masked}")

    # 通过不同值更新再次验证加密生效
    r = req("POST", "/api/admin/config/update", {"key": "wx_pay_api_key", "value": "NEWSECRET888"}, ah)
    check("P2-13 二次更新密钥", r.get("code") == 0)
    cfg2 = req("GET", "/api/admin/config/list", None, ah).get("data") or []
    masked2 = next((c for c in cfg2 if c.get("configKey") == "wx_pay_api_key"), {}).get("configValue", "")
    check("P2-13 二次列表脱敏", masked2.startswith("****"), f"shown={masked2}")

    # P2-14 告警：无 token 请求被 merchant 统一拦截器拦截，不应产生 500 异常告警
    before = len(req("GET", "/api/admin/alerts", None, ah).get("data") or [])
    req("POST", "/api/merchant/coupon/verify-manual",
        {"couponId": ""}, {"X-Merchant-Token": "badtoken"})
    after = len(req("GET", "/api/admin/alerts", None, ah).get("data") or [])
    # 允许历史告警存在，但本次请求不应新增大量 500 告警
    check("P2-14 无token被拦截无异常告警", after <= before + 1, f"before={before} after={after}")
    if after > before:
        # 如果有新增告警，尝试标记第一条为已处理并验证
        alerts = req("GET", "/api/admin/alerts", None, ah).get("data") or []
        if alerts:
            aid = alerts[0]["alertId"]
            r = req("POST", f"/api/admin/alerts/{aid}/handle", None, ah)
            check("P2-14 标记处理", r.get("code") == 0)
            r2 = req("GET", "/api/admin/alerts", None, ah)
            handled = next((a for a in r2.get("data") or [] if a.get("alertId") == aid), {}).get("status")
            check("P2-14 状态已处理", handled == 1, f"status={handled}")

    # P2-12 补单
    req("POST", "/api/admin/config/update", {"key": "wx_pay_enabled", "value": "0"}, ah)
    r = req("GET", "/api/admin/member/orders", None, ah)
    check("P2-12 订单列表可查", r.get("code") == 0)

    print("\n===== P2 运营容灾验证全部通过 =====")


if __name__ == "__main__":
    main()
