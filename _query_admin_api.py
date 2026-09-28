# -*- coding: utf-8 -*-
import urllib.request, urllib.parse, json

BASE = 'https://ybgtc.com'

def post(path, payload, token=None):
    data = payload if isinstance(payload, str) else json.dumps(payload)
    headers = {'Content-Type': 'application/x-www-form-urlencoded' if isinstance(payload, str) else 'application/json'}
    if token:
        headers['X-Admin-Token'] = token
    req = urllib.request.Request(BASE + path, data=data.encode('utf-8'), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode('utf-8', 'ignore'))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'ignore')[:500]

def get(path, token=None):
    headers = {'X-Admin-Token': token or ''}
    req = urllib.request.Request(BASE + path, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode('utf-8', 'ignore'))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'ignore')[:500]

st, body = post('/api/admin/auth/login', urllib.parse.urlencode({'account': 'admin', 'password': 'Admin@2026'}))
print('login:', st, json.dumps(body, ensure_ascii=False)[:200])
token = body.get('data') if isinstance(body, dict) else None
print('token:', (token or '')[:40])

if token:
    for path in ['/api/admin/alerts', '/api/admin/merchant/list', '/api/admin/config/list',
                 '/api/admin/public-pool-board', '/api/admin/member/list',
                 '/api/admin/property-company/list', '/api/admin/tv/ads/pending',
                 '/api/admin/report/coupons']:
        st, b = get(path, token)
        if isinstance(b, dict) and 'data' in b:
            d = b['data']
            info = ''
            if isinstance(d, list):
                info = 'items=%d' % len(d)
                if d and isinstance(d[0], dict):
                    info += ' first=' + json.dumps(d[0], ensure_ascii=False)[:150]
            else:
                info = json.dumps(d, ensure_ascii=False)[:150]
            print(path, '->', st, 'code', b.get('code'), info)
        else:
            print(path, '->', st, str(b)[:200])
