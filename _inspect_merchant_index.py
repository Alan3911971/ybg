# -*- coding: utf-8 -*-
import io, re
s = io.open(r'D:\workspace-java\ybg-master\merchant_index_live.html', encoding='utf-8').read()
print('len', len(s))
for m in re.finditer(r'queue[^"]{0,90}|merchantNo[^"]{0,70}', s):
    print(repr(m.group(0))[:110])
print('--- tab handler ---')
i = s.find('更多')
# 找 tabs 点击绑定
for m in re.finditer(r'addEventListener\([^)]{0,80}', s):
    print(repr(m.group(0))[:100])
