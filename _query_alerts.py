# -*- coding: utf-8 -*-
import urllib.request, json

BASE = 'https://ybgtc.com'

def post(path, payload, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(payload).encode('utf-8'),
                                 headers={'Content-Type': 'application/json', 'X-Admin-Token': token or ''})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode('utf-8', 'ignore'))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'ignore')[:500]

def get(path, token=None):
    req = urllib.request.Request(BASE + path, headers={'X-Admin-Token': token or ''})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode('utf-8', 'ignore'))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'ignore')[:500]

# 登录
st, body = post('/api/admin/auth', {'username': 'admin', 'password': 'Admin@2026'})
print('login:', st, json.dumps(body, ensure_ascii=False)[:200])
token = None
if isinstance(body, dict):
    token = body.get('token') or (body.get('data') or {}).get('token') if isinstance(body.get('data'), dict) else None
print('token:', token)

if token:
    st, body = get('/api/admin/alerts', token)
    print('alerts status:', st)
    if isinstance(body, dict):
        data = body.get('data') or body
        if isinstance(data, list):
            for a in data[:6]:
                print('-', a.get('id'), a.get('type'), str(a.get('message') or a.get('content') or '')[:120], a.get('createdAt') or a.get('time'))
        else:
            print(json.dumps(body, ensure_ascii=False)[:1500])
