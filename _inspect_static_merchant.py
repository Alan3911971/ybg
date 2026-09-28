# -*- coding: utf-8 -*-
import io, re
s = io.open(r'D:\workspace-java\ybg-master\backend\src\main\resources\static\h5\merchant\index.html', encoding='utf-8').read()
print('jar-inner len:', len(s))
# tab 切换代码
for m in re.finditer(r'.{120}queue\.html.{160}', s, re.S):
    print('--- queue ctx ---')
    print(repr(m.group(0)))
for m in re.finditer(r'.{40}merchantNo\s*=\s*mi\.data\.merchantNo.{80}', s, re.S):
    print('--- assign ctx ---')
    print(repr(m.group(0)))
# window.merchantNo 赋值
for m in re.finditer(r'window\.merchantNo[^;]{0,80}', s):
    print('window.merchantNo:', repr(m.group(0)))
