# frontend 三端 H5 骨架

产品域名：**https://ybgtc.com**（二维码 / H5 链接统一用域名，禁止硬编码 IP）

```
frontend/
├── customer/   # 顾客 H5（手机端）：盲盒抽奖、钱包、宜必购商城/订单/退款
├── merchant/   # 商家 H5 后台（PC + 手机）：盲盒配置、核销、流水报表、对账
└── admin/      # 软件公司平台后台（PC H5）：商家管理、全局参数、全平台报表、异常监控
```

技术说明：
- 全部端均为 H5，无任何 APP（需求 V1.4）
- 骨架阶段为静态占位页；API 由 backend 提供，联调路径见 `backend/` 下 service/controller README
