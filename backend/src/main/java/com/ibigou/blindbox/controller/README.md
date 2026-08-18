# controller 层（三端 API）

- `customer/`  顾客 H5 API：盲盒抽奖、钱包、宜必购商城/订单/退款
- `merchant/`  商家 H5 API：盲盒配置、公共券、外来券、手工核销/扣减、流水报表、团购登记、宜必购对账
- `admin/`     平台后台 API：商家账号、全局参数、公共池大盘、全平台报表、异常资产监控

通用校验（第 9 章）：
- 资产抵扣接口必须校验 `can_use_after_draw=1`
- 余额扣减 ≤ 优惠后实付 × balance_deduct_rate 且 ≤ 可用余额
- 宜必购接口第一步校验 `ibigou_channel_switch`
- 团购登记接口直接拦截优惠券 id / 余额扣减参数
