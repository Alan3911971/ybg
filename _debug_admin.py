# -*- coding: utf-8 -*-
import io
p = r'D:\workspace-java\ybg-master\admin\index.html'
s = io.open(p, encoding='utf-8').read()

old1 = 'el.innerHTML=arr.map(function(a){return "<div style="padding:8px 0;border-bottom:1px solid var(--c-divider);display:flex;justify-content:space-between;font-size:13px;"><span>"+esc(a.title||a.content||"—")+"</span><span class="pill "+(a.handled?"ok":"warn")+">"+(a.handled?"已处理":"待处理")+"</span></div>";}).join("")'

idx = s.find('el.innerHTML=arr.map')
print('find idx:', idx)
if idx >= 0:
    seg = s[idx:idx+len(old1)]
    print('segment len match:', len(seg), len(old1))
    for i, (a, b) in enumerate(zip(old1, seg)):
        if a != b:
            print('first diff at', i, 'script=', repr(a), 'file=', repr(b))
            print('context script:', repr(old1[max(0,i-20):i+20]))
            print('context file :', repr(seg[max(0,i-20):i+20]))
            break
    else:
        print('segments identical up to len(old1)')
