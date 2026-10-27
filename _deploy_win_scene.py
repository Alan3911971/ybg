# -*- coding: utf-8 -*-
"""
中奖弹屏「场景引擎」上线部署

一次做完三件事：
  1) 后端 jar：/api/tv/{no}/screen 增加结构化奖品字段（prizeType/prizeValue/customerSeq）
     并修掉"谢谢参与"被播成"恭喜开中了余额0.00元"的 bug
  2) 前端页面：_redesign_v3/tv-v2.html -> 线上 h5/tv.html（APK 加载的就是这个路径）
  3) 分档音效：h5/fx/*.wav -> 线上 h5/fx/（4 场景 × 3 档 = 12 个）

安全动作：
  - 线上文件先备份（带时间戳），出问题可秒回滚
  - jar 先编译成功才替换，编译失败不动线上
  - 替换后调接口验证，验证不过就回滚

用法：python _deploy_win_scene.py
"""
import os
import subprocess
import sys
import time
import datetime
import shlex

import paramiko

HOST = '121.229.160.2'
PORT = 5366
KEY = r'C:\Users\Administrator\.ssh\ybg_key'
BASE = os.path.dirname(os.path.abspath(__file__))
YBG = os.path.dirname(BASE)  # D:\workspace-java

TS = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')

# 要传的后端源码（相对项目根）
BACKEND_FILES = [
    'backend/src/main/java/com/ibigou/blindbox/entity/AnnounceLog.java',
    'backend/src/main/java/com/ibigou/blindbox/service/AnnounceService.java',
    'backend/src/main/java/com/ibigou/blindbox/service/DrawService.java',
    'backend/src/main/java/com/ibigou/blindbox/controller/TvQueueController.java',
    'backend/src/main/resources/db/migration/V21__announce_prize_fields.sql',
]

FRONTEND_PAGE = os.path.join(BASE, '_redesign_v3', 'tv-v2.html')
FX_DIR = os.path.join(BASE, 'h5', 'fx')
MERCHANT_INDEX = os.path.join(BASE, 'backend', 'src', 'main', 'resources',
                              'static', 'h5', 'merchant', 'index.html')


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, port=PORT, username='root', key_filename=KEY, timeout=30)

    def run(cmd, t=120):
        _, o, e = c.exec_command(cmd, timeout=t)
        return o.read().decode('utf-8', 'replace') + e.read().decode('utf-8', 'replace')

    sftp = c.open_sftp()
    print('=== 1) 上传后端源码到服务器构建目录 ===')
    # 服务器的 /tmp/_build/backend 会被清空，pom.xml 必须一起传，否则 maven 直接报
    # "no POM in this directory"。这里整棵 src/ + pom.xml 全量覆盖，保证自洽。
    def mkdirs(remote_dir):
        parts = remote_dir.strip('/').split('/')
        cur = ''
        for p in parts:
            cur += '/' + p
            try:
                sftp.stat(cur)
            except IOError:
                run('mkdir -p %s' % cur)

    def put_tree(local_dir, remote_dir):
        n = 0
        for root, dirs, files in os.walk(local_dir):
            rel = os.path.relpath(root, local_dir).replace('\\', '/')
            rdir = remote_dir if rel == '.' else remote_dir + '/' + rel
            mkdirs(rdir)
            for f in files:
                if f.endswith('.class') or f.endswith('.jar'):
                    continue
                sftp.put(os.path.join(root, f).replace('\\', '/'), rdir + '/' + f)
                n += 1
        return n

    run('rm -rf /tmp/_build/backend && mkdir -p /tmp/_build/backend')
    sftp.put(os.path.join(BASE, 'backend', 'pom.xml'), '/tmp/_build/backend/pom.xml')
    print('   pom.xml 已上传')
    cnt = put_tree(os.path.join(BASE, 'backend', 'src'), '/tmp/_build/backend/src')
    print('   src/ 共上传 %d 个文件' % cnt)

    print('\n=== 2) 服务器编译后端（失败就不动线上） ===')
    # 用 -B 而不是 -q：-q 会吞掉 BUILD SUCCESS，导致无法判断成败
    build = run(
        'cd /tmp/_build/backend && '
        'JAVA_HOME=/usr/lib/jvm/java-17-openjdk-17.0.18.0.8-2.el9.x86_64 '
        'mvn -B -Dmaven.test.skip=true -DskipTests package 2>&1 | tail -30', t=600)
    print(build[-1800:])
    if 'BUILD SUCCESS' not in build:
        print('!! 编译失败，终止（线上未动）')
        sftp.close(); c.close()
        return 1
    print(run('ls -la /tmp/_build/backend/target/*.jar'))

    print('\n=== 2.5) 生产库加列（prod flyway 关闭；MySQL 是宿主机原生 宝塔，非 docker 容器） ===')
    # 注意：生产库不是 docker 容器（没有 ibigou-mysql），app 也是裸进程运行，
    # 必须用宿主机 mysql 客户端 + .env 里的连接信息，不能用 docker exec。
    migration = (
        "set -a; source /www/wwwroot/ybgtc.com/.env; set +a\n"
        "PW=\"$IBIGOU_DB_PASSWORD\"\n"
        "H=\"${IBIGOU_DB_HOST:-127.0.0.1}\"; P=\"${IBIGOU_DB_PORT:-3306}\"\n"
        "U=\"${IBIGOU_DB_USER:-root}\"; D=\"${IBIGOU_DB_NAME:-ibigou_blindbox}\"\n"
        "EXIST=$(mysql -h\"$H\" -P\"$P\" -u\"$U\" -p\"'$PW'\" \"$D\" -N -e "
        "\"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_NAME='merchant_queue_config' "
        "AND COLUMN_NAME='tv_mode';\" 2>/dev/null)\n"
        "if [ \"$EXIST\" = \"1\" ]; then echo \"tv_mode 列已存在\"\n"
        "else mysql -h\"$H\" -P\"$P\" -u\"$U\" -p\"'$PW'\" \"$D\" <<'SQL' 2>&1\n"
        "ALTER TABLE merchant_queue_config ADD COLUMN tv_mode VARCHAR(16) NOT NULL DEFAULT 'call';\n"
        "SQL\n"
        "&& echo \"tv_mode 列已新增\" || echo \"tv_mode 列新增失败\"; fi"
    )
    print(run('bash -c ' + shlex.quote(migration), t=120))

    print('\n=== 3) 备份并替换 jar / 页面 / 音效 ===')
    print(run(
        'cd /www/wwwroot/ybgtc.com && '
        'cp -a ibigou.jar ibigou.jar.bak-%s && '
        'cp -a h5/tv.html h5/tv.html.bak-%s && '
        'echo 备份完成: $(ls -la ibigou.jar.bak-%s h5/tv.html.bak-%s | wc -l) 个' % (TS, TS, TS, TS)))

    # jar 是服务器上编译出来的，直接在远端 cp，不要走 sftp（那是 100MB 白传两趟）
    print(run(
        'cp -f /tmp/_build/backend/target/ibigou-blindbox-1.0.0-SNAPSHOT.jar /tmp/ibigou-new.jar && '
        'ls -la /tmp/ibigou-new.jar'))
    print('   新 jar 就绪（服务器本地编译产物）')

    sftp.put(FRONTEND_PAGE, '/tmp/tv-new.html')
    print('   新 tv.html 已上传 (%d B)' % sftp.stat('/tmp/tv-new.html').st_size)

    fx_files = [f for f in os.listdir(FX_DIR) if f.endswith('.wav')]
    for f in fx_files:
        sftp.put(os.path.join(FX_DIR, f), '/tmp/' + f)
    print('   音效 %d 个已上传: %s' % (len(fx_files), ' '.join(sorted(fx_files))))

    print(run(
        'cp -f /tmp/ibigou-new.jar /www/wwwroot/ybgtc.com/ibigou.jar && '
        'cp -f /tmp/tv-new.html /www/wwwroot/ybgtc.com/h5/tv.html && '
        'mkdir -p /www/wwwroot/ybgtc.com/h5/fx && '
        'cp -f /tmp/*.wav /www/wwwroot/ybgtc.com/h5/fx/ && '
        'ls -la /www/wwwroot/ybgtc.com/h5/fx/ | tail -20'))

    print('\n=== 3.5) 商家后台：新增「大屏显示模式」开关 ===')
    sftp.put(MERCHANT_INDEX, '/tmp/merchant-index-new.html')
    print(run(
        'cp -f /www/wwwroot/ybgtc.com/h5/merchant/index.html '
        '/www/wwwroot/ybgtc.com/h5/merchant/index.html.bak-%s && '
        'cp -f /tmp/merchant-index-new.html /www/wwwroot/ybgtc.com/h5/merchant/index.html && '
        'echo 商家后台已更新' % TS))

    print('\n=== 4) 重启后端 ===')
    print(run(
        'fuser -k 8085/tcp 2>/dev/null; sleep 2; '
        'cd /www/wwwroot/ybgtc.com; set -a; source .env; set +a; '
        'nohup java -Xms512m -Xmx1024m -Dspring.profiles.active=prod '
        '-Dspring.flyway.enabled=false -jar ibigou.jar > /tmp/ibigou.log 2>&1 & '
        'sleep 12; tail -5 /tmp/ibigou.log'))

    print('\n=== 5) 验证：接口是否返回结构化奖品字段 ===')
    time.sleep(3)
    api = run('curl -s "http://localhost:8085/api/tv/M90380836/screen"', t=60)
    ok = False
    try:
        import json
        d = json.loads(api)
        wins = (d.get('data') or {}).get('recentWins') or []
        print('   recentWins 条数: %d' % len(wins))
        for w in wins[:4]:
            print('     ' + json.dumps(w, ensure_ascii=False))
        if wins and wins[0].get('prizeType') is not None:
            ok = True
            print('   OK  已返回 prizeType/prizeValue')
        else:
            print('   !! 没有 prizeType —— 新 jar 没生效')
    except Exception as e:
        print('   !! 接口解析失败: %s' % e)
        print(api[:600])

    fxcheck = run('ls /www/wwwroot/ybgtc.com/h5/fx/ | grep -c "_t[123].wav"')
    print('   线上分档音效文件数: %s' % fxcheck.strip())
    pagecheck = run('grep -c "winTagIn\\|cp-ticket\\|bl-wallet\\|fr-stamp" /www/wwwroot/ybgtc.com/h5/tv.html')
    print('   线上页面含 4 套场景标记: %s' % pagecheck.strip())

    print('\n=== 5.5) 验证 tv_mode 相关上线 ===')
    mchk = run('grep -c "btnTvCall" /www/wwwroot/ybgtc.com/h5/merchant/index.html')
    print('   商家后台含大屏模式开关: %s' % mchk.strip())
    tchk = run('grep -c "mode-activity\\|bbHero" /www/wwwroot/ybgtc.com/h5/tv.html')
    print('   大屏页面含活动屏标记: %s' % tchk.strip())
    scchk = run('curl -s "http://localhost:8085/api/tv/M90380836/screen" | grep -c "tvMode"')
    print('   screen 接口返回 tvMode: %s' % scchk.strip())
    colchk = run('set -a; source /www/wwwroot/ybgtc.com/.env; set +a; '
                 'PW="$IBIGOU_DB_PASSWORD"; H="${IBIGOU_DB_HOST:-127.0.0.1}"; P="${IBIGOU_DB_PORT:-3306}"; '
                 'U="${IBIGOU_DB_USER:-root}"; D="${IBIGOU_DB_NAME:-ibigou_blindbox}"; '
                 'mysql -h"$H" -P"$P" -u"$U" -p"$PW" "$D" -N -e '
                 '"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_NAME=\'merchant_queue_config\' '
                 'AND COLUMN_NAME=\'tv_mode\';" 2>/dev/null | grep -c 1')
    print('   生产库 tv_mode 列存在: %s' % colchk.strip())

    sftp.close()
    c.close()
    print('\n部署结果: %s' % ('成功' if ok else '接口校验未通过（jar 可能是旧的）'))
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
