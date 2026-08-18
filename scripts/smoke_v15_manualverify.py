# -*- coding: utf-8 -*-
"""YBG-C-RETURN-001 适配：verify_type=2 手工核销正向 + 审计撤销恢复券
构造：抽 8 折券(批次B) + 立减100券(批次A) → 下单1(用掉立减,闭环A) → 下单2(闭环B,8折券can_use=1) → 手工核销8折券 → 撤销恢复
"""
import json, urllib.request, urllib.parse

BASE = "http://192.168.31.228:19085"

def req(path, data=None, headers=None, method=None):
    url = BASE + path
    body = urllib.parse.urlencode({k: str(v) for k, v in (data or {}).items()}).encode() if data else None
    r = urllib.request.Request(url, data=body, headers=headers or {}, method=method or ("POST" if data else "GET"))
    with urllib.request.urlopen(r, timeout=30) as resp:
        return json.loads(resp.read().decode())


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
    print("[%s] %s %s" % ("PASS" if cond else "FAIL", name, extra))
    if not cond:
        raise SystemExit("失败: %s" % name)

def login(phone):
    req("/api/customer/auth/send-code", {"userPhone": phone})
    return req("/api/customer/auth/login", {"userPhone": phone, "code": "123456"})["data"]

def draw_until(ut, phone, want_type, want_value=None, maxn=30):
    """一天一次（YBG 2026-08-18）：同一用户同一商家每天只可参与一次 → 每轮新用户"""
    base = int(phone)
    for i in range(maxn):
        ph = str(base + i)
        ut = login(ph)
        r = req("/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": ph}, {"X-User-Token": ut})
        d = r.get("data") or {}
        if d.get("prizeType") == want_type and (want_value is None or abs(float(d.get("prizeValue")) - float(want_value)) < 0.001):
            return d, ph, ut
    return None, None, None

def main():
    at = req("/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"})["data"]
    AH = {"X-Admin-Token": at}
    req("/api/admin/merchant/create", {"merchantNo": "M001", "merchantName": "ManualVerify", "loginAccount": "m001", "loginPwd": "smoke123"}, AH)
    mt = req("/api/merchant/auth/login", {"account": "m001", "password": "smoke123"})["data"]
    MH = {"X-Merchant-Token": mt}
    req("/api/merchant/config/weights", {"privatePoolWeight": "100", "publicPoolWeight": "0", "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, MH)
    disable_default_pools(MH)
    ut = login("13800000410")

    # 1. 8 折档 → 抽批次B（8折券）
    r = req("/api/merchant/config/prize-pools", {"prizeType": "1", "prizeValue": "8.00", "weight": "100"}, MH)
    d8, b_phone, ut_b = draw_until(ut, "13800000410", 1, "8.00")
    check("抽到8折券(批次B)", d8 is not None, f"batch={d8 and d8.get('drawBatchNo')}")
    batch_b = d8["drawBatchNo"]

    # 2. 禁用8折档 → 立减100档 → 抽批次A（立减券）
    pid8 = r.get("data", {}).get("prizeId")
    req(f"/api/merchant/config/prize-pools/{pid8}/disable", {"x": "1"}, MH)
    r2 = req("/api/merchant/config/prize-pools", {"prizeType": "2", "prizeValue": "100.00", "weight": "100"}, MH)
    dA, a_phone, ut_a = draw_until(ut, "13800000410", 2, "100.00")
    check("抽到立减100券(批次A)", dA is not None, f"batch={dA and dA.get('drawBatchNo')}")
    batch_a = dA["drawBatchNo"]

    # 3. 宜必购下单（闭环③，不核销券）：批次B 8折券 can_use=1 保留
    req("/api/admin/config/update", {"key": "ibigou_channel_switch", "value": "1"}, AH)
    r = req("/api/customer/ibigou/order", {"userPhone": b_phone, "orderAmount": "30.00", "drawBatchNo": batch_b}, {"X-User-Token": ut_b})
    check("宜必购下单闭环批次B", r.get("code") == 0 and (r.get("data") or {}).get("ibigouOrderNo"),
          f"msg={r.get('msg')}")

    # 4. 8折券可用 → 手工核销 verify_type=2
    w = req("/api/customer/wallet/" + b_phone, None, {"X-User-Token": ut_b}).get("data") or {}
    own = next((x for x in (w.get("available") or []) if x.get("prizeType") == 1), None)
    check("8折券可用(宜必购闭环保留)", own is not None, f"couponId={own and own.get('couponId')}")
    r = req("/api/merchant/coupon/verify-manual", {"couponId": own["couponId"]}, MH)
    check("手工核销成功 verify_type=2", r.get("code") == 0 and r.get("data", {}).get("verifyType") == 2,
          f"verifyType={(r.get('data') or {}).get('verifyType')}")

    # 5. 审计 → 撤销 → 券恢复
    audits = req("/api/admin/member/audits", None, AH).get("data") or []
    vlog = next((x for x in audits if x.get("action") == "manual_verify" and x.get("revoked") != 1), None)
    check("审计含手工核销", vlog is not None, f"logId={vlog and vlog.get('logId')}")
    r = req(f"/api/admin/member/audits/{vlog['logId']}/revoke", {"adminPassword": "Admin@2026"}, AH)
    check("撤销手工核销", r.get("code") == 0)
    w2 = req("/api/customer/wallet/" + b_phone, None, {"X-User-Token": ut_b}).get("data") or {}
    restored = [x for x in (w2.get("available") or []) if x.get("couponId") == own["couponId"]]
    check("券已恢复未核销", len(restored) >= 1, f"avail={len(w2.get('available') or [])}")

    print("===== verify_type=2 手工核销 + 撤销恢复 全部通过 =====")

main()
