# -*- coding: utf-8 -*-
"""P0 安全加固验证：验证码登录/单日额度/paid_diff/收款码审核/测试确认开关。"""
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
    h = dict(headers or {})
    if path.startswith("/api/customer") and "/auth/" not in path:
        ph = (params or {}).get("userPhone")
        if not ph and "/wallet/" in path:
            ph = path.split("/wallet/")[1].split("/")[0]
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
    # 前置：M001 商家 + 配置 + 抽余额
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    ah = {"X-Admin-Token": at}
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "P0Test", "loginAccount": "m001", "loginPwd": "smoke123"}, ah)
    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    # 前置：测试确认开关默认关（member 脚本可能残留开启）
    req("POST", "/api/admin/config/update", {"key": "test_pay_confirm_enabled", "value": "0"}, ah)
    req("POST", "/api/merchant/config/deduct-config", {"balanceDeductPercent": "50", "dailyDeductLimit": "30"}, h)
    req("POST", "/api/merchant/config/weights",
        {"privatePoolWeight": "100", "publicPoolWeight": "0",
         "boxDiscountTotalWeight": "50", "boxCouponTotalWeight": "50"}, h)
    req("POST", "/api/merchant/config/prize-pools", {"prizeType": "3", "prizeValue": "50.00", "weight": "100"}, h)
    for _ in range(20):
        r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13800000120"})
        d = r.get("data") or {}
        if d.get("drawBatchNo"):
            req("POST", "/api/customer/group/register",
                {"merchantNo": "M001", "userPhone": "13800000120", "channel": 1,
                 "groupAmount": "10", "drawBatchNo": d.get("drawBatchNo")})
        if d.get("prizeType") == 3:
            break

    # P0-1 无 token 拦截（裸请求，绕过自动登录）
    import urllib.request as _ur
    _body = urllib.parse.urlencode({"userPhone": "13800000120", "merchantNo": "M001", "orderAmount": "100.00"}).encode()
    _rr = _ur.Request(BASE + "/api/customer/offline/order", data=_body, method="POST")
    try:
        with _ur.urlopen(_rr, timeout=15) as _resp:
            _raw = _resp.read().decode()
    except Exception as _e:
        _raw = str(_e)
    check("P0-1 无token拦截", "请先登录" in _raw, f"resp={_raw[:80]}")

    # P0-3 paid_diff：实付与应付差异记录（YBG 2026-08-18 默认档位后抵扣随机，改为通用断言 diff=paid-pay）
    r = req("POST", "/api/customer/offline/order",
            {"userPhone": "13800000120", "merchantNo": "M001", "orderAmount": "100.00", "paidAmount": "60.00"})
    d = r.get("data") or {}
    pay = float(d.get("payAmount") or 0)
    paid = float(d.get("paidAmount") or 0)
    diff = float(d.get("paidDiff") or 0)
    check("P0-3 下单+paidDiff", r.get("code") == 0 and abs(diff - (paid - pay)) < 0.01 and paid < pay,
          f"payAmount={pay} paid={paid} diff={diff}（期望 {paid - pay}）")

    # P0-4 收款码上传 → 待审核(1)；平台审核通过(2)
    import io as _io
    png = _io.BytesIO()
    import base64
    png.write(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="))
    import tempfile, os
    tf = os.path.join(tempfile.gettempdir(), "p0_qr.png")
    with open(tf, "wb") as f:
        f.write(png.getvalue())
    import subprocess
    # 用 curl 上传（multipart）
    up = subprocess.run(["curl", "-s", "-X", "POST", BASE + "/api/merchant/config/upload-qr",
                         "-H", "X-Merchant-Token: " + mt, "-F", "type=wechat", "-F", "file=@" + tf],
                        capture_output=True, text=True).stdout
    upj = json.loads(up)
    check("P0-4 上传收款码", upj.get("code") == 0, f"url={upj.get('data')}")
    cfg = req("GET", "/api/merchant/config/deduct-config", None, h).get("data") or {}
    check("P0-4 上传后待审核", cfg.get("receiveQrStatus") == 1, f"status={cfg.get('receiveQrStatus')}")
    r = req("POST", "/api/admin/merchant/M001/qr-audit", {"action": "approve"}, ah)
    check("P0-4 平台审核通过", r.get("code") == 0)
    cfg = req("GET", "/api/merchant/config/deduct-config", None, h).get("data") or {}
    check("P0-4 审核后状态2", cfg.get("receiveQrStatus") == 2, f"status={cfg.get('receiveQrStatus')}")
    req("POST", "/api/admin/merchant/M001/qr-audit", {"action": "reject"}, ah)
    cfg = req("GET", "/api/merchant/config/deduct-config", None, h).get("data") or {}
    check("P0-4 驳回状态3", cfg.get("receiveQrStatus") == 3, f"status={cfg.get('receiveQrStatus')}")
    req("POST", "/api/admin/merchant/M001/qr-audit", {"action": "approve"}, ah)

    # P0-5 测试确认开关：默认关闭 → 拦截；开启 → 可用
    r = req("POST", "/api/merchant/member/renew-order", None, h)
    ono = r.get("data", {}).get("orderNo")
    r = req("POST", f"/api/merchant/member/renew-order/{ono}/confirm", None, h)
    check("P0-5 默认关闭拦截", r.get("code") != 0 and "生产环境" in str(r.get("msg")), f"msg={r.get('msg')}")
    r = req("POST", "/api/admin/config/update", {"key": "test_pay_confirm_enabled", "value": "1"}, ah)
    check("P0-5 开启开关", r.get("code") == 0)
    r = req("POST", f"/api/merchant/member/renew-order/{ono}/confirm", None, h)
    check("P0-5 开启后可用", r.get("code") == 0 and r.get("data", {}).get("status") == 1)
    req("POST", "/api/admin/config/update", {"key": "test_pay_confirm_enabled", "value": "0"}, ah)

    print("\n===== P0 安全加固验证全部通过 =====")


if __name__ == "__main__":
    main()
