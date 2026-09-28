# -*- coding: utf-8 -*-
"""V1.5 P2 播报事件验证：开奖/下单/退款生成话术 + poll 增量。"""
import json
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"


_CUST_TOKENS = {}

def _cust_token(phone):
    if phone not in _CUST_TOKENS:
        req("POST", "/api/customer/auth/send-code", {"userPhone": phone})
        _CUST_TOKENS[phone] = req("POST", "/api/customer/auth/login",
                                  {"userPhone": phone, "code": "123456"}).get("data")
    return _CUST_TOKENS[phone]

def req(method, path, params=None, headers=None):
    url = BASE + path
    data = None
    if params:
        data = urllib.parse.urlencode(params).encode("utf-8")
    r = urllib.request.Request(url, data=data, method=method)
    if path.startswith("/api/customer") and "/auth/" not in path:
        _ph = (params or {}).get("userPhone")
        if not _ph and "/wallet/" in path:
            _ph = path.split("/wallet/")[1].split("/")[0]
        if not _ph and "/ibigou/orders/" in path:
            _ph = path.split("/ibigou/orders/")[1].split("/")[0]
        if not _ph and "/qr/dynamic/" in path:
            _ph = path.split("/qr/dynamic/")[1].split("/")[0]
        if not _ph and _CUST_TOKENS:
            _ph = list(_CUST_TOKENS.keys())[-1]
        if _ph:
            headers = dict(headers or {})
            headers.setdefault("X-User-Token", _cust_token(_ph))
    if headers:
        for k, v in headers.items():
            r.add_header(k, v)
    with urllib.request.urlopen(r, timeout=30) as resp:
        body = resp.read().decode("utf-8")
        try:
            return json.loads(body)
        except Exception:
            return {"_raw": body[:200]}


def check(name, cond, extra=""):
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {extra}")
    if not cond:
        raise SystemExit(f"失败: {name}")


def main():
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "AnnounceTest", "loginAccount": "m001", "loginPwd": "smoke123"},
        {"X-Admin-Token": at})
    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    req("POST", "/api/merchant/config/deduct-config", {"balanceDeductPercent": "50", "dailyDeductLimit": "30"}, h)
    req("POST", "/api/merchant/config/weights",
        {"privatePoolWeight": "100", "publicPoolWeight": "0",
         "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, h)
    req("POST", "/api/merchant/config/prize-pools", {"prizeType": "3", "prizeValue": "50.00", "weight": "100"}, h)

    # 1. 开奖 → 播报（含第X位/余额/比例/今日剩余额度）
    for _ in range(20):
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13800000100"})
        d = r.get("data") or {}
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": "13800000100", "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
        if d.get("prizeType") == 3:
            break

    # 2. 下单 → 订单完成播报
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": "13800000100", "merchantNo": "M001", "orderAmount": "100.00", "paidAmount": "70.00"})
    check("下单成功", r.get("code") == 0)
    order_no = r.get("data", {}).get("offlineOrderNo")

    # 3. 播报列表：应含 draw + order_auto
    r = req("GET", "/api/merchant/announce/list", None, h)
    logs = r.get("data") or []
    types = [a.get("eventType") for a in logs]
    check("播报含开奖与订单", "draw" in types and "order_auto" in types, f"types={types}")
    draw_log = next((a for a in logs if a.get("eventType") == "draw"), None)
    check("开奖话术含关键信息", draw_log and "第" in str(draw_log.get("content")) and "抵扣比例" in str(draw_log.get("content")),
          f"content={str(draw_log.get('content'))[:60]}")

    # 4. 退款 → 退款播报
    r = req("POST", f"/api/customer/offline/order/{order_no}/refund", {})
    check("退款成功", r.get("code") == 0)
    r = req("GET", "/api/merchant/announce/list", None, h)
    logs = r.get("data") or []
    check("播报含退款", any(a.get("eventType") == "refund" for a in logs))

    # 5. 增量轮询
    latest = logs[0]["announceId"]
    r = req("GET", f"/api/merchant/announce/poll?afterId={latest}", None, h)
    check("增量轮询无重复", len(r.get("data") or []) == 0)
    r = req("GET", "/api/merchant/announce/poll?afterId=0", None, h)
    check("全量轮询返回全部", len(r.get("data") or []) >= 3, f"count={len(r.get('data') or [])}")

    print("\n===== 播报事件验证全部通过 =====")


if __name__ == "__main__":
    main()
