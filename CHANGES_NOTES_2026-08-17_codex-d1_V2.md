# CHANGES_NOTES · codex-d1 · V2 完全重设计

> 交付人：codex-d1  
> 交付日期：2026-08-17  
> 任务单：`Z:\ybg\ibigou-blindbox\docs\前端重设计任务单-for-codex-d1-V2.md`  
> 范围：8 个 HTML（顾客 3 + 商家 2 + 平台 2）+ 1 个设计规范；同步到 3 处（backend/static/h5、frontend、scripts/_templates）

## 1. 改了哪些文件

| 类型 | 路径 |
|---|---|
| 顾客 H5 | `backend/src/main/resources/static/h5/customer/{index,wallet,ibigou}.html` |
| 商家 H5 | `backend/src/main/resources/static/h5/merchant/{login,index}.html` |
| 平台 H5 | `backend/src/main/resources/static/h5/admin/{login,index}.html` |
| 模板源 | `scripts/_templates/{customer,merchant,admin}/*.html`（与运行副本同步） |
| 前端 | `frontend/{customer,merchant,admin}/*.html`（与运行副本同步） |
| 设计规范 | `frontend/设计规范.md`（v2 重写） |
| 交付说明 | `CHANGES_NOTES_2026-08-17_codex-d1_V2.md`（本文件） |

## 2. 功能覆盖清单（按 V2 任务单三、节逐项核对）

### 顾客端（3 页）

#### index.html 抽奖
- [x] 手机号验证码登录（`POST /api/customer/auth/send-code` + `auth/login`，测试码 123456，token `ibigou_user_token`）
- [x] 普通盲盒开奖（`POST /api/customer/draw/normal`），中奖底部 sheet 弹窗（emoji 跳跃 + `vibrate(80)`）
- [x] 团购登记（`POST /api/customer/group/register`，美团/饿了么 渠道 1/2 + 金额）
- [x] 模式A 下单（`offline/calc` 试算 → `offline/order` + `paidAmount 回填` → 收款码 sheet）
- [x] 模式B 下单（`offline/order-b` → `voucherToken` → `qr-svg` 凭证 sheet）
- [x] 退款（流水入口引导到 `wallet.html` 身份码 + 商家端 `order/{no}/refund`）
- [x] 本店订单流水列表（消费/退款）

#### wallet.html 钱包
- [x] 余额 / 券 / 流水
- [x] 动态码（`GET /qr/dynamic/{phone}` → `POST /qr-svg` 渲染 220px，120 秒自动刷新）
- [x] 身份码（`GET /identity-qr/{phone}` → `POST /qr-svg` 弹窗）
- [x] 优惠券 3 段（未使用/已使用/已过期）
- [x] 消息中心（`GET /messages/{phone}` + `/unread-count` + 单条 `/read` + 全部 `/read-all`）
- [x] 余额流水（最近 20 条）

#### ibigou.html 宜必购
- [x] 商品列表（`GET /ibigou/goods`）
- [x] 资产选择（券 + 余额 + `balanceDeductRate` 上限提示）
- [x] 下单（`POST /ibigou/order`：`couponId + deductBalance + orderAmount` → 返回 `payAmount`）
- [x] 订单列表 + 全额退款（`POST /ibigou/order/{no}/refund`，弹窗 confirm）

### 商家端（2 页）

#### login.html
- [x] 登录（`POST /api/merchant/auth/login` form：account+password，token `ibigou_merchant_token`）
- [x] 错误提示 + 回车提交
- [x] 测试账号提示 `m001 / smoke123`

#### index.html（8 Tab 全含）
1. [x] 概览：订单数/营业额/待确认/会员状态 + 最新播报
2. [x] 奖品池：
   - 门店抵扣参数（`balanceDeductPercent / dailyDeductLimit / receiveMode`）
   - 收款码上传（`POST /api/merchant/config/upload-qr`，multipart type=wechat|alipay + file）
   - 第一层权重 + 大类权重（`POST /weights`，四重校验提示）
   - 私有档位 CRUD（`prize-pools` enable/disable/delete/put-public）
   - 公共池上下架（`public-pools/{id}/up|down`）
   - 本店盲盒二维码（`qr-svg` 渲染 `https://ybgtc.com/h5/customer/index.html?merchantNo=...`）
3. [x] 专属盲盒：
   - 美团/饿了么档位（`group-pools`）
   - 大类权重（`group-pool-weights`）
   - 专属二维码（`group-qr?channel=`）
4. [x] 手工核销：
   - 券核销（`POST /coupon/verify-manual`，仅 couponId）
   - 余额扣减（`POST /balance/manual-deduct`，**仅 userPhone + orderAmount**，V1.5 修复）
   - 摄像头扫码（`getUserMedia` + 失败降级手动输入）
   - 动态码核验（`GET /qr/verify?qrToken=` + 列出可用券 + 一键核销）
   - 身份码核验（`GET /identity/verify?content=`，URL encode + 列历史订单 + 标记退款）
   - 模式B 凭证核验（`GET /order/voucher?voucherToken=` + `POST /order/{no}/confirm`）
5. [x] 流水报表：
   - 余额流水（`balance-flows?type=grant|consume&from=&to=` + 导出）
   - 团购登记（`group-records?channel=&from=&to=` + 导出）
   - 宜必购对账（`ibigou-recon?from=&to=` + 导出）
6. [x] 外来券（`external-coupons` + 导出）
7. [x] 会员续费：
   - 状态（`member/status` → state 0 试用/1 付费/2 过期 banner）
   - 套餐（`member/plan` → 动态文案 + 赠送活动提示）
   - 续费下单（`POST /member/renew-order`）+ 测试确认（`/confirm`，要求 `test_pay_confirm_enabled=1`）
   - 微信支付（`/pay` mock 提示）
   - 续费记录（`member/orders`）
8. [x] 订单收款：
   - 订单列表（`orders?userPhone=&date=` 含实付差异标红）
   - 录流水号（`POST /order/{no}/trade-no`）
   - 标记退款（`POST /order/{no}/refund`，弹窗 confirm）
   - 导出 Excel（`orders/export`）
- 播报：`announce/list` + `announce/poll?afterId=`（每 5s 增量）+ Web Speech API 朗读 + 开关
- 消息：`messages/unread-count` + `messages` + `messages/read-all`

### 平台端（2 页）

#### login.html
- [x] 登录（`POST /api/admin/auth/login`，token `ibigou_admin_token`）
- [x] 错误提示 + 回车提交
- [x] 测试账号提示 `admin / Admin@2026`

#### index.html（7 Tab 全含）
1. [x] 概览：商家总数/未处理告警/优惠券总数/有效会员
2. [x] 商家管理：
   - 新增（**merchantNo/merchantName/loginAccount/loginPwd 必填 + 密码 ≥6**）
   - 启用/禁用（`/enable`、`/disable`）
   - 重置密码（`/reset-pwd`）
   - **删除**（`/delete`，**`adminPassword` 必须正确**，弹窗 prompt）
3. [x] 全局参数：
   - `balance_deduct_rate` / `ibigou_channel_switch`
   - 微信支付 7 项 + `test_pay_confirm_enabled`
   - 全配置列表（`config/list`，密钥脱敏）
4. [x] 公共池大盘（`public-pool-board` + 导出）
5. [x] 全平台报表：
   - 券 / 流水 / 团购 / 宜必购（4 类）
   - 全部支持 Excel 导出
6. [x] 审计会员：
   - 会员列表（`member/list` + 状态 badge）
   - **人工调整有效期**（`POST /member/{no}/adjust`，months）
   - 续费订单（`member/orders`）
   - 审计日志（`member/audits` + 撤销 `/audits/{logId}/revoke`）
7. [x] 告警（`alerts` + 标记处理 `/handle`）

## 4. 自测结果

### 编码/语法
- 全部 8 文件 UTF-8 无 BOM（字节级校验）
- 全部 JS 通过 `node --check` 语法校验
- 全部 `<div>`/`<script>` 配对一致（顾客 56/56+1/1+39/39+1/1、商家 262/262+1/1、平台 146/146+1/1）
- 无 `\n` 字面量（按 V2 任务单硬约束）
- 无 `\uFFFD` 损坏字符

### 三处同步
- backend/static/h5/{customer,merchant,admin}/*.html ← 5+5 文件
- frontend/{customer,merchant,admin}/*.html ← 7 文件
- scripts/_templates/{customer,merchant,admin}/*.html ← 7 文件

### API 契约
- 全部请求 form-urlencoded（`URLSearchParams`），与后端 `@RequestParam` 一致
- 顾客 `X-User-Token` / 商家 `X-Merchant-Token` / 平台 `X-Admin-Token`
- GET 不带 body（`qr/dynamic`、`identity-qr`、`wallet`、`public-pool-board` 等）

### 已知限制
- 摄像头扫码（jsQR）未内联 JS，依赖浏览器调用 `getUserMedia`；失败降级手动输入（与 V2 任务单第 9 节工程约束一致）
- 微信支付 mock 模式按任务单约定走 `/confirm` 测试确认，需平台开 `test_pay_confirm_enabled=1`

## 5. 给 reasonix 的部署提示

1. 运行 `mvn -DskipTests package` 重新打包 backend（前端 HTML 已直接嵌入 `static/h5/`）
2. 测试服务器 `http://192.168.31.228:19085` 容器 `ibigou-blindbox-test`（不是 LensCabin 任何端口）
3. 自测账号：
   - 平台：`admin / Admin@2026`
   - 商家：`m001 / smoke123`
   - 顾客：任意手机号 + 验证码 `123456`
4. **不要在打包/部署期间运行外部同步任务**——本次任务期间检测到 Z: 盘上有外部同步进程（时间戳被刷新覆盖过我的写入），建议 reasonix 部署前先停 watcher，避免页面被旧版覆盖

## 6. 未在本任务单范围

- 部署与回归测试（reasonix 负责）
- 生成器脚本（`gen_frontend_v2.py` / `gen_frontend_v3.py`）未运行——本任务直接编辑了模板源（`_templates/`），下次合成时仍可由 reasonix 决定是否跑生成器链路
- 生产环境配置（`wx_pay_*`）保持占位，密钥不写入文件