# -*- coding: utf-8 -*-
"""V1.5 门店级抵扣 + 单日额度验证（四重 min / 累加 / 二次限额 / 退款返还）。"""
import json
import urllib.parse
import urllib.request
import time
import random

PHONE = "138" + "".join(random.choices("0123456789", k=8))
BALANCE_TARGET = 100.0

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



def disable_default_pools(mh):
    """一天一次+默认21档（YBG 2026-08-18）：禁用建商家自动生成的默认档位，只留脚本自配档位（独立 urllib，不依赖脚本 req 签名）"""
    import urllib.request as _u, urllib.parse as _p
    base = "http://192.168.31.228:19085"
    rq = _u.Request(base + "/api/merchant/config/prize-pools", headers=mh)
    with _u.urlopen(rq, timeout=30) as resp:
        r = json.loads(resp.read().decode())
    for p in (r.get("data") or []):
        if p.get("enabled") == 1:
            body = _p.urlencode({"x": "1"}).encode()
            rq2 = _u.Request(base + "/api/merchant/config/prize-pools/%s/disable" % p["prizeId"], data=body, headers=mh)
            _u.urlopen(rq2, timeout=30).read()


def check(name, cond, extra=""):
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {extra}")
    if not cond:
        raise SystemExit(f"失败: {name}")


def main():
    # 前置：建商家 M001 + 配置门店抵扣（percent=50, 单日上限=30）
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "DeductTest", "loginAccount": "m001", "loginPwd": "smoke123"},
        {"X-Admin-Token": at})
    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    req("POST", "/api/merchant/config/deduct-config",
        {"balanceDeductPercent": "50", "dailyDeductLimit": "30.00", "receiveMode": "1"}, h)
    req("POST", "/api/merchant/config/weights",
        {"privatePoolWeight": "100", "publicPoolWeight": "0",
         "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, h)
    disable_default_pools(h)
    req("POST", "/api/merchant/config/prize-pools",
        {"prizeType": "3", "prizeValue": "100.00", "weight": "100"}, h)
    # 抽余额并闭环（余额 100 元）
    import time
    for i in range(20):
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": PHONE})
        d = r.get("data") or {}
        if d.get("prizeType") == 3:
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": PHONE, "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
            break
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": PHONE, "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
    w = req("GET", f"/api/customer/wallet/{PHONE}").get("data") or {}
    bal = float(w.get("balance") or 0)
    print(f"[INFO] 用户余额={bal}")
    if bal < BALANCE_TARGET:
        # 余额不足时继续抽，最多再抽 40 次
        for _extra in range(40):
            r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": PHONE})
            d = r.get("data") or {}
            if d.get("drawBatchNo"):
                req("POST", "/api/customer/group/register",
                    {"merchantNo": "M001", "userPhone": PHONE, "channel": 1,
                     "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
            if d.get("prizeType") == 3:
                break
        w = req("GET", f"/api/customer/wallet/{PHONE}")
        bal = float(w.get("data", {}).get("balance") or 0)
    assert bal >= BALANCE_TARGET, f"余额不足: {bal}（需抽到 100 元余额档位）"

    # 1. 下单1：应自动抵扣 30（单日额度 30 用尽）
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": PHONE, "merchantNo": "M001", "orderAmount": "100.00"})
    d = r.get("data") or {}
    check("下单1 自动抵扣30", r.get("code") == 0 and float(d.get("deductBalance", 0)) == 30.0,
          f"order={d.get('offlineOrderNo')} deduct={d.get('deductBalance')} pay={d.get('payAmount')}")
    order1 = d.get("offlineOrderNo")

    # 2. 单日额度已用 30 → 再次下单应抵扣 0（min(余额70, 50, 0, 100)=0）
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": PHONE, "merchantNo": "M001", "orderAmount": "100.00"})
    d = r.get("data") or {}
    check("下单2 单日额度用尽抵扣0", r.get("code") == 0 and float(d.get("deductBalance", 99)) == 0.0,
          f"deduct={d.get('deductBalance')} pay={d.get('payAmount')}")

    # 3. 退款第1单 → 额度返还（accum 30 -> 0）
    r = req("POST", f"/api/customer/offline/order/{order1}/refund", {})
    check("退款成功", r.get("code") == 0 and r.get("data", {}).get("refundStatus") == 1)

    # 4. 退款后再下单：额度已返还 → 应再次抵扣 30
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": PHONE, "merchantNo": "M001", "orderAmount": "100.00"})
    d = r.get("data") or {}
    check("退款后额度返还再抵扣30", r.get("code") == 0 and float(d.get("deductBalance", 0)) == 30.0,
          f"deduct={d.get('deductBalance')}")

    # 5. 兜底完成订单（商家侧，verify_type=2，自动计算）
    MT = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": MT}
    r = req("POST", "/api/merchant/balance/manual-deduct",
            {"userPhone": PHONE, "orderAmount": "100.00"}, h)
    check("商家兜底自动计算抵扣", r.get("code") == 0, f"msg={r.get('msg')}")

    print("\n===== V1.5 门店抵扣验证全部通过 =====")


if __name__ == "__main__":
    main()
