# -*- coding: utf-8 -*-
"""生成 ibigou-blindbox/frontend 三端 H5 骨架（顾客/商家/平台）。
规矩 #6：HTML 不用 heredoc，用 Python raw string 生成。"""
import os

BASE = r"D:\reasonix\ibigou-blindbox\frontend"
DOMAIN = "ybgtc.com"

README = """# frontend 三端 H5 骨架

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
"""

TPL = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title}</title>
<style>
  body {{ font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
         margin: 0; background: #f5f6f8; color: #222; }}
  .wrap {{ max-width: 720px; margin: 0 auto; padding: 40px 20px; text-align: center; }}
  h1 {{ font-size: 22px; }}
  .domain {{ color: #888; font-size: 13px; margin-top: 8px; }}
  .status {{ margin-top: 32px; padding: 16px; background: #fffbe6; border: 1px solid #ffe58f;
            border-radius: 8px; color: #ad6800; font-size: 14px; }}
  .list {{ margin-top: 24px; text-align: left; font-size: 14px; color: #555;
          background: #fff; border-radius: 8px; padding: 16px 24px; line-height: 2; }}
</style>
</head>
<body>
<div class="wrap">
  <h1>{title}</h1>
  <div class="domain">域名：{domain}</div>
  <div class="status">🛠 骨架占位页 —— 功能开发中，V1.4 需求见 docs/需求规格说明书-V1.4.md</div>
  <div class="list">
    {modules}
  </div>
</div>
</body>
</html>
"""

MODULES = {
    "customer": ("顾客 H5（手机端）", """
    <b>模块规划：</b><br>
    1. 盲盒抽奖（普通 / 美团专属 / 饿了么专属）<br>
    2. 用户钱包（优惠券分组、余额流水）<br>
    3. 宜必购商城（浏览、下单、订单、退款）
    """),
    "merchant": ("商家 H5 后台", """
    <b>模块规划：</b><br>
    1. 盲盒奖品池配置（私有 / 公共投放 / 专属盲盒）<br>
    2. 手工核销券、手工扣减余额<br>
    3. 流水报表、团购登记、宜必购渠道对账、Excel 导出
    """),
    "admin": ("软件公司平台后台（PC H5）", """
    <b>模块规划：</b><br>
    1. 商家账号管理<br>
    2. 全局参数（balance_deduct_rate / ibigou_channel_switch）<br>
    3. 公共池大盘、全平台报表、异常资产监控
    """),
}

def main():
    os.makedirs(os.path.join(BASE, "customer"), exist_ok=True)
    os.makedirs(os.path.join(BASE, "merchant"), exist_ok=True)
    os.makedirs(os.path.join(BASE, "admin"), exist_ok=True)

    with open(os.path.join(BASE, "README.md"), "w", encoding="utf-8") as f:
        f.write(README)

    for sub, (title, modules) in MODULES.items():
        html = TPL.format(title=title, domain=DOMAIN, modules=modules)
        with open(os.path.join(BASE, sub, "index.html"), "w", encoding="utf-8") as f:
            f.write(html)

    print("frontend skeleton generated:")
    for root, _dirs, files in os.walk(BASE):
        for fn in files:
            print("  ", os.path.relpath(os.path.join(root, fn), BASE))

if __name__ == "__main__":
    main()
