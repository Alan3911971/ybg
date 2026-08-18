# 宜必购盲盒优惠券 & 余额系统 (ibigou-blindbox)

全新独立项目，与 LensCabin 主项目分离。

## 项目定位

线下门店盲盒抽奖营销系统：顾客扫码 H5 抽奖获得优惠券 / 余额奖励，
支持美团 / 饿了么团购到店专属盲盒、私有 / 公共奖品池、宜必购内部渠道
下单抵扣退款、商家对账、平台全局管控。

## 关键约束（来自需求 V1.4）

- 全部端均为 H5，无任何 APP。
- 宜必购是系统内部业务渠道，不调用任何第三方接口。
- 不对接美团 / 饿了么官方 API；团购券真实核销在美团 / 饿了么 App 完成，
  我方只做登记记录、发放盲盒奖励；团购订单禁止使用本次抽到的优惠券 / 余额。
- 用户唯一标识：手机号。
- 优惠券线下核销仅限发行商家 source_merchant_no；宜必购渠道由 is_support_ibigou 控制。
- 余额是用户全局资产，最大抵扣比例由平台全局参数 balance_deduct_rate 统一控制。
- 新抽资产 can_use_after_draw=0（暂不可用），流程闭环后置 1 才可抵扣。

## 目录结构

```
ibigou-blindbox/
├── README.md
├── docs/        # 需求规格说明书（V1.4 基线）
├── db/          # 建表 SQL、数据字典
├── backend/     # 后端服务
└── frontend/    # 三端 H5（顾客 / 商家 / 平台）
```

## 文档基线

- docs/需求规格说明书-V1.4.md —— 开发、测试、产品以 V1.4 为准；
  后续变更以文档修订版本号更新。

## 当前进度（2026-08-16）

| 模块 | 状态 | 说明 |
|---|---|---|
| docs/ | ✅ | 需求规格说明书 V1.4 基线存档 |
| db/ | ✅ | schema.sql：10 张表 + 索引 + 流水禁删触发器 + 全局参数初始数据 |
| backend/ | 🏗 骨架 | Spring Boot 3.2.5 + Java 17 + JPA；Entity/Repository/枚举齐备；service/controller 待业务开发 |
| frontend/ | 🏗 骨架 | 三端 H5 占位页（customer/merchant/admin） |

- 域名：**https://ybgtc.com**（app.domain 配置 + 前端页统一引用）
- 后端端口：8085（独立，避开 LensCabin 测试环境 8080/8081/8089/8090）
- 数据库：`ibigou_blindbox`（MySQL 8）

## 下一步

1. service 层业务开发（DrawService / BalanceService / IbigouService / FlowCloseService …）
2. controller 三端 API + 登录鉴权
3. 定时任务（过期券标记 / can_use_after_draw 补偿）
4. Excel 导出与报表

### 开发进度补充（2026-08-16 23:30）

- **service 层 13 个服务已实现**：DrawService（权重开奖+限额行锁）/ FlowCloseService（三种闭环）/ CouponService / BalanceService / OfflineOrderService / GroupRecordService / IbigouService（事务下单退款）/ ManualService / JobService（过期券+补偿）/ MerchantAuthService / WalletService / GlobalConfigService，编译通过。
- **三端 controller 已实现**：CustomerController / MerchantController（token 鉴权）/ AdminController；启动类已加 @EnableScheduling。
- **V1.4 补充设计**：offline_order、box_group_prize_pool、ibigou_goods 三张表 + draw_batch_no 批次闭环字段（详见 db/schema.sql 末尾说明）。

### 开发进度补充（2026-08-17 00:10）

- **商家后台配置接口**：档位 CRUD（私有池/公共投放/美团饿了么专属池）、权重保存（双 0 校验）、公共投放/上下架、专属盲盒二维码 URL、外来券查看、余额发放/消费流水、团购登记报表、宜必购渠道对账——均含 Excel 导出。
- **平台后台**：商家账号管理（新增 BCrypt/启停/重置密码）、商家配置只读审计、公共池大盘、全平台报表（券/余额流水/团购登记/宜必购交易）+ Excel 导出。
- **ReportService**：POI 通用 xlsx 导出；余额流水补充 merchant_no 门店维度（对账依据，schema 幂等添加）。

### 已知简化与待明确（V1.5 待办）

1. 折扣券 prize_value 语义文档未明确，当前统一按金额抵扣（注释已标注）
2. 专属盲盒"大类权重"未建模，当前按档位权重直抽
3. 商家登录为内存 token 简化版（重启失效）；平台后台鉴权未实现
4. 报表与 Excel 导出未实现（下一步）

### V1.5 最终定稿差距分析（2026-08-17 04:10）

- 用户提供**最终定稿需求**（新增：续费赠送活动、收款模式 A/B、门店级余额抵扣全自动、会员续费、跨店返还、个人身份二维码、审计日志、蓝牙音箱、平台微信支付）
- **用户拍板**：商家端不做 APP，改为 **H5**（手机浏览器适配，蓝牙音箱等功能保留在 H5 载体）
- 产出文档：
  - `docs/需求规格说明书-V1.5-最终定稿.md`（定稿全文 + V1.4→V1.5 变更对照 + 数据模型增量 + 接口改造点 + 待明确项）
  - `docs/差距分析-最终定稿.md`（已实现 ✅ / 缺失 ❌ / 不到位 ⚠️ / 文档变更点 / 待明确 / 开发优先级）
- **差距结论**：核心盲盒/券/余额链路已就绪；**未实现**：收款模式 A/B、门店级抵扣（现为平台全局+手输）、会员续费体系+写锁定、跨店返还、审计日志、个人身份二维码、平台微信支付、蓝牙播报、单日额度
- 开发优先级：P0 门店级抵扣+全自动四重计算+单日额度 → 会员续费+写锁定 → 收款模式 A；P1 模式 B/跨店返还/审计/身份码/微信支付；P2 蓝牙播报/流水号/H5 手机适配

### 知识图谱（2026-08-17）🗺️ 修改查询指南

- **docs/知识图谱.md**（人读）：架构分层 / 30 服务依赖注入 / 24 表↔实体 / 6 控制器 104 端点 / 9 枚举 / 5 定时任务 / 修改影响速查
- **docs/知识图谱.json**（机器读）：同上结构化数据
- 生成脚本：scripts/gen_knowledge_graph.py（改代码后重新生成：`python scripts/gen_knowledge_graph.py`）
- 说明：本机 codegraph 与 Node 26 不兼容、NAS codegraph-runner 未装工具，故自建零依赖图谱；后续 CodeGraph 可用可增量接入

## 当前状态（2026-08-18）

- **前端 V2 手机端 H5 已上线**：三端 375px 重设计（codex-d1 交付，reasonix 验收）；页面维护直接改 `backend/src/main/resources/static/h5/` 并同步 `frontend/` + `scripts/_templates/`（三处 md5 一致）
- **生成器 gen_frontend_v2/v3 已废弃**：V1 时代硬编码，运行会覆盖 V2 页面（已加防护，检测 V2 特征即退出）
- **团购登记折算**（YBG-C-RETURN-001）：美团/饿了么/抖音登记按盲盒奖品折算余额（谢谢5%/折扣N折/立减面值，原券作废，幂等）；宜必购闭环③已补齐
- **安全**：默认管理员密码配置化（`IBIGOU_ADMIN_INIT_PWD` 环境变量，生产必须注入）；商家删除/审计撤销需管理员密码；密钥平台后台加密存储脱敏
- 测试环境：`http://192.168.31.228:19085`（平台 admin/Admin@2026、商家 m001/smoke123、顾客验证码 123456）

### V2.2（2026-08-18 用户拍板）
- **一天一次**：同一顾客同一商家（含美团/饿了么/抖音所有二维码）每天只能参与一次盲盒，防刷
- **商家默认配置**：新商家创建自动预置 21 档（12 折扣券 98折~50折 + 4 立减 5~20 元 + 本次免单/免一 + 安慰奖余额 10/5 元 + 谢谢参与）+ 默认权重（私有100/公共0/大类50/50），开箱即用
- **并发限额修复**：开奖隔离级别 READ_COMMITTED（原 RR 快照导致限额并发超发）
- 大类空档自动回退（不再误报"尚未配置"）
