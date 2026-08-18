# 宜必购盲盒系统 · 系统全景交接文档（codex-d1 专属）

> 2026-08-17 | 目的：让 codex-d1 **完整了解整个系统**，可独立修改代码并部署测试服务器。
> 本次授权范围：**前端重设计（V2 手机端）+ 后端代码修改 + 测试服务器部署**。
> ❌ 不授权：生产部署（Hermes 门禁）、生产密钥配置（平台后台 UI）、NAS 系统级操作。

## 一、系统是什么

线下门店盲盒营销系统：顾客扫码 H5 抽盲盒得优惠券/余额 → 到店消费自动抵扣 →
商家后台配置奖品/核销/报表 → 平台后台管商家/全局参数/审计。三端全部 H5（无 APP）。

## 二、技术栈与代码结构

```
Spring Boot 3.2.5 / Java 17 / Spring Data JPA / MySQL 8.0 / Flyway / zxing / wechatpay-java
目录：
  backend/  Spring Boot 应用（端口 8085）
    src/main/java/com/ibigou/blindbox/
      common/    Result、BizException、QrSvgUtil（zxing→SVG）、ConfigCryptoService（AES-GCM 加密）
      config/    拦截器（CustomerAuth/AdminAuth/WebConfig）
      controller/ 5 个：CustomerController（顾客）/ MerchantController（商家）/ AdminController（平台）
                  / MerchantConfigController（商家配置）/ PayNotifyController（微信回调）
      entity/   24 个实体 ↔ 24 张表
      repository/ 24 个 XxxRepository（行锁 @Lock PESSIMISTIC_WRITE）
      service/  30 个服务（Draw/FlowClose/Coupon/Balance/OrderCalc/OfflineOrder/Ibigou/GroupRecord/
                Member/WxPay/Announce/UserMessage/AuditLog/GlobalConfig/Report/Sms/IdentityQr/DynamicQr/Job...）
      enums/    9 个枚举（PrizeType/FlowType/VerifyType/CouponStatus/RefundStatus/GroupChannel/LimitScope/LimitCycle/PoolType）
    src/main/resources/
      application.yml（8085、db=ibigou_blindbox、app.domain=https://ybgtc.com）
      db/migration/V1__baseline.sql（= db/schema.sql，Flyway 基线）
      static/h5/  ← 运行页面（顾客/商家/平台三端 HTML，改这里+重打包即生效）
  db/schema.sql   24 表幂等建表 + 初始配置 + 流水禁删触发器
  frontend/       设计稿输出目录（codex-d1 生成产物）
  scripts/
    gen_frontend_v2.py（顾客端模板分发生成器）
    gen_frontend_v3.py（商家+平台模板分发生成器）
    _templates/  ← 前端模板源（codex-d1 本次重做这里）
    smoke_*.py  14 个冒烟脚本（API 回归）
    gen_knowledge_graph.py（知识图谱生成）
  docs/           需求/差距/评审/知识图谱/部署手册/合规
  Dockerfile + docker-compose.yml + nginx/  生产部署材料
```

## 三、数据库（24 表，Schema 见 db/schema.sql）

| 域 | 表 |
|---|---|
| 奖品 | box_prize_pool（本店档位）/ box_public_pool（公共投放）/ box_group_prize_pool（团购专属）/ box_group_pool_config（专属大类权重）/ box_prize_limit_stat（限额统计）/ ibigou_goods（宜必购商品） |
| 用户资产 | user_coupon（券：can_use_after_draw 闭环标记）/ user_account（余额）/ user_balance_flow（**禁删流水**，触发器） |
| 交易 | offline_order（线下单：paid_amount/paid_diff/voucher_token/merchant_trade_no/return_balance）/ ibigou_order（宜必购）/ third_group_verify_record（团购登记） |
| 商家 | merchant（含 balance_deduct_percent/daily_deduct_limit/receive_mode/receive_qr*/member_expire_time）/ merchant_session / daily_deduct_quota（单日抵扣额度） |
| 会员 | member_order / audit_log（审计可撤销）/ sys_global_config（全局参数）/ admin_user / merchant_message / announce_log（播报）/ user_message（用户弹窗）/ customer_session / sys_alert（告警） |

## 四、核心业务规则（改代码前必读）

1. **余额抵扣全自动四重 min**：min(可用余额, 盲盒后金额×门店percent%, 单日上限−已累计, 盲盒后金额)；
   计算在 OrderCalcService（含自动选券、免单边界、理论/实际抵扣）；下单不手输抵扣。
2. **流程闭环**：新抽资产 can_use_after_draw=0 → 团购登记/线下支付成功/宜必购下单 → 批次闭环置 1（FlowCloseService）+ 定时补偿（JobService 每 5 分钟）。
3. **收款模式 A/B**：A=商家上传收款码（receive_qr_status 0未传/1待审/2通过/3驳回），用户回填实付；B=30 分钟凭证二维码+商家确认完成。
4. **跨店返还**：线下闭环后实付×5%（cross_store_return_percent）入余额+用户弹窗；退款扣回（允许负余额）。
5. **会员**：免费试用=激活+free_trial_days；续费叠加（过期从当天）；过期写锁定（查询只读）；平台人工调整 +N 月（审计）。
6. **折扣券**：prizeType=1 为折扣率（8=8 折，范围 0-10）；立减 2；余额 3；团购免单 4。
7. **券核销**：线下仅发行商家（source_merchant_no）；宜必购看 is_support_ibigou；外来券商家只能看不能手工核销。
8. **退款仅全额**（定稿要求）；标记入口仅商家端。
9. **平台微信支付**：wx_pay_enabled=0 mock/1 真实；密钥仅平台后台配置（AES-GCM 加密存储、脱敏）。
10. **金额一致性**：任何扣减/发放必须写 user_balance_flow（流水禁删）+ 同步 user_account.total_balance + 对应订单字段。

## 五、API 契约

- 请求头：顾客 `X-User-Token`、商家 `X-Merchant-Token`、平台 `X-Admin-Token`（登录返回 token 存 localStorage）
- 顾客登录：POST /api/customer/auth/send-code + /login（测试固定码 123456）
- 商家登录：POST /api/merchant/auth/login；平台：POST /api/admin/auth/login
- 二维码渲染：POST /api/merchant/config/qr-svg 或 /api/customer/qr-svg（content→{svg}）
- 完整端点索引：`docs/知识图谱.md`（104 端点）/ `docs/知识图谱.json`

## 六、测试环境（codex-d1 可用）

```
服务      http://192.168.31.228:19085（容器 ibigou-blindbox-test，jar 挂载 /volume1/Download/ybg/deploy/ibigou.jar）
平台      admin / Admin@2026
商家      m001 / smoke123（测试门店 M001）
顾客      任意 1 开头手机号 + 验证码 123456
数据库    NAS docker 内 MySQL：172.17.0.11:3306 / ibigou_blindbox / root / TestRoot@2026
项目      Z:\ybg\ibigou-blindbox（NAS /volume1/Download/ybg/ibigou-blindbox）——D 盘副本 D:\reasonix\ibigou-blindbox
```

## 七、打包部署流程（测试服务器，codex-d1 可执行）

```bash
# 1. 打包（本机，JDK17 路径固定）
cd D:\reasonix\ibigou-blindbox\backend
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot" mvn -DskipTests package

# 2. 上传 jar（ssh 管道）
cat target/ibigou-blindbox-1.0.0-SNAPSHOT.jar | ssh -o StrictHostKeyChecking=no alan@192.168.31.228 "cat > /volume1/Download/ybg/deploy/ibigou.jar"

# 3. 重启容器
ssh -o StrictHostKeyChecking=no alan@192.168.31.228 "sudo -n docker restart ibigou-blindbox-test"

# 4. 健康检查
curl http://192.168.31.228:19085/actuator/health   # {"status":"UP"}

# 5. 改表结构时（schema.sql 幂等，UTF-8 管道执行）
cat D:\reasonix\ibigou-blindbox\db\schema.sql | ssh -o StrictHostKeyChecking=no alan@192.168.31.228 \
  "sudo -n docker exec -i lenscabin-mysql-test mysql -uroot -pTestRoot@2026"

# 6. 跑冒烟（先重置业务数据，SQL 见 scripts/reset_test_data.sql；然后）
cd D:\reasonix\global-workspace
PYTHONIOENCODING=utf-8 python smoke_test.py        # 主冒烟 23 项
PYTHONIOENCODING=utf-8 python smoke_v15_p0.py      # P0 安全
PYTHONIOENCODING=utf-8 python smoke_v15_p1.py      # P1 业务
PYTHONIOENCODING=utf-8 python smoke_v15_p2.py      # P2 运营
```

> 冒烟脚本依赖 API 契约；改了接口名/参数会导致 FAIL，需同步改脚本。

## 八、代码修改纪律（重要）

1. **同步双向**：改完 D 盘后 `cp -r D:\reasonix\ibigou-blindbox\. Z:\ybg\ibigou-blindbox\`（Z 为 NAS 权威，反之亦同步）
2. **禁硬编码**：域名/IP 用 `location.origin`（前端）或 `app.domain` 配置（后端）；禁止写死 https://ybgtc.com
3. **转义坑**：HTML 内嵌 JS 多行文本用模板字符串；禁 `\n` 字面量（用 String.fromCharCode(10)）；heredoc 嵌套会降级转义
4. **编码**：UTF-8 无 BOM；ssh 传 SQL 用 UTF-8 文件管道（Windows 编码问题）
5. **金额**：一律 BigDecimal；改金额逻辑必须同步流水+余额+订单三处一致性
6. **安全**：生产密钥不落盘、不进代码库；管理员密码 BCrypt；删除敏感操作需密码验证
7. **编译**：JAVA_HOME 必须用 Adoptium jdk-17（msjdk17 不可用）；测试用 `mvn test -DforkCount=0`（surefire fork 崩溃）
8. **前端页面在 static/h5/**（打包进 jar）；**模板在 scripts/_templates/**（生成器分发到 frontend/，需 reasonix 合成或直接改 static/h5）

## 九、本次任务（V2 手机端重设计）

见《前端重设计任务单-for-codex-d1-V2.md》：三端 375px 手机端完全重设计、功能完整、
改 `scripts/_templates/` 或直接改 `backend/src/main/resources/static/h5/`（两者最终都进 jar）。

**两条路径均可（都可部署，codex-d1 自选）**：
- **路径 A（直接）**：改 `backend/src/main/resources/static/h5/*.html` → 打包部署即生效
- **路径 B（生成器，同样可部署）**：改 `scripts/_templates/*.html` → 运行
  `python scripts/gen_frontend_v2.py` + `gen_frontend_v3.py` 生成到 `frontend/` →
  **把生成结果复制到** `backend/src/main/resources/static/h5/` 对应目录（cp 覆盖）→ 打包部署
  注意：若直接改 frontend/ 而不同步 static/h5/，页面不会进 jar，必须复制或由 reasonix 合并
（推荐路径 A，改哪套最终以 static/h5/ 为准——它打包进 jar）
