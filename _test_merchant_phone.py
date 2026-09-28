# -*- coding: utf-8 -*-
import urllib.request, urllib.parse, json

BASE = 'https://ybgtc.com'

def call(method, path, payload=None, token=None, hdr='X-Merchant-Token'):
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

for acct, pwd in [('13851918986', '123321'), ('13851918986', 'M001@2026'), ('13851918986', 'smoke123')]:
    st, body = call('POST', '/api/merchant/auth/login', urllib.parse.urlencode({'account': acct, 'password': pwd}))
    print('form acct=%s pwd=%s ->' % (acct, pwd), st, json.dumps(body, ensure_ascii=False)[:250])
    if isinstance(body, dict) and body.get('code') == 0:
        d = body.get('data') or {}
        tk = d.get('token') if isinstance(d, dict) else d
        print('LOGIN OK, data:', json.dumps(d, ensure_ascii=False)[:300])
        for m, p in [('GET','/api/merchant/config'), ('GET','/api/merchant/orders?limit=5'),
                     ('GET','/api/merchant/member/status'), ('GET','/api/merchant/member/plan'),
                     ('GET','/api/merchant/announce/list'), ('GET','/api/merchant/messages'),
                     ('GET','/api/merchant/config/prize-pools'), ('GET','/api/merchant/messages/unread-count')]:
            s2, b2 = call(m, p, token=tk)
            d2 = b2.get('data') if isinstance(b2, dict) else None
            print('  ', p, '->', s2, 'code', b2.get('code') if isinstance(b2, dict) else '?', json.dumps(d2, ensure_ascii=False)[:150] if d2 is not None else 'null')
        break
