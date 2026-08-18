# service 层（业务逻辑）

按需求 V1.4 业务模块规划，下一轮开发逐个实现：

| 模块 | 核心职责 | 关键约束 |
|---|---|---|
| DrawService | 盲盒开奖（普通/美团/饿了么）、权重随机、限额行锁 | 新奖品 `can_use_after_draw=0`；公共池投放商家不可抽自己的档位 |
| PrizePoolService | 私有池/公共池/专属池配置、投放上架下架 | 私有与公共权重不可同时为 0；不允许全部大类禁用 |
| CouponService | 券生命周期：发放/核销/过期/作废/恢复 | 线下核销须 `source_merchant_no` 匹配；宜必购看 `is_support_ibigou` |
| BalanceService | 余额发放/扣减/退款，流水落账 | 流水禁物理删除；扣减上限 = 优惠后实付 × `balance_deduct_rate` |
| FlowCloseService | 流程闭环：抽奖产出全部资产置 `can_use_after_draw=1` | 三种闭环：自营下单成功 / 团购登记保存 / 关闭会话 |
| GroupRecordService | 美团/饿了么团购登记 | 只登记不真实核销；后端拦截券/余额参数 |
| IbigouService | 宜必购下单/退款（事务） | 总开关校验；全额退款券恢复、部分退款券作废 |
| ManualService | 商家手工核销券、手工扣减余额 | verify_type=2；退款不自动回退，人工处理 |
| ReportService | 商家报表/平台大盘/异常资产监控，Excel 导出 | 全部支持筛选导出 |
| JobService | 定时任务：过期券标记、can_use_after_draw 补偿 | 补偿识别闭环业务批量置 1 |
