# -*- coding: utf-8 -*-
import urllib.request, urllib.parse, json

BASE = 'https://ybgtc.com'

def call(method, path, payload=None, token=None, hdr='X-Admin-Token'):
    data = None
    headers = {}
    if payload is not None:
        if isinstance(payload, str):
            data = payload.encode('utf-8')
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        else:
            data = json.dumps(payload).encode('utf-8')
            headers['Content-Type'] = 'application/json'
    if token:
        headers[hdr] = token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode('utf-8', 'ignore'))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'ignore')[:400]

# 1) 平台后台登录 + 最新告警详情
st, body = call('POST', '/api/admin/auth/login', urllib.parse.urlencode({'account': 'admin', 'password': 'Admin@2026'}))
admin_tk = body.get('data') if isinstance(body, dict) else None
print('admin login:', st, 'code', body.get('code') if isinstance(body, dict) else body)
if admin_tk:
    st, body = call('GET', '/api/admin/alerts', token=admin_tk)
    alerts = body.get('data') if isinstance(body, dict) else None
    if isinstance(alerts, list) and alerts:
        a = alerts[0]
        print('--- latest alert #%s ---' % a.get('alertId'))
        for k, v in a.items():
            print(' ', k, ':', json.dumps(v, ensure_ascii=False)[:300])
    else:
        print('alerts body:', json.dumps(body, ensure_ascii=False)[:800])

# 2) 商家后台登录（m001）
st, body = call('POST', '/api/merchant/auth/login', {'account': 'm001', 'password': 'M001@2026'}, hdr='X-Merchant-Token')
print('\nmerchant m001 login:', st, json.dumps(body, ensure_ascii=False)[:200])
tk = None
if isinstance(body, dict) and body.get('code') == 0:
    d = body.get('data')
    tk = d.get('token') if isinstance(d, dict) else d
    print('merchantNo:', d.get('merchantNo') if isinstance(d, dict) else '')
if not tk:
    st, body = call('POST', '/api/merchant/auth/login', {'account': 'm001', 'password': 'smoke123'}, hdr='X-Merchant-Token')
    print('merchant m001/smoke123:', st, json.dumps(body, ensure_ascii=False)[:200])
    if isinstance(body, dict) and body.get('code') == 0:
        d = body.get('data')
        tk = d.get('token') if isinstance(d, dict) else d
if tk:
    endpoints = [
        ('GET', '/api/merchant/config', None),
        ('GET', '/api/merchant/orders?limit=5', None),
        ('GET', '/api/merchant/member/status', None),
        ('GET', '/api/merchant/member/plan', None),
        ('GET', '/api/merchant/announce/list', None),
        ('GET', '/api/merchant/messages', None),
        ('GET', '/api/merchant/config/prize-pools', None),
    ]
    for m, p, pl in endpoints:
        st, b = call(m, p, pl, token=tk, hdr='X-Merchant-Token')
        if isinstance(b, dict):
            d = b.get('data')
            info = json.dumps(d, ensure_ascii=False)[:180] if d is not None else 'null'
            print('merchant', p, '->', st, 'code', b.get('code'), info)
        else:
            print('merchant', p, '->', st, str(b)[:180])
