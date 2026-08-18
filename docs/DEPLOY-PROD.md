# 生产部署手册（ybgtc.com）

> 生产部署由 **Hermes** 执行（reasonix 只交付代码与材料，标"待部署"）。
> 测试环境部署见 `docs/DEPLOY.md`。

## 0. 部署前检查清单

- [ ] 测试环境全部验证通过（主冒烟 23 项 + 专项 21 项 + 补偿任务）
- [ ] 生产 MySQL 8.0 实例可用（建议独立实例，勿与测试共用）
- [ ] 域名 ybgtc.com DNS 已解析到生产服务器
- [ ] HTTPS 证书就位（ybgtc.com.pem / ybgtc.com.key）
- [ ] 环境变量就位（见下方清单）

## 1. 环境变量清单

| 变量 | 必填 | 说明 |
|---|---|---|
| `IBIGOU_DB_PASSWORD` | ✅ | 生产库 root 密码（或专用账号密码） |
| `IBIGOU_DB_HOST` | | 默认 127.0.0.1；docker compose 内填 mysql |
| `IBIGOU_DB_PORT` | | 默认 3306 |
| `IBIGOU_DB_NAME` | | 默认 ibigou_blindbox |
| `IBIGOU_DB_USER` | | 默认 root（建议最小权限专用账号） |
| `IBIGOU_DOMAIN` | | 默认 https://ybgtc.com |

## 2. 建库

```bash
# 方式A：docker compose（MySQL 服务起来后）
docker compose up -d mysql
docker exec -i ibigou-mysql mysql -uroot -p"$IBIGOU_DB_PASSWORD" < db/schema.sql

# 方式B：独立 MySQL
mysql -h <host> -uroot -p < db/schema.sql   # schema.sql 幂等可重复执行
```

验证：`SHOW TABLES;` 应看到 15 张表（13 业务 + merchant_session + admin_user）+ sys_global_config 初始数据。

## 3. 构建与启动

```bash
# 方式A：docker compose（推荐，含 MySQL/App/Nginx）
cp .env.example .env && vim .env          # 填 IBIGOU_DB_PASSWORD / IBIGOU_DOMAIN
docker compose build
docker compose up -d
# 首次建库（见上节）；Nginx 证书放 ./nginx/certs/

# 方式B：单容器
docker build -t ibigou-blindbox:1.0.0 .
docker run -d --name ibigou-app --restart unless-stopped \
  -p 8085:8085 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e IBIGOU_DB_HOST=127.0.0.1 -e IBIGOU_DB_PORT=3306 \
  -e IBIGOU_DB_NAME=ibigou_blindbox -e IBIGOU_DB_USER=root \
  -e IBIGOU_DB_PASSWORD='<生产密码>' \
  -e IBIGOU_DOMAIN=https://ybgtc.com \
  ibigou-blindbox:1.0.0
```

## 4. 上线自检（Hermes 执行）

```bash
curl -s http://127.0.0.1:8085/actuator/health                    # {"status":"UP"}
curl -s https://ybgtc.com/h5/customer/index.html                 # 顾客盲盒页 200
curl -s -X POST https://ybgtc.com/api/admin/auth/login \
  -d "account=admin&password=Admin@2026"                          # 平台登录（首次）
curl -s https://ybgtc.com/h5/merchant/login.html                  # 商家后台 200
```

> ⚠️ 上线后立即在平台后台修改默认管理员密码 admin/Admin@2026（当前无修改密码接口，需下版本补充或由运维直接改库 BCrypt）。

## 5. 回滚

```bash
# 容器回滚：停掉当前，起上一个镜像 tag
docker compose stop app
docker tag ibigou-blindbox:1.0.0 ibigou-blindbox:rollback-$(date +%Y%m%d%H%M)
# 重新 build 旧版本 tag 后 up

# 数据回滚：DB 无结构变更时无需处理；有结构变更需先备份
mysqldump -h <host> ibigou_blindbox > backup-$(date +%Y%m%d%H%M).sql
```

## 6. 运维要点

- **定时任务**：过期券标记（每小时 5 分）、补偿闭环（每 5 分钟）由应用内置 @Scheduled 执行，无需外部 cron
- **流水禁删**：user_balance_flow 有 DELETE 触发器硬保护，运维清数必须 TRUNCATE
- **备份**：建议每日 mysqldump 全库 + binlog
- **监控**：/actuator/health + 容器日志（`docker logs -f ibigou-app`）
- **HTTPS 到期**：证书续期后 `docker compose restart nginx`

## 7. 交付物清单（本目录）

```
Dockerfile                     # 多阶段构建（maven -> temurin-jre）
docker-compose.yml             # mysql + app + nginx 编排
nginx/conf/ybgtc.com.conf      # 反代配置样例（80->443、/api、/h5、/qr）
db/schema.sql                  # 幂等建表（15 表 + 触发器 + 初始数据）
docs/DEPLOY-PROD.md            # 本手册
```

## 数据库迁移（Flyway，启动自动执行）
- `V1__baseline.sql`：24 表基线（含 V1.4 补充字段/抖音渠道/折算列）
- `V2__ybg_bug003_ensure_draw_batch_cols.sql`：draw_batch_no/merchant_no 补列（幂等）
- `V3__ybg_c_return_cols.sql`：团购登记折算 3 列（幂等，老库升级）
- 生产新库/老库均安全：新库跑 V1 完整建表；老库由 V2/V3 幂等补列

## 环境变量（必须设置）
| 变量 | 说明 |
|---|---|
| IBIGOU_DB_HOST/PORT/NAME/USER/PASSWORD | 数据库连接 |
| IBIGOU_DOMAIN | 对外域名（https://ybgtc.com） |
| **IBIGOU_ADMIN_INIT_PWD** | 默认管理员初始密码（**必须设置**，首登后立即修改） |
