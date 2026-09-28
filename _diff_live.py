# -*- coding: utf-8 -*-
import io, difflib
live = io.open(r'D:\workspace-java\ybg-master\admin_index_live2.html', encoding='utf-8').read()
disk = io.open(r'D:\workspace-java\ybg-master\admin\index.html', encoding='utf-8').read()
print('live chars:', len(live), 'disk chars:', len(disk))
d = list(difflib.unified_diff(disk.splitlines(), live.splitlines(), lineterm='', n=0))
print('diff lines:', len(d))
for x in d[:60]:
    print(x[:200])
