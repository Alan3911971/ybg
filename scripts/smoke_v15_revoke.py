# -*- coding: utf-8 -*-
"""V1.5 审计撤销验证：手工核销券撤销/兜底完成撤销/退款撤销/调整有效期撤销/重复撤销拦截。"""
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
    # 前置：M001 商家 + 配置 + 档位（立减券+余额）
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    ah = {"X-Admin-Token": at}
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "RevokeTest", "loginAccount": "m001", "loginPwd": "smoke123"}, ah)
    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    req("POST", "/api/merchant/config/weights",
        {"privatePoolWeight": "100", "publicPoolWeight": "0",
         "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, h)
    req("POST", "/api/merchant/config/prize-pools",
        {"prizeType": "2", "prizeValue": "20.00", "weight": "50"}, h)
    req("POST", "/api/merchant/config/prize-pools",
        {"prizeType": "3", "prizeValue": "50.00", "weight": "50"}, h)

    # ---------- 场景1：手工核销券 → 撤销恢复 ----------
    # YBG-C-RETURN-001：团购登记折算作废券；未闭环券手工核销应拦截（正向 verify_type=2 由 smoke_v15_manualverify.py 覆盖）
    coupon_id = None
    # 一天一次（YBG）：每轮新用户，避免被当日参与限制拦截
    base = 13800000060
    for i in range(30):
        ph = str(base + i)
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": ph})
        d = r.get("data") or {}
        if d.get("isCoupon"):
            w = req("GET", "/api/customer/wallet/" + ph).get("data") or {}
            pend = [x for x in (w.get("pending") or []) if x.get("prizeType") == 2]
            if pend:
                coupon_id = pend[0]["couponId"]
                break
    check("抽到立减券(未闭环)", coupon_id is not None)
    r = req("POST", "/api/merchant/coupon/verify-manual", {"couponId": coupon_id}, h)
    check("未闭环券手工核销被拦截", r.get("code") != 0 and "暂不可用" in str(r.get("msg")),
          f"msg={r.get('msg')}")

    # ---------- 场景2：兜底完成 → 撤销退回余额+额度 ----------
    # 抽余额闭环（一天一次：每轮新用户）
    ph2 = None
    for i in range(30):
        ph2 = str(13800000061 + i)
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": ph2})
        d = r.get("data") or {}
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": ph2, "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
        if d.get("prizeType") == 3:
            break
    w0 = req("GET", "/api/customer/wallet/" + ph2).get("data") or {}
    bal0 = float(w0.get("balance") or 0)
    r = req("POST", "/api/merchant/balance/manual-deduct",
            {"userPhone": ph2, "orderAmount": "100.00"}, h)
    check("兜底完成订单", r.get("code") == 0)
    audits = req("GET", "/api/admin/member/audits", None, ah).get("data") or []
    comp_log = next((x for x in audits if x.get("action") == "manual_complete" and x.get("revoked") != 1), None)
    check("审计含兜底完成", comp_log is not None and comp_log.get("revokeType") == "BALANCE_RETURN")
    _ah = dict(ah); _ah["X-Admin-Pwd"] = "Admin@2026"
    r = req("POST", f"/api/admin/member/audits/{comp_log['logId']}/revoke", None, _ah)
    check("撤销兜底完成", r.get("code") == 0)
    w1 = req("GET", "/api/customer/wallet/" + ph2).get("data") or {}
    check("撤销后余额退回", float(w1.get("balance") or 0) == bal0, f"before={bal0} after={w1.get('balance')}")

    # ---------- 场景3：退款 → 撤销反向 ----------
    # 用模式A下单（用户有余额50）→ 退款 → 撤销退款
    w = req("GET", "/api/customer/wallet/" + ph2).get("data") or {}
    bal_before = float(w.get("balance") or 0)
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": ph2, "merchantNo": "M001", "orderAmount": "100.00", "paidAmount": "50.00"})
    o = r.get("data") or {}
    check("下单成功", r.get("code") == 0 and o.get("offlineOrderNo"), f"deduct={o.get('deductBalance')}")
    order_no = o.get("offlineOrderNo")
    r = req("POST", f"/api/customer/offline/order/{order_no}/refund", {})
    check("退款成功", r.get("code") == 0)
    audits = req("GET", "/api/admin/member/audits", None, ah).get("data") or []
    refund_log = next((x for x in audits if x.get("action") == "refund" and x.get("revoked") != 1), None)
    check("审计含退款", refund_log is not None and refund_log.get("revokeType") == "REFUND_REVERSE")
    _ah = dict(ah); _ah["X-Admin-Pwd"] = "Admin@2026"
    r = req("POST", f"/api/admin/member/audits/{refund_log['logId']}/revoke", None, _ah)
    check("撤销退款", r.get("code") == 0, f"msg={r.get('msg')}")
    # 订单回到未退款状态
    w = req("GET", "/api/customer/wallet/" + ph2).get("data") or {}
    check("撤销退款后余额回退(扣除抵扣)", float(w.get("balance") or 0) < bal_before,
          f"before={bal_before} after={w.get('balance')}")

    # ---------- 场景4：调整有效期 → 撤销减回 + 重复撤销拦截 ----------
    r = req("POST", "/api/admin/member/M001/adjust", {"months": 12}, ah)
    check("调整有效期", r.get("code") == 0)
    audits = req("GET", "/api/admin/member/audits", None, ah).get("data") or []
    adj_log = next((x for x in audits if x.get("action") == "adjust_expire" and x.get("revoked") != 1), None)
    check("审计含调整有效期", adj_log is not None and adj_log.get("revokeType") == "EXPIRE_DECREASE")
    st = req("GET", "/api/merchant/member/status", None, h).get("data") or {}
    expire_before = st.get("expireTime")
    _ah = dict(ah); _ah["X-Admin-Pwd"] = "Admin@2026"
    r = req("POST", f"/api/admin/member/audits/{adj_log['logId']}/revoke", None, _ah)
    check("撤销调整有效期", r.get("code") == 0)
    st2 = req("GET", "/api/merchant/member/status", None, h).get("data") or {}
    check("有效期减回12个月", st2.get("expireTime") is not None and st2.get("expireTime") < expire_before,
          f"before={expire_before} after={st2.get('expireTime')}")
    _ah2 = dict(ah); _ah2["X-Admin-Pwd"] = "Admin@2026"
    r = req("POST", f"/api/admin/member/audits/{adj_log['logId']}/revoke", None, _ah2)
    check("重复撤销拦截", r.get("code") != 0, f"msg={r.get('msg')}")

    print("\n===== 审计撤销验证全部通过 =====")


if __name__ == "__main__":
    main()
