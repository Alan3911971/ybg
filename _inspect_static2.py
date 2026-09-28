# -*- coding: utf-8 -*-
import io, re
s = io.open(r'D:\workspace-java\ybg-master\backend\src\main\resources\static\h5\merchant\index.html', encoding='utf-8').read()
print('len', len(s))
for kw in ['queue', '排队', 'ads.html', '广告', 'p20', 'p21', 'merchantNo']:
    cnt = s.count(kw)
    print(kw, ':', cnt)
# 找 tabs 定义
i = s.find('id="tabs"')
if i > 0:
    print('--- tabs html ---')
    print(s[i:i+700])
# 找 tab 点击处理
for m in re.finditer(r'.{80}classList\.add\(.active.\);.{200}', s, re.S):
    print('--- click handler ---')
    print(repr(m.group(0)))
    break
