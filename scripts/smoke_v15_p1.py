# -*- coding: utf-8 -*-
"""P1 业务完整性验证：身份码/用户消息/订单导出/券有效期。"""
import base64
import io as _io
import json
import os
import subprocess
import tempfile
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
    h = dict(headers or {})
    if path.startswith("/api/customer") and "/auth/" not in path:
        ph = (params or {}).get("userPhone")
        if not ph and "/wallet/" in path:
            ph = path.split("/wallet/")[1].split("/")[0]
        if not ph and "/messages/" in path and "/unread" not in path and "/read" not in path:
            ph = path.split("/messages/")[1].split("/")[0]
        if not ph and "/identity-qr/" in path:
            ph = path.split("/identity-qr/")[1].split("/")[0]
        if not ph and _CUST_TOKENS:
            ph = list(_CUST_TOKENS.keys())[-1]
        if ph:
            h.setdefault("X-User-Token", _cust_token(ph))
    r = urllib.request.Request(url, data=data, method=method)
    for k, v in h.items():
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
    ah = {"X-Admin-Token": at}
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "P1Test", "loginAccount": "m001", "loginPwd": "smoke123"}, ah)
    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    req("POST", "/api/merchant/config/weights",
        {"privatePoolWeight": "100", "publicPoolWeight": "0",
         "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, h)
    req("POST", "/api/merchant/config/prize-pools", {"prizeType": "3", "prizeValue": "50.00", "weight": "100"}, h)

    # 顾客登录 + 抽余额闭环 + 下单
    ut = _cust_token("13800000130")
    for _ in range(20):
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13800000130"})
        d = r.get("data") or {}
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": "13800000130", "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
        if d.get("prizeType") == 3:
            break
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": "13800000130", "merchantNo": "M001", "orderAmount": "100.00", "paidAmount": "70.00"})
    check("下单成功", r.get("code") == 0)

    # P1-6 身份码：签发/验签查单/篡改拦截
    r = req("GET", "/api/customer/identity-qr/13800000130", None, {"X-User-Token": ut})
    idc = r.get("data") or ""
    check("身份码签发", r.get("code") == 0 and idc.startswith("YBGT-ID:"), f"{idc[:24]}...")
    r = req("GET", "/api/merchant/identity/verify?content=" + urllib.parse.quote(idc), None, h)
    v = r.get("data") or {}
    check("商家验签查单", r.get("code") == 0 and v.get("userPhone") == "13800000130" and len(v.get("orders") or []) >= 1,
          f"用户={v.get('userPhone')} 订单={len(v.get('orders') or [])}")
    r = req("GET", "/api/merchant/identity/verify?content=" + urllib.parse.quote("YBGT-ID:13800000130:bad"), None, h)
    check("篡改身份码拦截", r.get("code") != 0, f"msg={r.get('msg')}")

    # P1-10 用户消息：跨店返还已生成
    r = req("GET", "/api/customer/messages/13800000130/unread-count", None, {"X-User-Token": ut})
    check("用户消息未读>0", r.get("code") == 0 and (r.get("data") or 0) > 0, f"unread={r.get('data')}")
    r = req("GET", "/api/customer/messages/13800000130", None, {"X-User-Token": ut})
    msgs = r.get("data") or []
    check("消息含返还", any("返还余额" in str(m.get("content", "")) for m in msgs),
          f"count={len(msgs)} types={[m.get('msgType') for m in msgs]}")

    # P1-7 订单导出（二进制请求）
    _exp = urllib.request.Request(BASE + "/api/merchant/orders/export", headers=h)
    with urllib.request.urlopen(_exp, timeout=30) as _resp:
        _data = _resp.read()
    check("订单导出", len(_data) > 1000, f"bytes={len(_data)}")

    # P1-9 券有效期可配（配置成功即验证）
    r = req("POST", "/api/admin/config/update", {"key": "coupon_valid_days", "value": "15"}, ah)
    check("券有效期可配", r.get("code") == 0)

    print("\n===== P1 业务完整性验证全部通过 =====")


if __name__ == "__main__":
    main()
