# 前端 H5 页面美化任务单（派给 codex-d1）

## 〇、⚠️ 先读这里（三条硬约束）

1. **这是一个全新独立项目，与 LensCabin 完全无关**：项目叫「宜必购盲盒优惠券 & 余额系统」，代码/文档/设计都以本项目为准，**不要套用 LensCabin 的任何业务、风格、约定**（虽然你做过 LensCabin 前端，但这是另一个系统）。
2. **你只做设计，交付后通知 reasonix 合成打包部署**：做完把改动留在项目目录（或告知改了什么），**不要自己打包 jar、不要自己部署容器、不要自己执行任何部署/同步脚本**。合并、构建、部署、回归由 reasonix 统一完成。
3. **存储与测试环境不要搞错**：
   - 项目目录：`Z:\ybg\ibigou-blindbox`（NAS `/volume1/Download/ybg/ibigou-blindbox`）——**不要**放/同步到 LensCabin 的 `Z:\workspace-java`、`lenscabin-platform`、`lenscabin-src` 等任何路径，也**不要**执行 LensCabin 的 `sync-to-nas.bat` 等同步机制。
   - 测试服务器：宜必购独立容器 **`http://192.168.31.228:19085`**（容器名 `ibigou-blindbox-test`）——**不是** LensCabin 的 19089/19090/8080/8081 等测试环境。浏览器访问、联调只认 19085。

## 一、任务背景

宜必购盲盒优惠券 & 余额系统（`Z:\ybg\ibigou-blindbox`，NAS `/volume1/Download/ybg/ibigou-blindbox`）功能已全部开发完成并测试通过（业务逻辑/API/鉴权均已就绪）。当前页面为**原生 HTML/JS 功能骨架**（能用但视觉朴素），需要你做 **UI/UX 美化设计**。

## 二、你的工作范围（只做设计，不动业务）

1. **视觉美化**：CSS 样式体系（主题色/字体/间距/卡片/按钮/表单）、页面布局、响应式适配（顾客端手机优先，商家/平台 PC+手机）
2. **交互增强**：盲盒开奖动效、按钮/卡片 hover、加载态、空状态、Toast 提示样式
3. **素材**：logo/图标（可用 emoji 或 SVG，不引入付费素材）
4. **视觉规范**：形成一份 `frontend/设计规范.md`（色板/字号/间距/组件样式，供后续统一）

**明确不做**：
- ❌ 不改后端 API、不改业务逻辑（抵扣/抽奖/核销/鉴权等全部现成）
- ❌ 不新增/删除页面功能
- ❌ 不改 `db/schema.sql`、不碰数据库

## 三、页面清单与访问地址（测试环境）

| 端 | 页面 | 地址 |
|---|---|---|
| 顾客 | 盲盒抽奖 | `http://192.168.31.228:19085/h5/customer/index.html?merchantNo=M001` |
| 顾客 | 我的钱包（含动态码/身份码/消息） | `.../h5/customer/wallet.html` |
| 顾客 | 宜必购商城 | `.../h5/customer/ibigou.html` |
| 商家 | 登录 + 主控台（7 Tab：奖品池/专属盲盒/手工核销/流水报表/外来券/会员续费/订单列表） | `.../h5/merchant/login.html`、`.../h5/merchant/index.html` |
| 平台 | 登录 + 主控台（7 Tab：商家管理/全局参数/公共池/报表/审计/会员/告警） | `.../h5/admin/login.html`、`.../h5/admin/index.html` |

**测试账号**：平台 `admin/Admin@2026`；商家 `m001/smoke123`；顾客：任意手机号 + 验证码 `123456`（测试固定码）

## 四、⚠️ 关键工程约束（务必遵守）

1. **页面由 Python 生成器产出**（`scripts/gen_frontend_v2.py` 顾客端 / `scripts/gen_frontend_v3.py` 商家+平台）：
   - 页面 HTML 是生成器的产物，**直接改 static/ 下 HTML 会被下次生成覆盖**
   - 你应**修改生成器脚本**（`gen_frontend_v2.py` / `gen_frontend_v3.py`），改完运行 `python scripts/gen_frontend_v2.py` 与 `python scripts/gen_frontend_v3.py` 重新生成
   - 若你想直接改 HTML 也可以，但**必须在任务记录里注明"已直接改 HTML，勿重新生成"**，否则会丢失
2. **HTML 内嵌 JS 用模板字符串（反引号）承载多行文本**，禁止 `\n` 字面量（heredoc 转义会坏，用 `String.fromCharCode(10)` 或模板字符串）——这是本项目踩过的坑
3. **前端登录机制已实现**（顾客短信验证码 token、商家/平台 token），样式美化时**不要破坏鉴权请求逻辑**（`X-User-Token` / `X-Merchant-Token` / `X-Admin-Token` 请求头）
4. **全部端为 H5，无 APP**；顾客端手机浏览器优先，商家/平台 PC+手机
5. 文件编码 UTF-8 无 BOM（与现有一致）
6. 页面可访问性：中文字体栈 `-apple-system,"PingFang SC","Microsoft YaHei",sans-serif`；勿用 `display:none` 隐藏关键操作

## 五、交付物

1. 美化后的三端页面（通过生成器重新生成或直接改 HTML，按上面约束）
2. `frontend/设计规范.md`（色板/字号/间距/组件/按钮/表单样式规范）
3. 自测：7 个页面在浏览器（手机模拟）打开无 JS 报错、无样式错乱、登录流程可用
4. **完成后通知 reasonix**（代码留在 `Z:\ybg\ibigou-blindbox` 并说明改了哪些文件/生成器），**由 reasonix 负责合成、打包、部署到测试容器并回归**——你不要自行部署

## 六、验收标准

- 三端 7 页面视觉统一、专业（盲盒/消费主题，活泼但不廉价）
- 顾客端手机适配良好（375px 起），商家/平台 PC 布局合理
- 登录、抽奖、钱包、订单、报表等**原有功能全部可用**（回归：跑一遍核心流程）
- 不破坏 `scripts/smoke_*.py` 冒烟脚本所依赖的 DOM 结构与请求逻辑

## 七、参考

- 需求与功能说明：`docs/需求规格说明书-V1.5-最终定稿.md`
- 知识图谱（改代码前查影响）：`docs/知识图谱.md`
- 生成器：`scripts/gen_frontend_v2.py`、`scripts/gen_frontend_v3.py`
