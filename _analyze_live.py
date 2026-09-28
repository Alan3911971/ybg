# -*- coding: utf-8 -*-
import io, re
s = io.open(r'D:\workspace-java\ybg-master\admin_index_live2.html', encoding='utf-8').read()
print('len', len(s))
print('has p8 tab:', 'data-p="p8"' in s)
print('has p9 tab:', 'data-p="p9"' in s)
print('has inviteQr:', 'btnGenInvite' in s)
print('has appSecret:', 'gWxAppSecret' in s)
print('has bad quote loadRecent:', 'return "<div style="padding' in s)
print('has good quote loadRecent:', "return '<div style=\"padding" in s)
m = re.search(r'loadRecentAlerts\(\)\{try\{const r=await get\(A\+"/alerts"\);.*?\}catch\(e\)\{\}\}', s)
print('loadRecent:', (m.group(0)[:300] if m else 'NOT FOUND'))
