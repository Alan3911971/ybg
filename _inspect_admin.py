# -*- coding: utf-8 -*-
import io
p = r'D:\workspace-java\ybg-master\admin\index.html'
s = io.open(p, encoding='utf-8').read()
lines = s.splitlines()
for i, ln in enumerate(lines):
    if 'loadRecentAlerts' in ln or 'filterMerchants' in ln or 'async function loadReport' in ln:
        print('=== line', i+1, '===')
        print(repr(ln[:400]))
        print()
