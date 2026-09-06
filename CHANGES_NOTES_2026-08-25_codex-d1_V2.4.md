# YBG-V2.4 · codex-d1 改动说明（2026-08-25）

> 任务: 用户对话驱动（Hermes协调·盲盒前端微调）
> 执行: codex-d1
> 范围: `Z:\ybg\ibigou-blindbox\docs\知识图谱.md` + `outputs/` 三端页面（draw.html / pay.html / merchant.html / merchant/login.html / admin/login.html）
> 测试环境: http://127.0.0.1:8765（静态 8765 + mock 8766）
> 部署: codex-d1 **不自跑构建/部署**，由 reasonix 部署后回归验证

## 一、改动目的（对齐用户对话需求）

本轮 5 项前端微调 + 2 项登录契约修复，全部由用户在 Hermes 协调对话里提出。

### 1. draw.html 倒计时位置
- 反馈: 15s 倒计时压着礼盒
- 修复: `.countdown` 加 `transform: translateY(-80px)` 上移约 2.1cm，礼盒保持居中
- 不影响盒子的 perspective/rotate 动效

### 2. pay.html 完成支付静态图
- 反馈: "我已完成付款"按钮取消
- 修复: 删除 "我已完成付款" + "暂不付款" 两个按钮
- 新增 `.finish-tip` 卡片：金色虚线边框 + 信封 SVG + 绿色对勾 + 提示文字"请在上方选择支付方式"

### 3. merchant.html 奖品 Tab 乱码 (N35)
- 反馈: 商家后台奖品 Tab 显示乱码/空白
- 根因: API 返回 `prizeName/prizeEmoji/prizeId`，JS 渲染读 `p.name/p.emoji/p.id`，字段名不一致
- 修复: 全部加 fallback：`p.name||p.prizeName||"--"` / `p.emoji||p.prizeEmoji||"🎁"` / `p.id||p.prizeId||""`

### 4. merchant/login.html 登录契约修复 (N36)
- 反馈: "测试账号密码无法登录，提示网络异常"
- 根因1: login.html 直接 `fetch("/api/merchant/auth/login")` 走 8765 静态服务 → 404
- 根因2: mock 只认 `code:888888` 验证码，不认账号密码
- 修复:
  - 表单: 验证码登录 → **账号密码登录**（m001 / smoke123）
  - JS: 改用 `IBIGOU_API.merchant.login({account,password})` 走 api.js → 8766 mock
  - api.js `merchant.login` 扩展接受任意 body（兼容 code 或 {account,password}）
  - mock `/api/merchant/auth/login` 支持双方式
  - login.html 加 `?logout=1` 旁路参数

### 5. admin/login.html 登录契约修复 (N37)
- 反馈: "软件公司的也一样的问题"
- 根因: 同 N36
- 额外问题: mock 缺 admin 接口 + readBody 不支持 form-urlencoded
- 修复:
  - mock 加 `/api/admin/auth/login` (admin / Admin@2026)
  - mock 加 `/api/admin/merchant/list` / `/api/admin/config` / `/api/admin/audit/list`
  - mock `readBody` 升级支持 `application/x-www-form-urlencoded`
  - login.html fetch 改绝对 URL `http://127.0.0.1:8766/api/admin/auth/login`
  - login.html 加 `?logout=1` 旁路参数

## 二、改动文件清单

| 路径 | 改动 | 说明 |
|---|---|---|
| `outputs/draw.html` | 修改 | `.countdown` transform: translateY(-80px) |
| `outputs/pay.html` | 修改 | 删除两个按钮，新增 `.finish-tip` 卡片 + CSS |
| `outputs/merchant.html` | 修改 | 奖品字段 fallback（name/emoji/id ↔ prizeName/prizeEmoji/prizeId） |
| `outputs/merchant/login.html` | 修改 | 账号密码登录 + `?logout=1` 旁路 + 走 api.js |
| `outputs/admin/login.html` | 修改 | 改绝对 URL + `?logout=1` 旁路 |
| `outputs/api.js` | 修改 | `merchant.login(body)` 接受任意 body |
| `work/api-mock-server.js` | 修改 | readBody 支持 form-urlencoded + 加 admin 接口 + 商家登录双方式 |
| `docs/知识图谱.md` | **更新到 V2.4** | 端点数 107 → 110+，新增 V2.4 章节（登录契约 + mock 扩展） |
| `frontend/设计规范.md` | **更新到 v2.4** | 版本记录追加 V2.4 + 教训条目 |
| `docs/验收报告-前端重设计V2-2026-08-18.md` | **追加 V2.4 验收** | 新增 V2.4 节，本轮验收 |

**未改动**：`backend/`、`db/`、`nginx/`、`scripts/`、`docker-compose.yml`、所有部署/任务脚本。

## 三、对后端的契约期望（依赖 reasonix 落地）

| 项 | mock 演示 | 真后端期望 |
|---|---|---|
| 商家登录 `code` | 888888 | MerchantAuthService 支持验证码 |
| 商家登录 `{account,password}` | m001/smoke123 | MerchantAuthService.login() 新增账号密码分支（MerchantRepository.findByLoginAccount + passwordEncoder） |
| 平台登录 `{account,password}` | admin/Admin@2026 | AdminAuthService.login() 支持（adminUserRepository.findByLoginAccount） |
| X-Admin-Token | mock header 校验 | 平台端所有 /api/admin/** 加 AdminAuthInterceptor |

## 四、回归建议（reasonix 部署后必跑）

```bash
# 1. 静态页完整性
python scripts/smoke_test.py

# 2. 三端登录回归
# - 顾客 /login.html?logout=1 → 手机号 + 验证码 123456 → 跳转 /choose.html
# - 商家 /merchant/login.html?logout=1 → m001 + smoke123 → 跳转 /merchant/index.html
# - 平台 /admin/login.html?logout=1 → admin + Admin@2026 → 跳转 /admin/index.html

# 3. 商家奖品 Tab（修复后无乱码）
# - 登录 → "奖品池" Tab → 12 项档位正常显示

# 4. mock 服务器升级（开发环境）
# - 重启 work/api-mock-server.js（readBody + admin 接口）
```

## 五、风险与未做

- 风险: 本轮全部前端 + mock，后端 `MerchantAuthService.login()` / `AdminAuthService.login()` 仍只支持旧契约，需 reasonix 跟进扩展
- 未做: 未跑 `mvn install`、未重启容器、未部署 jar、未触生产
- 依赖: reasonix 完成 `MerchantAuthService` / `AdminAuthService` 账号密码分支后才能完整闭环

## 六、踩坑摘要（详见 Z:\workspace-java\踩坑日志.txt N31-N37）

- **N31**: 共享记忆已存在文件，不能在本地新建副本
- **N32**: 给文件路径前先 `fs.readdirSync` + HTTP 探测验证
- **N33**: 带自动跳转的登录页要加 `?logout=1` 旁路
- **N34**: 工作目录必须在 Z:\workspace-java 或收尾时同步过去
- **N35**: API契约字段名不一致时前端必须做兼容
- **N36**: 所有 /api/* 必须走 api.js (BASE=127.0.0.1:8766)，不能直接相对路径 fetch
- **N37**: mock 必须支持 form-urlencoded

—— codex-d1 (2026-08-25 17:06:08)
