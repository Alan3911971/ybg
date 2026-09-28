# -*- coding: utf-8 -*-
"""生成 ibigou-blindbox 知识图谱（docs/知识图谱.json + docs/知识图谱.md）。
扫描 backend 源码：服务/仓储/实体/控制器/端点/枚举/定时任务/依赖注入关系。"""
import io
import json
import os
import re

BASE = r"D:\reasonix\ibigou-blindbox\backend\src\main\java\com\ibigou\blindbox"
DOC = r"D:\reasonix\ibigou-blindbox\docs"


def read(p):
    with io.open(p, encoding="utf-8") as f:
        return f.read()


def scan_class(path):
    c = read(path)
    name = os.path.splitext(os.path.basename(path))[0]
    # 注解
    annos = re.findall(r"@(\w+)(?:\([^)]*\))?", c)
    # 依赖注入字段
    deps = re.findall(r"private final (\w+) (\w+);", c)
    # 方法
    methods = re.findall(r"public\s+[\w<>\[\],\s]+\s+(\w+)\s*\(", c)
    methods = [m for m in methods if m not in ("main",)]
    # 定时任务
    jobs = re.findall(r"@Scheduled\(cron = \"([^\"]+)\"\)\s*\n\s*public void (\w+)", c)
    return name, annos, deps, methods, jobs


def main():
    graph = {"services": [], "repositories": [], "entities": [], "controllers": [],
             "enums": [], "config": []}

    # services
    for fn in sorted(os.listdir(os.path.join(BASE, "service"))):
        if not fn.endswith(".java"):
            continue
        p = os.path.join(BASE, "service", fn)
        name, annos, deps, methods, jobs = scan_class(p)
        graph["services"].append({
            "name": name,
            "type": "service",
            "injects": [d[1] for d in deps],
            "methods": methods,
            "scheduled": [{"cron": j[0], "method": j[1]} for j in jobs],
        })

    # repositories
    for fn in sorted(os.listdir(os.path.join(BASE, "repository"))):
        if not fn.endswith(".java"):
            continue
        c = read(os.path.join(BASE, "repository", fn))
        name = os.path.splitext(fn)[0]
        extends = re.search(r"extends\s+(\w+)<(\w+)", c)
        graph["repositories"].append({
            "name": name,
            "extends": extends.group(1) if extends else "",
            "entity": extends.group(2) if extends else "",
        })

    # entities（表映射）
    for fn in sorted(os.listdir(os.path.join(BASE, "entity"))):
        if not fn.endswith(".java"):
            continue
        c = read(os.path.join(BASE, "entity", fn))
        name = os.path.splitext(fn)[0]
        table = re.search(r'@Table\(name = "(\w+)"\)', c)
        graph["entities"].append({
            "name": name,
            "table": table.group(1) if table else "",
            "fields": len(re.findall(r"private \S+ \w+;", c)),
        })

    # controllers（端点）
    for fn in sorted(os.listdir(os.path.join(BASE, "controller"))):
        if not fn.endswith(".java"):
            continue
        p = os.path.join(BASE, "controller", fn)
        c = read(p)
        name = os.path.splitext(fn)[0]
        base_path = re.search(r'@RequestMapping\("([^"]+)"\)', c)
        endpoints = []
        for m in re.finditer(r"@(Get|Post|Put|Delete)Mapping\(\"([^\"]*)\"\)", c):
            endpoints.append({"method": m.group(1), "path": m.group(2)})
        deps = re.findall(r"private final (\w+) (\w+);", c)
        graph["controllers"].append({
            "name": name,
            "base": base_path.group(1) if base_path else "",
            "endpoints": endpoints,
            "injects": [d[1] for d in deps],
        })

    # enums
    for fn in sorted(os.listdir(os.path.join(BASE, "enums"))):
        if not fn.endswith(".java"):
            continue
        c = read(os.path.join(BASE, "enums", fn))
        name = os.path.splitext(fn)[0]
        values = re.findall(r"(\w+)\((\d+),", c)
        graph["enums"].append({"name": name, "values": [v[0] for v in values]})

    # 汇总统计
    graph["stats"] = {
        "services": len(graph["services"]),
        "repositories": len(graph["repositories"]),
        "entities": len(graph["entities"]),
        "controllers": len(graph["controllers"]),
        "enums": len(graph["enums"]),
        "endpoints": sum(len(ct["endpoints"]) for ct in graph["controllers"]),
        "scheduled_jobs": sum(len(s["scheduled"]) for s in graph["services"]),
    }

    # 写 JSON
    os.makedirs(DOC, exist_ok=True)
    with io.open(os.path.join(DOC, "知识图谱.json"), "w", encoding="utf-8") as f:
        json.dump(graph, f, ensure_ascii=False, indent=2)

    # 渲染 md
    md = []
    md.append("# 宜必购盲盒系统 知识图谱\n")
    md.append(f"> 自动生成 {len(graph['services'])} service / {len(graph['repositories'])} repo"
              f" / {len(graph['entities'])} entity / {len(graph['controllers'])} controller"
              f" / {len(graph['enums'])} enum / {graph['stats']['endpoints']} 端点"
              f" / {graph['stats']['scheduled_jobs']} 定时任务。修改前查此图谱定位影响范围。\n")
    md.append("## 1. 架构分层\n")
    md.append("```\n"
              "controller（API 入口）\n"
              "  └── service（业务逻辑，含鉴权拦截器校验）\n"
              "       ├── repository（JPA 数据访问，行锁/幂等删除）\n"
              "       └── entity（表映射）\n"
              "config（拦截器：CustomerAuth/AdminAuth） + common（Result/异常/加密）\n"
              "```\n")

    md.append("## 2. 服务与依赖注入（service → 注入项）\n")
    for s in graph["services"]:
        deps_txt = ", ".join(s["injects"]) if s["injects"] else "-"
        jobs_txt = "；".join(f"{j['method']}(cron={j['cron']})" for j in s["scheduled"])
        md.append(f"- **{s['name']}**：注入 {deps_txt}" + (f"；定时：{jobs_txt}" if jobs_txt else "") + "\n")

    md.append("\n## 3. 数据表 ↔ 实体\n")
    for e in graph["entities"]:
        md.append(f"- **{e['table']}** ← `{e['name']}`（{e['fields']} 字段）\n")

    md.append("\n## 4. Controller 端点\n")
    for ct in graph["controllers"]:
        md.append(f"### {ct['name']}（base={ct['base']}）\n")
        for ep in ct["endpoints"]:
            md.append(f"- `{ep['method']} {ct['base']}{ep['path']}`\n")

    md.append("\n## 5. 枚举\n")
    for e in graph["enums"]:
        md.append(f"- **{e['name']}**：{', '.join(e['values'])}\n")

    md.append("\n## 6. 修改影响速查\n")
    md.append("""- 改**表结构**：改 schema.sql（幂等）+ Flyway V2__ 迁移 + entity + repository
- 改**业务规则**（抵扣/有效期/返还）：OrderCalcService / MemberService / OfflineOrderService + 对应单测
- 改**接口**：controller + 冒烟脚本（scripts/smoke_*）
- 改**鉴权**：config/CustomerAuthInterceptor / AdminAuthInterceptor / config/WebConfig
- 改**金额**：balance/flow/order 三处一致性 + 审计撤销逻辑
- 关键**不可逆约束**：user_balance_flow 禁删（触发器）、负余额仅退款回滚、流水必须落账
""")
    with io.open(os.path.join(DOC, "知识图谱.md"), "w", encoding="utf-8") as f:
        f.write("".join(md))
    print("图谱生成完成:", graph["stats"])


if __name__ == "__main__":
    main()
