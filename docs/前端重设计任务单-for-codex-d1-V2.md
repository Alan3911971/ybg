# 前端 H5 完全重设计任务单 V2（派给 codex-d1）

> 2026-08-17 | 背景：V1 美化稿合成后**手机端无法使用**（商家/平台 PC 优先、顾客端交互差），
> 用户拍板：**三端全部按手机端 H5 完全重新设计**，PC 仅兼容降级。

## 〇、硬约束（同 V1，务必遵守）

1. **全新独立项目，与 LensCabin 完全无关**——「宜必购盲盒优惠券 & 余额系统」。
2. **你只做设计，交付后通知 reasonix 合成打包部署**——不要自己打包/部署/跑同步脚本。
3. **存储与测试环境**：项目目录 `Z:\ybg\ibigou-blindbox`（NAS `/volume1/Download/ybg/ibigou-blindbox`）；
   测试服务器 **`http://192.168.31.228:19085`**（容器 `ibigou-blindbox-test`），**不是** LensCabin 的任何端口。
4. 文件 UTF-8 无 BOM；内嵌 JS 多行文本用**模板字符串**，禁 `\n` 字面量（用 `String.fromCharCode(10)`）。

## 一、设计目标（本次核心）

**Mobile-First 手机端 H5**：
- 基准宽度 **375px**，向下兼容 320px，向上 PC 自适应（≥768px 居中容器）
- 三端统一移动端交互：底部 Tab / 卡片流 / 大按钮 / 手势友好 / safe-area 适配
- 顾客端：盲盒抽奖（普通+团购）、钱包（动态码/身份码/券/流水/消息）、宜必购商城（商品/订单/退款）——**全流程手机可完成**
- 商家端：手机可完成**全部 7 大功能**（奖品池/专属盲盒/手工核销/流水报表/外来券/会员续费/订单列表）+ 收款码上传 + 核销扫码
- 平台端：手机可完成**全部 7 大功能**（商家管理/全局参数/公共池/报表/审计/会员管理/告警）
- **不允许"只有布局没有功能"**：每个页面必须有对应 API 调用与交互逻辑（参照下方功能清单）

## 二、文件位置（必读）

| 类型 | 路径 |
|---|---|
| 项目根 | `Z:\ybg\ibigou-blindbox` |
| 需求文档 | `docs\需求规格说明书-V1.5-最终定稿.md` |
| 知识图谱（API/表/服务索引） | `docs\知识图谱.md` |
| **当前运行页面（功能参照，勿照抄布局）** | `backend\src\main\resources\static\h5\{customer,merchant,admin}\*.html` |
| 上一版设计稿（可参考风格，本次重做） | `frontend\*.html`、`frontend\设计规范.md` |
| 生成器（模板分发模式） | `scripts\gen_frontend_v2.py`（顾客）、`scripts\gen_frontend_v3.py`（商家+平台） |
| 模板源（**本次改这里**） | `scripts\_templates\{customer,merchant,admin}\*.html` |
| 测试入口 | `http://192.168.31.228:19085/h5/...`（账号：平台 admin/Admin@2026、商家 m001/smoke123、顾客验证码 123456） |

## 三、功能清单（每页必须全含，禁止缺 Tab/缺功能）

### 顾客端（3 页）
- **index.html 抽奖**：手机号验证码登录（send-code + login，token 存 localStorage）、普通盲盒开奖（draw/normal）、
  团购登记（group/register，选渠道+金额）、中奖弹窗、本店下单（offline/calc 试算 + offline/order + 收款码展示 + 回填实付）、退款（offline/order/{no}/refund）
- **wallet.html 钱包**：余额/券/流水、动态核销码（qr/dynamic + qr-svg 渲染）、身份码（identity-qr + qr-svg）、
  优惠券列表（可核销/已核销/过期）、消息中心（messages 未读数+列表+已读）、跨店返还余额
- **ibigou.html 宜必购**：商品列表（ibigou/goods）、资产选择（券+余额）、下单（ibigou/order）、订单列表+退款（ibigou/order/{no}/refund）

### 商家端（1 页 index.html + login，**7 大功能全含**）
1. 奖品池：本店普通档位 CRUD + 私有/公共权重 + **本店专属盲盒二维码（qr-svg 渲染）**
2. 专属盲盒：美团/饿了么档位 + 大类权重 + 专属二维码
3. 手工核销/扣减：券核销（verify-manual）、余额扣减（manual-deduct）、扫码识别、动态码核验（qr/verify）+ 确认完成
4. 流水报表：订单列表（含实付差异标红）、导出 Excel
5. 外来券：查看外来券
6. 会员续费：状态/套餐/续费下单/到期提醒
7. 订单列表 + 收款码上传（微信/支付宝）+ 门店参数（抵扣比例/单日上限/收款模式）
8. 播报：announce/list + poll（音箱/页面播报）

### 平台端（1 页 index.html + login，**7 大功能全含**）
1. 商家管理：新增（**必填校验**）/启用禁用/重置密码/**删除（管理员密码确认）**
2. 全局参数：全局配置列表+修改（脱敏）
3. 公共池大盘：公共档位列表+导出
4. 全平台报表：券/余额流水/团购/宜必购订单 + Excel 导出
5. 商家配置审计：按商家查配置变更
6. 会员管理：会员列表/**人工调整有效期（+月数）**/续费订单/审计日志+撤销
7. 告警：告警列表/标记处理

## 四、API 契约（不要改）

- 请求头：顾客 `X-User-Token`、商家 `X-Merchant-Token`、平台 `X-Admin-Token`（登录后 localStorage）
- 二维码渲染：调 `POST /api/merchant/config/qr-svg` 或 `POST /api/customer/qr-svg`（content→{svg}，内联显示）
- 金额字段：decimal 字符串；折扣券 prizeType=1（折扣率 0-10）、立减 2、普通余额 3、团购免单 4

## 五、交付物

1. `scripts\_templates\{customer,merchant,admin}\*.html`（**7 个模板全量重做，mobile-first，功能完整**）
2. 运行 `gen_frontend_v2.py` + `gen_frontend_v3.py` 生成到 `frontend\`（验证生成链路）
3. `frontend\设计规范.md` 更新 v2（移动端规范）
4. `CHANGES_NOTES_*.md` 交付说明（改了哪些、功能覆盖清单、自测结果）
5. **自测**：浏览器手机模拟（375px）逐页走通主要流程，无 JS 报错
6. 完成后**通知 reasonix 合成打包部署回归**

## 六、验收标准

- 三端 7 页 375px 手机完全可用（手指操作顺畅、无横向滚动、按钮可点）
- **全部功能块存在且调通 API**（禁止缺 Tab/缺功能/假按钮）
- 顾客抽奖→下单→退款、商家核销→报表、平台管理→会员调整 核心链路可用
- JS 无语法错误、无 console 报错
