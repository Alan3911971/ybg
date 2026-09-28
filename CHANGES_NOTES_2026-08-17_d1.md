# 前端美化交付说明（2026-08-17，codex-d1）

> 状态：交付完成，等待 reasonix 合成打包部署。
> 任务来源：`docs/前端美化任务单-for-codex-d1.md`
> 测试环境：`http://192.168.31.228:19085`（容器 `ibigou-blindbox-test`，非 LensCabin）

## 一、本次改动文件清单

| 类型 | 路径 | 状态 | 备注 |
|---|---|---|---|
| 新增 | `frontend/设计规范.md` | 新建 | 三端色板/字号/间距/组件/动效规范 |
| 修改 | `frontend/customer/index.html` | 美化 | 盲盒抽奖页（普通/团购两档入口 + 中奖弹窗 + 底部 Tab） |
| 修改 | `frontend/customer/wallet.html` | 美化 | 我的钱包（动态码 / 身份码 / 优惠券 / 流水 / 消息） |
| 修改 | `frontend/customer/ibigou.html` | 美化 | 宜必购商城（商品 / 资产 / 订单 + 全/部分退款） |
| 修改 | `frontend/merchant/login.html` | 美化 | 商家登录（含测试账号提示） |
| 修改 | `frontend/merchant/index.html` | 美化 | 商家主控台（5 Tab：奖品池/专属盲盒/手工核销扣减/流水报表/外来券） |
| 修改 | `frontend/admin/login.html` | 美化 | 平台登录 + 补充 logout JS（原 v3 缺） |
| 修改 | `frontend/admin/index.html` | 美化 | 平台主控台（5 Tab：商家账号/全局参数/公共池大盘/全平台报表/商家配置审计） |
| 修改 | `scripts/gen_frontend_v2.py` | 升级 v2.1 | BASE 改为 NAS `frontend/customer`；改读 `scripts/_templates/customer/*.html` 模板分发 |
| 修改 | `scripts/gen_frontend_v3.py` | 升级 v3.1 | BASE 改为 NAS `frontend/`；分发 `merchant/` 与 `admin/` 模板 |
| 新增 | `scripts/_templates/customer/*.html` | 新增 3 模板 | 顾客端三页模板源（v2 生成器读取源） |
| 新增 | `scripts/_templates/merchant/*.html` | 新增 2 模板 | 商家端模板源 |
| 新增 | `scripts/_templates/admin/*.html` | 新增 2 模板 | 平台端模板源 |

**未改动**：`backend/`、`db/`、`nginx/`、`scripts/smoke_*`、`docs/*`、`docker-compose.yml`、`Dockerfile`、所有 `.env*`、所有任务/部署脚本。

## 二、视觉设计要点

- **三色系**：顾客橙（#ff5b3e）、商家蓝（#2b6ef0）、平台靛蓝（#4338ca）。
- **统一元件**：CSS 变量（颜色、圆角、间距、阴影）+ 统一按钮/卡片/输入/弹窗/Toast/Loading 组件。
- **顾客端**：移动优先（375px 起）+ 渐变 hero 区 + 底部固定 Tab（抽盒/钱包/商城）+ 抽奖中奖弹窗带礼花 emoji + 振动反馈。
- **商家/平台端**：PC 优先 + 移动降级。Tabs 横向滚动 + sticky 顶部栏 + 圆角表格 + 操作链接主色。
- **可访问性**：系统字体栈开头、焦点发光、`prefers-reduced-motion` 降级、关键按钮不全隐藏。
- **不依赖**：零 CDN、零图标库、零付费素材，全部 emoji + 内联 SVG。
- **文件规范**：UTF-8 **无 BOM**、统一 7 个页面字符集经 `verify_final.py` 校验通过。

## 三、与原生成器的关系

- 原 `gen_frontend_v2.py` 与 `gen_frontend_v3.py` 都使用 Python 多行三引号内嵌 HTML。
- 本次重构为：**模板抽出到磁盘 → 生成器只负责分发**，更易 diff、更易审核，也避免了三引号转义陷阱。
- v2/v3 生成器保留环境变量覆盖：
  - `IBIGOU_FRONTEND_BASE`：覆盖输出根目录
  - `IBIGOU_TEMPLATE_BASE`：覆盖模板源根目录

## 四、回归说明（reasonix 部署后可重跑）

请部署后重跑以下冒烟脚本验证仍通过（**前端 DOM id/字段未破坏**）：

```
python scripts/smoke_test.py
python scripts/smoke_v15_p0.py
python scripts/smoke_v15_p1.py
python scripts/smoke_v15_p2.py
```

冒烟脚本只走 HTTP API，不依赖前端 DOM。

## 五、已知不影响功能但值得后续 follow-up

1. **原 v3 商家/平台只有 5 Tab**，任务单期望各 7 Tab（多「会员续费 / 订单列表 / 审计 / 告警」）。本次未增加 Tab 以遵守"不新增/删除功能"硬约束，后续由 reasonix 在补全业务时同步补全前端。
2. **平台登录页**：原 v3 没有 logout 按钮绑定，本次新增了 `ibigou_admin_token` 清除 + 跳转。
3. **格式兼容**：所有生成的 HTML 用 `viewport-fit=cover` 与 `env(safe-area-inset-bottom)`，iOS 适配更稳。
4. **emoji 二级菜单**：`dist/style/animation` 与图标库都未引入，保持轻量。

## 六、交付（请 reasonix 进行）

- ✅ 美化后 7 个页面
- ✅ `frontend/设计规范.md`
- ⏳ 合成 / 打包 / 部署：`codex-d1` **未自执行**，按你本人工作流处理
- ⏳ 部署后请顺手把 `NAS` 镜像的 `frontend/{customer,merchant,admin}/*.html`、`scripts/gen_frontend_v2.py`、`scripts/gen_frontend_v3.py`、`scripts/_templates/`、`frontend/设计规范.md` 全部推上去

## 七、未做

- ❌ 没有跑 `mvn` / `gradle` 打包 jar
- ❌ 没有重启 / 部署测试容器
- ❌ 没有碰 `Z:\workspace-java` / LensCabin 的任何路径（按硬约束）
- ❌ 没有改 `docs/` 下任何规范（修订请 reasonix 拍板）
- ❌ 没有改任何后端代码 / DB / 任务脚本

—— codex-d1