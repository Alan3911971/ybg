# -*- coding: utf-8 -*-
import io, re
s = io.open(r'D:\workspace-java\ybg-master\merchant_index_live.html', encoding='utf-8').read()
for m in re.finditer(r'.{150}boot.{220}', s, re.S):
    print(repr(m.group(0)))
    print('====')
for m in re.finditer(r'.{80}merchantNo\s*=.{120}', s):
    print(repr(m.group(0)))
    print('----')
