# -*- coding: utf-8 -*-
# 校验 admin/index.html：script 标签配对 + 内联 JS 语法（node --check）
import io, re, subprocess, os, tempfile

p = r'D:\workspace-java\ybg-master\admin\index.html'
s = io.open(p, encoding='utf-8').read()

opens = re.findall(r'<script[^>]*>', s)
closes = re.findall(r'</script>', s)
print('script open:', len(opens), 'close:', len(closes))
assert len(opens) == len(closes), 'TAG MISMATCH!'

# 内联脚本（无 src）
inline = re.findall(r'<script(?![^>]*src=)[^>]*>(.*?)</script>', s, re.S)
print('inline script blocks:', len(inline))

node = r'C:\Program Files\nodejs\node.exe'
if not os.path.exists(node):
    # 尝试其他路径
    for c in ['node', r'C:\Program Files\nodejs\node.exe']:
        try:
            subprocess.run([c, '--version'], capture_output=True, check=True)
            node = c
            break
        except Exception:
            pass
print('node path:', node)
for i, sc in enumerate(inline):
    with tempfile.NamedTemporaryFile('w', suffix='.js', delete=False, encoding='utf-8') as f:
        f.write(sc)
        fn = f.name
    r = subprocess.run([node, '--check', fn], capture_output=True, text=True)
    print(f'block {i}: exit={r.returncode}', (r.stderr or r.stdout).strip()[:300])
    os.unlink(fn)
print('VALIDATE DONE')
