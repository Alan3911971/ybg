# -*- coding: utf-8 -*-
"""V1.5 细化（27行）：跨店返还 = 实付×5% 发放 + 退款扣回（允许负余额）验证。"""
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



def disable_default_pools(mh):
    """禁用建商家默认档位（YBG 2026-08-18），只留脚本自配档位"""
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
    # 前置：M001 商家 + 门店配置（percent=50/上限=30）+ 抽余额 100 闭环
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "ReturnTest", "loginAccount": "m001", "loginPwd": "smoke123"},
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
    for _ in range(20):
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13800000040"})
        d = r.get("data") or {}
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": "13800000040", "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
        if d.get("prizeType") == 3:
            break

    # 1. 下单：抵扣 30，实付 70 → 跨店返还 70×5% = 3.5
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": "13800000040", "merchantNo": "M001",
             "orderAmount": "100.00", "paidAmount": "70.00"})
    d = r.get("data") or {}
    check("下单(抵扣30/实付70)", r.get("code") == 0 and float(d.get("deductBalance", 0)) == 30.0
          and float(d.get("payAmount", 0)) == 70.0,
          f"deduct={d.get('deductBalance')} pay={d.get('payAmount')}")
    check("跨店返还3.5入账", float(d.get("returnBalance", 0)) == 3.5, f"return={d.get('returnBalance')}")
    order_no = d.get("offlineOrderNo")

    # 2. 用户余额 = 100 - 30(抵扣) + 3.5(返还) = 73.5
    w = req("GET", "/api/customer/wallet/13800000040").get("data") or {}
    check("余额=73.5(含返还)", float(w.get("balance") or 0) == 73.5, f"balance={w.get('balance')}")

    # 3. 退款 → 退回抵扣 30 + 扣回返还 3.5 → 余额回 100
    r = req("POST", f"/api/customer/offline/order/{order_no}/refund", {})
    check("退款成功", r.get("code") == 0)
    w = req("GET", "/api/customer/wallet/13800000040").get("data") or {}
    check("退款后余额复原100", float(w.get("balance") or 0) == 100.0, f"balance={w.get('balance')}")

    # 4. 流水含跨店返还发放与扣回
    flows = w.get("flows") or []
    has_grant = any("跨店返还" in str(f.get("remark", "")) and f.get("amount", 0) > 0 for f in flows)
    has_claw = any("跨店返还扣回" in str(f.get("remark", "")) for f in flows)
    check("流水含发放与扣回", has_grant and has_claw, f"flows={len(flows)}")

    # 5. 商家只读展示返还比例
    r = req("GET", "/api/merchant/config", None, h)
    check("商家只读返还比例5%", r.get("code") == 0 and r.get("data", {}).get("crossStoreReturnPercent") == 5,
          f"percent={r.get('data', {}).get('crossStoreReturnPercent') if r.get('data') else None}")

    print("\n===== 跨店返还验证全部通过 =====")


if __name__ == "__main__":
    main()
