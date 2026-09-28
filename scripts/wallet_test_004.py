# -*- coding: utf-8 -*-
"""WALLET-TEST-004 余额按商家隔离验收测试"""
import json
import urllib.request
import urllib.parse

BASE = "http://192.168.31.228:19085"
MEMBER_ID = "13800138004"
STORE_A = "M001"
STORE_B = "M002"


def req(method, path, params=None, headers=None):
    url = BASE + path
    data = urllib.parse.urlencode(params).encode("utf-8") if params else None
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
        return {"_http": e.code, "body": e.read()[:300].decode("utf-8", "ignore")}


def check(name, cond, extra=""):
    print(f"[{'PASS' if cond else 'FAIL'}] {name} {extra}")
    if not cond:
        raise SystemExit(f"失败: {name}")


def grant(member_id, store_id, amount):
    return req("POST", f"/api/wallet/grant?memberId={member_id}&storeId={store_id}&amount={amount}")


def main():
    print(f"\n===== WALLET-TEST-004 余额按商家隔离验收 =====")
    print(f"会员: {MEMBER_ID}, A店: {STORE_A}, B店: {STORE_B}\n")

    # Step 1: A店充值 200
    r = grant(MEMBER_ID, STORE_A, 200)
    check("A店充值200", r.get("code") == 0 and float(r["data"]["balance"]) == 200.00,
          f"balance={r.get('data', {}).get('balance')}")

    # Step 2: B店充值 100
    r = grant(MEMBER_ID, STORE_B, 100)
    check("B店充值100", r.get("code") == 0 and float(r["data"]["balance"]) == 100.00,
          f"balance={r.get('data', {}).get('balance')}")

    # Step 3: 钱包列表按商家分组
    r = req("GET", f"/api/wallet/list?memberId={MEMBER_ID}")
    wallets = r.get("data") or []
    check("钱包列表含2个商家", len(wallets) == 2, f"count={len(wallets)}")
    a_wallet = next((w for w in wallets if w["storeId"] == STORE_A), None)
    b_wallet = next((w for w in wallets if w["storeId"] == STORE_B), None)
    check("A店余额=200", a_wallet and float(a_wallet["balance"]) == 200.00)
    check("B店余额=100", b_wallet and float(b_wallet["balance"]) == 100.00)

    # Step 4: A店余额查询
    r = req("GET", f"/api/wallet/balance?memberId={MEMBER_ID}&storeId={STORE_A}")
    check("A店balance=200", r.get("code") == 0 and float(r["data"]["balance"]) == 200.00,
          f"balance={r.get('data', {}).get('balance')}")

    # Step 5: B店余额查询
    r = req("GET", f"/api/wallet/balance?memberId={MEMBER_ID}&storeId={STORE_B}")
    check("B店balance=100", r.get("code") == 0 and float(r["data"]["balance"]) == 100.00,
          f"balance={r.get('data', {}).get('balance')}")

    # Step 6: A店消费 30 → A店余额 170
    r = req("POST", f"/api/wallet/pay?memberId={MEMBER_ID}&storeId={STORE_A}&amount=30")
    check("A店消费30", r.get("code") == 0 and float(r["data"]["remainingBalance"]) == 170.00,
          f"remaining={r.get('data', {}).get('remainingBalance')}")

    # Step 7: 验证 B店余额不受影响，仍为 100
    r = req("GET", f"/api/wallet/balance?memberId={MEMBER_ID}&storeId={STORE_B}")
    check("B店余额不受A店消费影响=100", r.get("code") == 0 and float(r["data"]["balance"]) == 100.00,
          f"balance={r.get('data', {}).get('balance')}")

    # Step 8: B店余额不足拒绝（尝试消费 200）
    r = req("POST", f"/api/wallet/pay?memberId={MEMBER_ID}&storeId={STORE_B}&amount=200")
    check("B店余额不足拒绝", r.get("code") == -1 and "余额不足" in r.get("msg", ""),
          f"msg={r.get('msg')}")

    # Step 9: B店正常消费 50 → B店余额 50
    r = req("POST", f"/api/wallet/pay?memberId={MEMBER_ID}&storeId={STORE_B}&amount=50")
    check("B店消费50", r.get("code") == 0 and float(r["data"]["remainingBalance"]) == 50.00,
          f"remaining={r.get('data', {}).get('remainingBalance')}")

    # Step 10: 商家流水接口（transactions）
    r = req("GET", f"/api/wallet/transactions?memberId={MEMBER_ID}&storeId={STORE_A}")
    check("A店transactions可查询", r.get("code") == 0, f"count={len(r.get('data') or [])}")

    # Step 11: 最终钱包列表验证
    r = req("GET", f"/api/wallet/list?memberId={MEMBER_ID}")
    wallets = r.get("data") or []
    a_final = next((w for w in wallets if w["storeId"] == STORE_A), {})
    b_final = next((w for w in wallets if w["storeId"] == STORE_B), {})
    check("最终A店余额=170", float(a_final.get("balance", 0)) == 170.00)
    check("最终B店余额=50", float(b_final.get("balance", 0)) == 50.00)
    check("A店累计消费=30", float(a_final.get("totalConsumed", 0)) == 30.00)
    check("B店累计消费=50", float(b_final.get("totalConsumed", 0)) == 50.00)

    print("\n===== WALLET-TEST-004 全部通过 =====")


if __name__ == "__main__":
    main()
