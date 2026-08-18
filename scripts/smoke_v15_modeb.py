# -*- coding: utf-8 -*-
"""V1.5 模式B验证：挂起下单(凭证)→商家核验→确认完成(扣余额/核销/返还)→凭证失效。"""
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
    # 前置：M001 商家 + 模式B + 门店配置 + 抽余额 100 闭环
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "ModeBTest", "loginAccount": "m001", "loginPwd": "smoke123"},
        {"X-Admin-Token": at})
    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    req("POST", "/api/merchant/config/deduct-config",
        {"balanceDeductPercent": "50", "dailyDeductLimit": "30.00", "receiveMode": "2"}, h)  # 模式B
    req("POST", "/api/merchant/config/weights",
        {"privatePoolWeight": "100", "publicPoolWeight": "0",
         "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, h)
    disable_default_pools(h)
    req("POST", "/api/merchant/config/prize-pools",
        {"prizeType": "3", "prizeValue": "100.00", "weight": "100"}, h)
    for _ in range(20):
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13800000050"})
        d = r.get("data") or {}
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": "13800000050", "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
        if d.get("prizeType") == 3:
            break

    # 1. 模式B下单：挂起（order_status=0，凭证 token，不扣资产）
    r = req("POST", "/api/customer/offline/order-b",
            {"userPhone": "13800000050", "merchantNo": "M001", "orderAmount": "100.00"})
    o = r.get("data") or {}
    check("模式B挂起下单", r.get("code") == 0 and o.get("orderStatus") == 0 and o.get("voucherToken"),
          f"status={o.get('orderStatus')} voucher={o.get('voucherToken','')[:12]}...")
    check("挂起不扣资产(余额仍100)", float(req("GET", "/api/customer/wallet/13800000050")
          .get("data", {}).get("balance") or 0) == 100.0)
    voucher = o.get("voucherToken")
    order_no = o.get("offlineOrderNo")

    # 2. 商家凭证核验
    r = req("GET", f"/api/merchant/order/voucher?voucherToken={urllib.parse.quote(voucher)}", None, h)
    v = r.get("data") or {}
    check("商家凭证核验", r.get("code") == 0 and v.get("offlineOrderNo") == order_no and v.get("payAmount") == 70.0,
          f"payAmount={v.get('payAmount')}")

    # 3. 商家确认完成 → 扣余额30/返还3.5 → 余额 = 100-30+3.5 = 73.5
    r = req("POST", f"/api/merchant/order/{order_no}/confirm", None, h)
    c = r.get("data") or {}
    check("确认完成闭环", r.get("code") == 0 and c.get("orderStatus") == 1 and c.get("voucherToken") is None,
          f"status={c.get('orderStatus')} deduct={c.get('deductBalance')} return={c.get('returnBalance')}")
    w = req("GET", "/api/customer/wallet/13800000050").get("data") or {}
    check("确认后余额73.5", float(w.get("balance") or 0) == 73.5, f"balance={w.get('balance')}")

    # 4. 凭证一次性：确认后失效
    r = req("GET", f"/api/merchant/order/voucher?voucherToken={urllib.parse.quote(voucher)}", None, h)
    check("凭证确认后失效", r.get("code") != 0, f"msg={r.get('msg')}")

    # 5. 模式A仍可用（模式切换回A后收款码流程）
    req("POST", "/api/merchant/config/deduct-config", {"receiveMode": "1"}, h)
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": "13800000050", "merchantNo": "M001", "orderAmount": "100.00", "paidAmount": "70.00"})
    check("模式A下单仍可用", r.get("code") == 0 and r.get("data", {}).get("orderStatus") == 1,
          f"status={r.get('data', {}).get('orderStatus') if r.get('data') else None}")

    print("\n===== 模式B验证全部通过 =====")


if __name__ == "__main__":
    main()
