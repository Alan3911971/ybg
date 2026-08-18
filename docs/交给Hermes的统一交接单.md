# 宜必购盲盒系统 · 交给 Hermes 的统一交接单

> 2026-08-17 | 用户拍板：前端重设计 + 系统交接 + 测试部署授权，**全部由 Hermes 统一协调处理**。

## 一、交接内容（4 项）

1. **代码**：完整项目 `D:\reasonix\ibigou-blindbox`（NAS 权威：`/volume1/Download/ybg/ibigou-blindbox`，即 Z:\ybg\ibigou-blindbox）——170 文件，D/Z 已同步一致
2. **文档**：`docs\` 下 12 份（需求 V1.4/V1.5 定稿、差距分析、专业评审、知识图谱、部署手册、合规、微信支付指引、交接文档、V2 任务单、本交接单）
3. **文件位置**：见下文第二节（Hermes 派发 codex-d1 时可直接引用）
4. **测试服务器部署授权 + 系统全景交接**：已写入给 codex-d1 的文档（可直接复用）

## 二、文件位置速查（派发 codex-d1 用）

```
项目根（NAS 权威）   Z:\ybg\ibigou-blindbox = /volume1/Download/ybg/ibigou-blindbox
项目根（本地副本）   D:\reasonix\ibigou-blindbox（与 NAS 双向同步）
系统全景交接文档     docs\系统全景交接文档-for-codex-d1.md（codex-d1 完整了解系统：架构/24表/业务规则10条/API/测试环境/部署流程/纪律）
V2 任务单           docs\前端重设计任务单-for-codex-d1-V2.md（三端 375px 手机端完全重设计 + 全功能清单 + 验收标准）
需求基线            docs\需求规格说明书-V1.5-最终定稿.md
知识图谱（API 索引） docs\知识图谱.md / 知识图谱.json
当前运行页面        backend\src\main\resources\static\h5\{customer,merchant,admin}\*.html（改这里打包即生效）
前端模板源          scripts\_templates\{customer,merchant,admin}\*.html（生成器路径，生成后须复制回 static/h5）
生成器              scripts\gen_frontend_v2.py / gen_frontend_v3.py
冒烟脚本（14 个）   scripts\smoke_*.py（API 回归，需先 cat scripts\reset_test_data.sql 重置）
重置 SQL            scripts\reset_test_data.sql
测试服务器          http://192.168.31.228:19085（容器 ibigou-blindbox-test）
部署产物            /volume1/Download/ybg/deploy/ibigou.jar + schema.sql
微信证书            /volume1/Download/ybg/certs/（商户号 1440071102，权限 700/600）
```

## 三、给 Hermes 的待办清单

| # | 事项 | 负责 | 状态 |
|---|---|---|---|
| 1 | 派 codex-d1 执行 V2 手机端重设计（任务单+全景文档已备好，协作约定已写入 codex-d1-inbox） | Hermes 协调 | ⏳ 待派发 |
| 2 | codex-d1 改代码 + 上传测试服务器部署（授权已给，流程见全景文档第七节） | codex-d1 | ⏳ |
| 3 | reasonix 合成打包部署回归 + 验收 | reasonix | ⏳ codex-d1 交付后 |
| 4 | 微信支付联调：**待用户提供 app_id**（证书已就绪） | 用户/产品 | ⏳ 阻塞 |
| 5 | 生产部署（门禁规矩 #22/#119：测试全绿 + 用户明示 + Hermes 执行） | Hermes | ⏳ |

## 四、当前基线（不要回退，codex-d1 已被告知）

- 抖音渠道（渠道 3）已上线：枚举/校验/前端/专属二维码（404 已修复）/顾客扫码自动选渠道
- 商家删除需管理员密码、添加商家必填校验已上线
- 微信支付生产证书已就绪（商户号 1440071102，序列号 598230E1FFEBADCDA744EB31739DD843460232AE）
- 余额抵扣四重 min、跨店返还 5%、会员续费、收款模式 A/B、P0-P3 加固全部上线

## 五、协作约定（已写入 codex-d1-inbox，Hermes 监督执行）

分工：codex-d1 设计/实现 + reasonix 合成部署回归验收；双向同步 D↔Z；交付即通知（【交付】留言）；冲突以 V1.5 需求+知识图谱为准；基线不回退。
