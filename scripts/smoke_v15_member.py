# -*- coding: utf-8 -*-
"""V1.5 会员续费体系验证：状态/套餐/续费订单/有效期叠加/过期写锁定/平台调整+审计。"""
import io
import json
import os
import subprocess
import tempfile
import urllib.parse
import urllib.request

BASE = "http://192.168.31.228:19085"
EXP_SQL = "USE ibigou_blindbox; UPDATE merchant SET member_expire_time = NOW() - INTERVAL 1 DAY WHERE merchant_no = 'M001';"


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


def make_expired():
    """SQL 文件管道方式改过期时间（避免 ssh 参数引号被远端 shell 处理）"""
    sql_file = os.path.join(tempfile.gettempdir(), "expire_m001.sql")
    with io.open(sql_file, "w", encoding="utf-8") as f:
        f.write(EXP_SQL)
    with open(sql_file, "rb") as fh:
        p = subprocess.Popen(
            ["ssh", "-o", "ConnectTimeout=15", "-o", "StrictHostKeyChecking=no",
             "alan@192.168.31.228", "sudo", "-n", "docker", "exec", "-i",
             "lenscabin-mysql-test", "mysql", "-uroot", "-pTestRoot@2026"],
            stdin=fh, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        p.wait()


def main():
    at = req("POST", "/api/admin/auth/login", {"account": "admin", "password": "Admin@2026"}).get("data")
    check("平台登录", isinstance(at, str))
    ah = {"X-Admin-Token": at}
    # 前置：强制活动关闭 + 开启测试确认开关（P0-5），随后脚本再操作
    req("POST", "/api/admin/config/update", {"key": "renew_gift_switch", "value": "0"}, ah)
    req("POST", "/api/admin/config/update", {"key": "renew_gift_months", "value": "0"}, ah)
    req("POST", "/api/admin/config/update", {"key": "test_pay_confirm_enabled", "value": "1"}, ah)
    req("POST", "/api/admin/merchant/create",
        {"merchantNo": "M001", "merchantName": "MemberTest", "loginAccount": "m001", "loginPwd": "smoke123"}, ah)

    mt = req("POST", "/api/merchant/auth/login", {"account": "m001", "password": "smoke123"}).get("data")
    h = {"X-Merchant-Token": mt}
    check("商家登录", isinstance(mt, str))

    r = req("GET", "/api/merchant/member/status", None, h)
    st = r.get("data") or {}
    check("免费试用中(30天)", r.get("code") == 0 and st.get("state") == 0 and st.get("remainDays") >= 28,
          f"state={st.get('state')} remain={st.get('remainDays')}")

    r = req("GET", "/api/merchant/member/plan", None, h)
    pl = r.get("data") or {}
    check("套餐信息(月价150/年费1800)", pl.get("monthlyPrice") == 150.0 and pl.get("annualPrice") == 1800.0
          and pl.get("giftSwitchOn") is False, f"{pl}")

    req("POST", "/api/admin/config/update", {"key": "renew_gift_switch", "value": "1"}, ah)
    r = req("POST", "/api/admin/config/update", {"key": "renew_gift_months", "value": "3"}, ah)
    check("开启赠送活动", r.get("code") == 0)

    r = req("POST", "/api/merchant/member/renew-order", None, h)
    order = r.get("data") or {}
    check("续费订单(购12赠3总15)", r.get("code") == 0 and order.get("buyMonths") == 12
          and order.get("giftMonths") == 3 and order.get("totalMonths") == 15
          and order.get("amount") == 1800.0,
          f"buy={order.get('buyMonths')} gift={order.get('giftMonths')} total={order.get('totalMonths')}")
    order_no = order.get("orderNo")

    r = req("POST", f"/api/merchant/member/renew-order/{order_no}/confirm", None, h)
    c = r.get("data") or {}
    check("确认支付叠加有效期", r.get("code") == 0 and c.get("status") == 1 and c.get("validTo") is not None,
          f"validTo={c.get('validTo')}")

    r = req("GET", "/api/merchant/member/status", None, h)
    st = r.get("data") or {}
    check("状态变为已付费", st.get("state") == 1, f"state={st.get('state')}")

    req("POST", "/api/admin/config/update", {"key": "renew_gift_switch", "value": "0"}, ah)
    r = req("POST", "/api/merchant/member/renew-order", None, h)
    o2 = r.get("data") or {}
    check("活动关闭后新订单赠送0", o2.get("giftMonths") == 0 and o2.get("totalMonths") == 12,
          f"gift={o2.get('giftMonths')} total={o2.get('totalMonths')}")

    # 过期写锁定（SQL 文件管道）
    make_expired()
    r = req("POST", "/api/customer/draw/normal", {"merchantNo": "M001", "userPhone": "13800000030"})
    check("过期后开盲盒被锁定", r.get("code") != 0 and "会员已到期" in str(r.get("msg")), f"msg={r.get('msg')}")
    r = req("GET", "/api/customer/wallet/13800000030")
    check("过期后查询只读不受限", r.get("code") == 0)

    # 平台人工调整 +12 个月（审计）
    r = req("POST", "/api/admin/member/M001/adjust", {"months": 12}, ah)
    check("平台人工调整有效期", r.get("code") == 0)
    r = req("GET", "/api/admin/member/audits", None, ah)
    logs = r.get("data") or []
    check("审计日志已记录", len(logs) >= 2, f"count={len(logs)}")
    r = req("GET", "/api/merchant/member/status", None, h)
    st = r.get("data") or {}
    check("调整后写权限恢复", st.get("writeAllowed") is True)

    print("\n===== 会员续费体系验证全部通过 =====")


if __name__ == "__main__":
    main()
