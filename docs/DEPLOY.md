# 部署文档（测试环境）

## NAS 目录

| 用途 | 路径 |
|---|---|
| 项目 NAS 根目录 | `Z:\ybg\ibigou-blindbox`（NAS 物理路径 `/volume1/Download/ybg/ibigou-blindbox`） |
| 部署产物 | NAS `/volume1/Download/ybg/deploy/`（ibigou.jar / schema.sql） |

> 容器与 LensCabin 测试容器完全分离：独立命名 `ibigou-blindbox-test`，独立部署目录，仅共用
> `lenscabin-mysql-test` 的 MySQL 实例（库 `ibigou_blindbox` 独立，不污染 `lenscabin_test`）。

## 环境

- 数据库：`lenscabin-mysql-test` 容器（mysql:8.0），IP `172.17.0.11:3306`
  - 测试库：`ibigou_blindbox`，账号 `root` / `TestRoot@2026`
- 服务端口：容器内 8085，宿主映射 **19085**（`http://192.168.31.228:19085`）

## 建库

```bash
# schema.sql 已含 CREATE DATABASE + 13 张表 + 触发器 + 初始数据（幂等）
ssh alan@192.168.31.228 "cat /volume1/Download/ybg/deploy/schema.sql | sudo -n docker exec -i lenscabin-mysql-test mysql -uroot -pTestRoot@2026"
```

## 启动容器

```bash
sudo -n docker rm -f ibigou-blindbox-test
sudo -n docker run -d --name ibigou-blindbox-test --restart unless-stopped \
  --network bridge -p 19085:8085 \
  -v /volume1/Download/ybg/deploy/ibigou.jar:/app/ibigou.jar:ro \
  eclipse-temurin:17-jre-alpine \
  java -jar /app/ibigou.jar --server.port=8085 \
  --spring.datasource.url="jdbc:mysql://172.17.0.11:3306/ibigou_blindbox?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
  --spring.datasource.username=root --spring.datasource.password=TestRoot@2026
```

## 冒烟测试

```bash
python scripts/smoke_test.py   # 23 项断言：建商家→登录→配置→抽奖→闭环→线下抵扣退款→宜必购抵扣退款→报表导出→约束拦截
```

## 数据重置（注意）

`user_balance_flow` 有**禁止物理删除触发器**（V1.4 第 7 章防护），重置必须用 TRUNCATE：

```sql
USE ibigou_blindbox;
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE user_balance_flow;      -- 必须 TRUNCATE，DELETE 会被触发器拦截
DELETE FROM user_coupon; DELETE FROM user_account; DELETE FROM box_prize_limit_stat;
DELETE FROM third_group_verify_record; DELETE FROM ibigou_order; DELETE FROM offline_order;
DELETE FROM box_group_prize_pool; DELETE FROM box_prize_pool; DELETE FROM box_public_pool;
DELETE FROM merchant;
SET FOREIGN_KEY_CHECKS=1;
```
