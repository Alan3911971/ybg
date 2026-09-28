# -*- coding: utf-8 -*-
import io, re
s = io.open(r'D:\workspace-java\ybg-master\merchant_index_live.html', encoding='utf-8').read()
# 找 queue.html 出现的完整上下文
for m in re.finditer(r'.{260}queue\.html.{200}', s, re.S):
    print(repr(m.group(0)))
    print('====')
