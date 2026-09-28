# YBG-C-FLOW-001 · codex-d1 改动说明（2026-08-19）

> 任务ID: YBG-C-FLOW-001
> 执行: codex-d1（前端）
> 范围: `frontend/customer/index.html` 单一文件
> 部署: codex-d1 **不自跑构建/部署**，由 reasonix 部署后回归验证
> 测试环境: `http://192.168.31.228:19085`（容器 `ibigou-blindbox-test`）

## 一、改动目的（对齐任务单）

任务单（`docs/YBG-C-FLOW-001-20260818.md`）要求 C 端四渠道极简抽奖流程：
四渠道统一先开盲盒 → 输金额 → 本店走算价+扫码付款 / 第三方走登记 → 闭环 → 第三方按奖品折算展示返还余额。

`index.html` 在改动前**已实现核心链路**（扫码 `?m=`、`?channel=`、开盲盒 `/draw/normal` 与 `/draw/group`、本店付款 `/offline/calc`+`/offline/order`、第三方登记 `/group/register`），本次仅补齐任务单要求的**展示层**4 项：

1. **渠道四选组件**（不重排已有 groupSeg，仅在切换时同步 `window.__cflowChannel`）
2. **抽奖页渠道专属提示**（团购不可抵扣，奖励下次本店自营/宜必购渠道可用）
3. **金额输入页折算占位**（隐藏占位 `groupConversionHint`，等抽奖返回奖品后动态显示）
4. **闭环结果页折算说明**（在 `prizeWarn` 末尾追加"折算预告"，仅当第三方渠道且 `prizeType` 已知时显示）

## 二、改动文件清单

| 路径 | 改动 | 说明 |
|---|---|---|
| `frontend/customer/index.html` | 修改 | 新增 3 个 id（`groupHint` / `groupConversionHint` / `groupConversionText`），新增 1 个函数（`cflowUpdateHint`），在 `showPrize` 内追加 cflow 捕获 + 折算预告；groupSeg 切换同步 `window.__cflowChannel` |

**未改动**：`backend/`、`db/`、`nginx/`、`scripts/`、`docs/`、`docker-compose.yml`、`.env*`、所有部署/任务脚本。**严格遵守**交接单第七节"未做"清单。

## 三、新增 DOM（标识 `data-cflow` 便于回归）

```html
<!-- groupSeg 后 -->
<div id="groupHint" class="tip-line" data-cflow="group-hint">本次团购不可抵扣，奖励下次本店自营或宜必购渠道可用。</div>

<!-- groupAmount 输入框下方 -->
<div id="groupConversionHint" class="tip-line" data-cflow="group-conv-hint" style="display:none;">
  当前折算规则：<span id="groupConversionText">请先抽奖获得奖品</span>
</div>
```

## 四、新增/修改的 JS 行为

- **window.__cflowChannel**：渠道状态（0 本店 / 1 美团 / 2 饿了么 / 3 抖音），由 `qrCh` 初始化、由 groupSeg click 切换
- **window.__cflowDiscountPct**：当前奖品折算比例（折扣券=N折、谢谢=5%；其他类型设为 -1 表示已发放不再叠加）
- **cflowUpdateHint(ch)**：根据渠道切换 `groupHint` 文案（团购 / 本店两套）
- **showPrize(d)** 末尾追加：根据 `__cflowChannel` 与 `__cflowDiscountPct` 在 `prizeWarn` 末尾追加"【折算预告】…"段

## 五、对后端的契约期望（依赖 reasonix 落地的 `YBG-C-RETURN-001`）

| 字段 | 当前调用 | 期望后端 |
|---|---|---|
| `returnBalance`（`/group/register` 响应体） | 已在 toast 显示 | reasonix 实际发余额 |
| `returnDesc`（同上） | 已在 toast 显示 | reasonix 实际发描述 |
| `prizeType`（开团抽奖响应） | 已知 | 无需新加 |
| `prizeValue`（同上） | 已知 | 无需新加 |

后端如果未落地 `returnBalance/returnDesc`，前端**降级**：toast 仍按现有"团购登记成功，奖品已激活"提示；`groupConversionHint` 占位不显示；`prizeWarn` 仍可显示折算预告但金额为 0。

## 六、回归建议（reasonix 部署后必跑）

```bash
# 1. 文件完整性（id/字段未破坏）
python scripts/smoke_test.py
python scripts/smoke_v15_p0.py
python scripts/smoke_v15_p1.py
python scripts/smoke_v15_p2.py

# 2. 三端同步 md5 校验（与 codex-d1 V2.1 一致）
python scripts/verify_final.py

# 3. 手测（理由由 reasonix 验收）
# - 扫码 ?m=XXX&channel=1 → groupSeg 自动选中"美团"；groupHint 显示团购提示
# - 输金额 → 抽奖 → prizeWarn 显示"【折算预告】抽中7折！…"或"【折算预告】抽中 谢谢 …"
# - 登记 → toast 显示 returnBalance（如有）
# - 切到 channel=2/3 重复验证
```

## 七、风险与未做

- 风险：本任务**未触达真实资金链路**，仅展示层；纯前端改造可热重载验证
- 未做：未跑 `mvn install`、未重启容器、未部署 jar、未触生产（按交接单硬约束）
- 依赖：reasonix 完成 `YBG-C-RETURN-001` 后才能完整闭环第三方"按奖品折算"展示

—— codex-d1 (2026-08-19)