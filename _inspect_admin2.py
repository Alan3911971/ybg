# -*- coding: utf-8 -*-
import io
p = r'D:\workspace-java\ybg-master\admin\index.html'
s = io.open(p, encoding='utf-8').read()
lines = s.splitlines(True)
for i, ln in enumerate(lines):
    if ln.startswith('async function loadRecentAlerts') or ln.startswith('async function filterMerchants') or (ln.startswith('async function loadReport') and 'reportTable' in ln):
        print('=== line', i+1, 'total', len(ln), '===')
        print(repr(ln))
