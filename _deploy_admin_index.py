# -*- coding: utf-8 -*-
# 部署修复后的 admin/index.html 到生产磁盘 + 同步 backend static 副本
import paramiko, hashlib, io, os, time

HOST, PORT, USER, KEY = '121.229.160.2', 5366, 'root', r'C:\Users\Administrator\.ssh\ybg_key'
LOCAL = r'D:\workspace-java\ybg-master\admin\index.html'
REMOTE = '/www/wwwroot/ybgtc.com/h5/admin/index.html'
STATIC = r'D:\workspace-java\ybg-master\backend\src\main\resources\static\h5\admin\index.html'

data = io.open(LOCAL, 'rb').read()
local_md5 = hashlib.md5(data).hexdigest()
print('local md5:', local_md5, 'size:', len(data))

# 1) 同步 backend static 副本（jar 内源码与磁盘一致）
io.open(STATIC, 'wb').write(data)
print('static copy updated:', hashlib.md5(io.open(STATIC,'rb').read()).hexdigest())

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect(HOST, port=PORT, username=USER, key_filename=KEY, timeout=20)

# 2) 备份生产文件
stdin, stdout, stderr = ssh.exec_command('cp %s %s.bak.$(date +%%Y%%m%%d_%%H%%M%%S) && ls -la %s.bak.* | tail -3' % (REMOTE, REMOTE, REMOTE))
print('backup:', stdout.read().decode('utf-8', 'ignore'), stderr.read().decode('utf-8', 'ignore'))

# 3) SFTP 上传
sftp = ssh.open_sftp()
sftp.put(LOCAL, REMOTE)
sftp.close()
print('uploaded via sftp')

# 4) md5 校验
stdin, stdout, stderr = ssh.exec_command('md5sum %s' % REMOTE)
remote_md5 = stdout.read().decode('utf-8', 'ignore').strip().split()[0]
print('remote md5:', remote_md5)
assert remote_md5 == local_md5, 'MD5 MISMATCH!'
print('MD5 OK - deploy verified')

# 5) curl 公网验证（含缓存参数）
stdin, stdout, stderr = ssh.exec_command("curl -s 'http://127.0.0.1/h5/admin/index.html?v=%s' | md5sum" % int(time.time()))
print('local curl md5:', stdout.read().decode('utf-8', 'ignore').strip())

ssh.close()
print('DEPLOY DONE')
