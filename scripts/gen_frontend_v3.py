# -*- coding: utf-8 -*-
"""生成商家 H5 后台 + 平台后台页面（登录页 + 主控台）。"""

import sys as _sys

import io
import os
# ===== 真防护（2026-08-18）：目标目录若已是 V2 页面，拒绝运行，防止覆盖 =====
import os as _os
_target = None
for _cand in ("D:/reasonix/ibigou-blindbox/backend/src/main/resources/static/h5",
              "Z:/ybg/ibigou-blindbox/backend/src/main/resources/static/h5"):
    if _os.path.isdir(_cand):
        _target = _cand
        break
if _target:
    for _probe in ("customer/index.html", "merchant/index.html", "admin/index.html"):
        _pf = _os.path.join(_target, _probe)
        if _os.path.isfile(_pf):
            _txt = io.open(_pf, encoding="utf-8", errors="ignore").read()
            if ("ibigou_last_draw" in _txt) or ("二维码已上传" in _txt):
                raise SystemExit("本生成器已废弃(V1时代硬编码)：目标为V2页面，禁止覆盖。请直接改 static/h5 并同步 frontend/_templates。")


BASE = r"D:\reasonix\ibigou-blindbox\backend\src\main\resources\static\h5"

MERCHANT_LOGIN = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>商家后台登录</title>
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; display:flex; align-items:center; justify-content:center; min-height:100vh; }
  .card { background:#fff; border-radius:14px; padding:28px; width:90%; max-width:360px; box-shadow:0 2px 8px rgba(0,0,0,.08); }
  h1 { font-size:19px; text-align:center; margin-bottom:20px; }
  input { width:100%; padding:11px; border:1px solid #ddd; border-radius:8px; font-size:15px; margin-bottom:12px; }
  .btn { width:100%; padding:12px; border:none; border-radius:8px; background:#1a73e8; color:#fff; font-size:15px; cursor:pointer; }
  .err { color:#c62828; font-size:13px; margin-bottom:10px; min-height:18px; }
</style>
</head>
<body>
<div class="card">
  <h1>🏪 商家 H5 后台</h1>
  <div class="err" id="err"></div>
  <input id="account" placeholder="登录账号">
  <input id="pwd" type="password" placeholder="登录密码">
  <button class="btn" id="btnLogin">登 录</button>
</div>
<script>
const $ = id => document.getElementById(id);
$("btnLogin").onclick = async () => {
  $("err").textContent = "";
  const body = new URLSearchParams({ account: $("account").value.trim(), password: $("pwd").value });
  const r = await fetch("/api/merchant/auth/login", { method: "POST", body }).then(r => r.json());
  if (r.code === 0) {
    localStorage.setItem("ibigou_merchant_token", r.data);
    location.href = "index.html";
  } else {
    $("err").textContent = r.msg || "登录失败";
  }
};
</script>
</body>
</html>
"""

MERCHANT_INDEX = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>商家后台</title>
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; color:#222; }
  .top { background:#1a73e8; color:#fff; padding:12px 16px; display:flex; justify-content:space-between; align-items:center; }
  .top b { font-size:16px; }
  .logout { color:#fff; text-decoration:underline; font-size:13px; cursor:pointer; }
  .tabs { display:flex; background:#fff; overflow-x:auto; border-bottom:1px solid #eee; position:sticky; top:0; z-index:10; }
  .tab { padding:12px 14px; font-size:13px; white-space:nowrap; cursor:pointer; color:#666; border-bottom:2px solid transparent; }
  .tab.active { color:#1a73e8; border-bottom-color:#1a73e8; font-weight:600; }
  .wrap { max-width:900px; margin:0 auto; padding:16px; }
  .panel { display:none; }
  .panel.active { display:block; }
  .card { background:#fff; border-radius:12px; padding:16px; margin-bottom:14px; box-shadow:0 1px 4px rgba(0,0,0,.06); }
  .sec { font-weight:600; margin-bottom:10px; }
  label { display:block; font-size:13px; color:#666; margin:8px 0 3px; }
  input, select { width:100%; padding:9px; border:1px solid #ddd; border-radius:8px; font-size:14px; }
  .row { display:flex; gap:8px; flex-wrap:wrap; }
  .row input, .row select { flex:1; min-width:120px; }
  .btn { padding:9px 14px; border:none; border-radius:8px; background:#1a73e8; color:#fff; font-size:14px; cursor:pointer; margin-top:8px; }
  .btn.orange { background:#ff6b35; }
  .btn.green { background:#2e7d32; }
  .btn.red { background:#c62828; }
  .btn.gray { background:#9aa0a6; }
  table { width:100%; border-collapse:collapse; font-size:13px; }
  th, td { border:1px solid #eee; padding:7px 8px; text-align:left; }
  th { background:#f8f9fa; }
  .op a { color:#1a73e8; margin-right:8px; cursor:pointer; text-decoration:none; }
  .warn { background:#fff3cd; border:1px solid #ffe58f; color:#8a5a00; padding:10px; border-radius:8px; font-size:12px; margin:10px 0; line-height:1.6; }
  .red-tip { color:#c62828; font-size:12px; }
</style>
</head>
<body>
<div class="top"><b>🏪 商家 H5 后台</b><span class="logout" id="logout">退出登录</span></div>
<div class="tabs" id="tabs">
  <div class="tab active" data-p="p1">奖品池配置</div>
  <div class="tab" data-p="p2">专属盲盒</div>
  <div class="tab" data-p="p3">手工核销/扣减</div>
  <div class="tab" data-p="p4">流水报表</div>
  <div class="tab" data-p="p5">外来券</div>
</div>
<div class="wrap">

<!-- ================= 奖品池配置 ================= -->
<div class="panel active" id="p1">
  <div class="card">
    <div class="sec">第一层权重（私有池 vs 公共池）</div>
    <div class="row">
      <div><label>私有池权重</label><input id="wPrivate" type="number" min="0"></div>
      <div><label>公共池权重</label><input id="wPublic" type="number" min="0"></div>
    </div>
    <div class="sec" style="margin-top:10px;">大类权重（折扣 vs 立减&余额）</div>
    <div class="row">
      <div><label>折扣大类权重</label><input id="wDiscount" type="number" min="0"></div>
      <div><label>立减&余额大类权重</label><input id="wCoupon" type="number" min="0"></div>
    </div>
    <button class="btn" id="btnWeights">保存权重</button>
    <div class="warn">私有池与公共池权重不能同时为 0；折扣大类与立减余额大类不能同时为 0。</div>
  </div>
  <div class="card">
    <div class="sec">档位管理（私有池）</div>
    <div class="row">
      <select id="fType"><option value="1">折扣券</option><option value="2">立减券</option><option value="3">普通余额</option><option value="4">团购免单余额</option></select>
      <input id="fValue" type="number" step="0.01" placeholder="优惠数值">
      <input id="fWeight" type="number" min="1" placeholder="权重">
    </div>
    <div class="row">
      <select id="fIbigou"><option value="1">支持宜必购</option><option value="0">仅线下</option></select>
      <select id="fScope"><option value="1">限额-平台全局</option><option value="2">限额-单商家</option><option value="3">限额-单用户</option></select>
      <select id="fCycle"><option value="3">周期-不限</option><option value="1">周期-每日</option><option value="2">周期-每周</option></select>
      <input id="fMax" type="number" min="0" placeholder="最大中奖次数(0不限)">
    </div>
    <button class="btn green" id="btnAddPrize">新增档位</button>
    <table id="prizeTable" style="margin-top:12px;"></table>
  </div>
  <div class="card">
    <div class="sec">我投放出去的公共券</div>
    <table id="publicTable"></table>
    <div class="warn">下架公共档位不影响已发放到用户手里的券。</div>
  </div>
</div>

<!-- ================= 专属盲盒 ================= -->
<div class="panel" id="p2">
  <div class="card">
    <div class="sec">美团 / 饿了么专属盲盒（独立奖品池）</div>
    <div class="row">
      <select id="gChannel"><option value="1">美团</option><option value="2">饿了么</option></select>
      <select id="gType"><option value="3">普通余额</option><option value="1">折扣券</option><option value="2">立减券</option><option value="4">团购免单余额</option></select>
      <input id="gValue" type="number" step="0.01" placeholder="优惠数值(折扣券填折扣率如8)">
      <input id="gWeight" type="number" min="1" placeholder="权重">
    </div>
    <button class="btn green" id="btnAddGroup">新增专属档位</button>
    <div class="sec" style="margin-top:10px;">大类权重（V1.5：折扣 vs 立减&余额；0/0=按档位权重直抽）</div>
    <div class="row">
      <div><label>折扣大类权重</label><input id="gDisc" type="number" min="0"></div>
      <div><label>立减&余额大类权重</label><input id="gCou" type="number" min="0"></div>
    </div>
    <button class="btn" id="btnGroupWeights" style="margin-top:8px;">保存大类权重</button>
    <table id="groupTable" style="margin-top:12px;"></table>
    <button class="btn" id="btnQr" style="margin-top:10px;">获取专属盲盒二维码链接</button>
    <div id="qrOut" class="warn" style="display:none;"></div>
    <div class="warn">美团/饿了么专属盲盒二维码专供团购到店客户；用户扫码后只需填写团购消费金额即可参与抽奖；本系统仅做业务登记、发放复购奖励，不会处理美团、饿了么团购券核销，团购券请商家在美团、饿了么 App 正常完成核销；所有盲盒奖励本次团购不可使用，可用于客户下次本店自营消费或宜必购渠道；两套盲盒奖品池独立，不和本店普通盲盒、公共奖品池互通。</div>
  </div>
</div>

<!-- ================= 手工核销/扣减 ================= -->
<div class="panel" id="p3">
  <div class="card">
    <div class="sec">优惠券手工扫码核销（异常兜底）</div>
    <div class="row">
      <input id="couponNo" placeholder="券编号 couponId">
      <button class="btn orange" id="btnVerifyCoupon">核销</button>
    </div>
    <button class="btn blue" id="btnScan" style="margin-top:8px;">📷 扫码识别券编号</button>
    <div class="warn">只有券的发行商家可以核销；外来公共券禁止手工核销。手工核销后退款不会自动退回优惠券，需平台人工处理。</div>
    <video id="scanner" style="width:100%;max-height:260px;border-radius:8px;display:none;margin-top:8px;" muted playsinline></video>
    <div id="scanTip" style="font-size:12px;color:#888;"></div>
  </div>
  <div class="card">
    <div class="sec">余额手工扣减</div>
    <div class="row">
      <input id="dPhone" placeholder="用户手机号">
      <input id="dAmount" type="number" step="0.01" placeholder="本次消费总金额">
      <input id="dDeduct" type="number" step="0.01" placeholder="扣减余额金额">
    </div>
    <button class="btn orange" id="btnDeduct">扣减</button>
    <div class="warn">余额最大抵扣比例由平台参数 balance_deduct_rate 统一控制，商家不可修改；手工扣减后退款不会自动回退余额。</div>
  </div>
  <div class="card">
    <div class="sec">本店可用券 / 用户资产查询</div>
    <div class="row">
      <input id="uPhone" placeholder="用户手机号">
      <button class="btn" id="btnUserAssets">查询</button>
    </div>
    <div id="userAssetsOut" style="font-size:13px;margin-top:10px;"></div>
  </div>
</div>

<!-- ================= 流水报表 ================= -->
<div class="panel" id="p4">
  <div class="card">
    <div class="sec">余额流水</div>
    <div class="row">
      <select id="flowType"><option value="grant">发放流水</option><option value="consume">消费流水</option></select>
      <input id="flowFrom" type="date">
      <input id="flowTo" type="date">
      <button class="btn" id="btnFlows">查询</button>
      <button class="btn gray" id="btnFlowsXlsx">导出Excel</button>
    </div>
    <div class="red-tip">当前余额抵扣百分比由平台管控（balance_deduct_rate），商家不可修改。</div>
    <table id="flowTable" style="margin-top:10px;"></table>
  </div>
  <div class="card">
    <div class="sec">第三方团购登记报表</div>
    <div class="row">
      <select id="grChannel"><option value="">全部渠道</option><option value="1">美团</option><option value="2">饿了么</option></select>
      <input id="grFrom" type="date">
      <input id="grTo" type="date">
      <button class="btn" id="btnGroupRecords">查询</button>
      <button class="btn gray" id="btnGroupXlsx">导出Excel</button>
    </div>
    <div class="warn">本记录仅我方平台登记存档，不会操作美团饿了么真实核销；第三方团购渠道不可使用平台余额、优惠券。</div>
    <table id="grTable" style="margin-top:10px;"></table>
  </div>
  <div class="card">
    <div class="sec">宜必购渠道消费对账</div>
    <div class="row">
      <input id="rbFrom" type="date">
      <input id="rbTo" type="date">
      <button class="btn" id="btnRecon">查询</button>
      <button class="btn gray" id="btnReconXlsx">导出Excel</button>
    </div>
    <table id="rbTable" style="margin-top:10px;"></table>
  </div>
</div>

<!-- ================= 外来券 ================= -->
<div class="panel" id="p5">
  <div class="card">
    <div class="sec">外来流入优惠券</div>
    <button class="btn" id="btnExternal">查询</button>
    <button class="btn gray" id="btnExternalXlsx">导出Excel</button>
    <table id="extTable" style="margin-top:10px;"></table>
    <div class="warn">此券为其他商家发行，本店仅可查看，请引导用户前往券所属商家门店或宜必购渠道处理。</div>
  </div>
</div>

</div>
<script>
const TOKEN = localStorage.getItem("ibigou_merchant_token");
if (!TOKEN) location.href = "login.html";
const H = { "X-Merchant-Token": TOKEN };
const CFG = "/api/merchant/config";
const M = "/api/merchant";
const $ = id => document.getElementById(id);

async function api(url, body, method) {
  const opt = { method: method || "POST", headers: { ...H } };
  if (body) {
    opt.headers["Content-Type"] = "application/x-www-form-urlencoded";
    opt.body = new URLSearchParams(body).toString();
  }
  return fetch(url, opt).then(r => r.json());
}
function get(url) { return fetch(url, { headers: H }).then(r => r.json()); }
function fmtT(t) { return (t || "").replace("T", " ").slice(0, 19); }
function dflt(from, to) {
  const d = new Date();
  return [from || (d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-01"),
          to || d.toISOString().slice(0, 10)];
}

// ---------- tab 切换 ----------
document.querySelectorAll(".tab").forEach(t => t.onclick = () => {
  document.querySelectorAll(".tab").forEach(x => x.classList.remove("active"));
  document.querySelectorAll(".panel").forEach(x => x.classList.remove("active"));
  t.classList.add("active");
  $("p" + t.dataset.p.slice(1)).classList.add("active");
});
$("logout").onclick = () => { localStorage.removeItem("ibigou_merchant_token"); location.href = "login.html"; };

// ---------- 权重 ----------
async function loadWeights() {
  const r = await get(CFG + "/weights");
  if (r.code !== 0) return;
  const m = r.data;
  $("wPrivate").value = m.privatePoolWeight;
  $("wPublic").value = m.publicPoolWeight;
  $("wDiscount").value = m.boxDiscountTotalWeight;
  $("wCoupon").value = m.boxCouponTotalWeight;
}
$("btnWeights").onclick = async () => {
  const r = await api(CFG + "/weights", {
    privatePoolWeight: $("wPrivate").value, publicPoolWeight: $("wPublic").value,
    boxDiscountTotalWeight: $("wDiscount").value, boxCouponTotalWeight: $("wCoupon").value });
  alert(r.code === 0 ? "✅ 权重已保存" : (r.msg || "保存失败"));
};

// ---------- 档位 ----------
async function loadPrizes() {
  const r = await get(CFG + "/prize-pools");
  const list = r.data || [];
  const tname = {1:"折扣券",2:"立减券",3:"普通余额",4:"团购免单余额"};
  $("prizeTable").innerHTML = "<tr><th>ID</th><th>类型</th><th>数值</th><th>权重</th><th>状态</th><th>宜必购</th><th>限额</th><th>操作</th></tr>" +
    list.map(p => `<tr><td>${p.prizeId}</td><td>${tname[p.prizeType]}</td><td>${p.prizeValue}</td>
      <td>${p.weight}</td><td>${p.enabled === 1 ? "启用" : "禁用"}</td>
      <td>${p.isSupportIbigou === 1 ? "支持" : "仅线下"}</td>
      <td>${p.limitScope}/${p.limitCycle}/${p.limitMax}</td>
      <td class="op">
        <a onclick="togglePrize(${p.prizeId}, ${p.enabled === 1 ? 0 : 1})">${p.enabled === 1 ? "禁用" : "启用"}</a>
        <a onclick="delPrize(${p.prizeId})">删除</a>
        <a onclick="putPublic(${p.prizeId})">投放公共池</a>
      </td></tr>`).join("");
}
async function togglePrize(id, en) { const r = await api(CFG + "/prize-pools/" + id + (en ? "/enable" : "/disable")); alert(r.code === 0 ? "ok" : r.msg); loadPrizes(); }
async function delPrize(id) { if (!confirm("确认删除档位？已发放券不受影响")) return; const r = await api(CFG + "/prize-pools/" + id, null, "DELETE"); alert(r.code === 0 ? "已删除" : r.msg); loadPrizes(); }
async function putPublic(id) { const r = await api(CFG + "/prize-pools/" + id + "/put-public"); alert(r.code === 0 ? "✅ 已投放公共池" : (r.msg || "失败")); loadPublics(); }
$("btnAddPrize").onclick = async () => {
  const r = await api(CFG + "/prize-pools", {
    prizeType: $("fType").value, prizeValue: $("fValue").value, weight: $("fWeight").value,
    isSupportIbigou: $("fIbigou").value, limitScope: $("fScope").value,
    limitCycle: $("fCycle").value, limitMax: $("fMax").value, isPutPublic: 0 });
  alert(r.code === 0 ? "✅ 档位已新增" : (r.msg || "失败"));
  loadPrizes();
};

// ---------- 公共投放 ----------
async function loadPublics() {
  const r = await get(CFG + "/public-pools");
  const list = r.data || [];
  $("publicTable").innerHTML = "<tr><th>ID</th><th>类型</th><th>数值</th><th>状态</th><th>宜必购</th><th>操作</th></tr>" +
    list.map(p => `<tr><td>${p.publicId}</td><td>${p.prizeType}</td><td>${p.prizeValue}</td>
      <td>${p.enabled === 1 ? "上架" : "下架"}</td><td>${p.isSupportIbigou === 1 ? "支持" : "仅线下"}</td>
      <td class="op"><a onclick="togglePub(${p.publicId}, ${p.enabled === 1 ? 0 : 1})">${p.enabled === 1 ? "下架" : "上架"}</a></td></tr>`).join("");
}
async function togglePub(id, en) { await api(CFG + "/public-pools/" + id + (en ? "/up" : "/down")); loadPublics(); }

// ---------- 专属盲盒 ----------
async function loadGroups() {
  const ch = $("gChannel").value;
  const r = await get(CFG + "/group-pools?channel=" + ch);
  const list = r.data || [];
  $("groupTable").innerHTML = "<tr><th>ID</th><th>类型</th><th>数值</th><th>权重</th><th>宜必购</th><th>操作</th></tr>" +
    list.map(p => `<tr><td>${p.groupPoolId}</td><td>${p.prizeType}</td><td>${p.prizeValue}</td>
      <td>${p.weight}</td><td>${p.isSupportIbigou === 1 ? "支持" : "仅线下"}</td>
      <td class="op"><a onclick="toggleGp(${p.groupPoolId}, ${p.enabled === 1 ? 0 : 1})">${p.enabled === 1 ? "禁用" : "启用"}</a>
      <a onclick="delGp(${p.groupPoolId})">删除</a></td></tr>`).join("");
}
async function toggleGp(id, en) { await api(CFG + "/group-pools/" + id + (en ? "/enable" : "/disable")); loadGroups(); }
async function loadGroupWeights() {
  const ch = $("gChannel").value;
  const r = await get(CFG + "/group-pool-weights?channel=" + ch);
  const cfg = r.data;
  $("gDisc").value = cfg ? cfg.discountTotalWeight : 0;
  $("gCou").value = cfg ? cfg.couponBalanceTotalWeight : 0;
}
$("gChannel").onchange = () => { loadGroups(); loadGroupWeights(); };
$("btnGroupWeights").onclick = async () => {
  const r = await api(CFG + "/group-pool-weights", {
    channel: $("gChannel").value, discountTotalWeight: $("gDisc").value, couponBalanceTotalWeight: $("gCou").value });
  alert(r.code === 0 ? "✅ 大类权重已保存" : (r.msg || "保存失败"));
};
async function delGp(id) { if (!confirm("确认删除？")) return; await api(CFG + "/group-pools/" + id, null, "DELETE"); loadGroups(); }
$("gChannel").onchange = loadGroups;
$("btnAddGroup").onclick = async () => {
  const r = await api(CFG + "/group-pools", {
    channel: $("gChannel").value, prizeType: $("gType").value, prizeValue: $("gValue").value,
    weight: $("gWeight").value, isSupportIbigou: 1 });
  alert(r.code === 0 ? "✅ 专属档位已新增" : (r.msg || "失败"));
  loadGroups();
};
$("btnQr").onclick = async () => {
  const ch = $("gChannel").value;
  const r = await get(CFG + "/group-qr?channel=" + ch);
  $("qrOut").style.display = "block";
  $("qrOut").textContent = "二维码内容链接：" + r.data;
};

// ---------- 手工核销 / 扣减 ----------
$("btnVerifyCoupon").onclick = async () => {
  const id = $("couponNo").value.trim();
  if (!id) { alert("请输入券编号"); return; }
  const r = await api(M + "/coupon/verify-manual", { couponId: id });
  alert(r.code === 0 ? "✅ 核销成功（verify_type=2）" : (r.msg || "核销失败"));
};
$("btnDeduct").onclick = async () => {
  const r = await api(M + "/balance/manual-deduct", {
    userPhone: $("dPhone").value.trim(), orderAmount: $("dAmount").value, deductAmount: $("dDeduct").value });
  alert(r.code === 0 ? "✅ 扣减成功" : (r.msg || "扣减失败"));
};
$("btnUserAssets").onclick = async () => {
  const p = $("uPhone").value.trim();
  if (!p) { alert("请输入手机号"); return; }
  const r = await get(M + "/user/" + p + "/assets");
  if (r.code !== 0) { alert(r.msg); return; }
  const a = r.data;
  $("userAssetsOut").innerHTML = `本店可用券: ${(a.offlineCoupons||[]).length} 张，宜必购券: ${(a.ibigouCoupons||[]).length} 张，余额: <b>${a.balance}</b> 元`;
};

// ---------- 报表 ----------
$("btnFlows").onclick = () => loadFlows(false);
$("btnFlowsXlsx").onclick = () => loadFlows(true);
async function loadFlows(xlsx) {
  const [f, t] = dflt($("flowFrom").value, $("flowTo").value);
  const type = $("flowType").value;
  if (xlsx) return downloadXlsx(CFG + "/balance-flows/export?type=" + type + "&from=" + f + "&to=" + t);
  const r = await get(CFG + "/balance-flows?type=" + type + "&from=" + f + "&to=" + t);
  const list = r.data || [];
  $("flowTable").innerHTML = "<tr><th>手机号</th><th>类型</th><th>金额</th><th>核销方式</th><th>单号</th><th>时间</th></tr>" +
    list.map(x => `<tr><td>${x.userPhone}</td><td>${x.flowType}</td><td>${x.amount}</td><td>${x.verifyType}</td><td>${x.bizNo || ""}</td><td>${fmtT(x.createTime)}</td></tr>`).join("");
}
$("btnGroupRecords").onclick = () => loadGr(false);
$("btnGroupXlsx").onclick = () => loadGr(true);
async function loadGr(xlsx) {
  const [f, t] = dflt($("grFrom").value, $("grTo").value);
  const ch = $("grChannel").value;
  if (xlsx) return downloadXlsx(CFG + "/group-records/export?channel=" + ch + "&from=" + f + "&to=" + t);
  const r = await get(CFG + "/group-records?channel=" + ch + "&from=" + f + "&to=" + t);
  const list = r.data || [];
  $("grTable").innerHTML = "<tr><th>手机号</th><th>渠道</th><th>团购金额</th><th>奖品摘要</th><th>登记时间</th></tr>" +
    list.map(x => `<tr><td>${x.userPhone}</td><td>${x.channel === 1 ? "美团" : "饿了么"}</td><td>${x.groupAmount}</td><td>${(x.prizeInfo || "").slice(0, 40)}</td><td>${fmtT(x.createTime)}</td></tr>`).join("");
}
$("btnRecon").onclick = () => loadRecon(false);
$("btnReconXlsx").onclick = () => loadRecon(true);
async function loadRecon(xlsx) {
  const [f, t] = dflt($("rbFrom").value, $("rbTo").value);
  if (xlsx) return downloadXlsx(CFG + "/ibigou-recon/export?from=" + f + "&to=" + t);
  const r = await get(CFG + "/ibigou-recon?from=" + f + "&to=" + t);
  const list = r.data || [];
  $("rbTable").innerHTML = "<tr><th>订单号</th><th>手机号</th><th>券/余额</th><th>实付</th><th>退款状态</th><th>时间</th></tr>" +
    list.map(x => `<tr><td>${x.ibigouOrderNo}</td><td>${x.userPhone}</td><td>券:${x.couponId || "-"} 余额:${x.deductBalance}</td><td>${x.payAmount}</td><td>${x.refundStatus}</td><td>${fmtT(x.createTime)}</td></tr>`).join("");
}

// ---------- 外来券 ----------
$("btnExternal").onclick = () => loadExt(false);
$("btnExternalXlsx").onclick = () => loadExt(true);
async function loadExt(xlsx) {
  if (xlsx) return downloadXlsx(CFG + "/external-coupons/export");
  const r = await get(CFG + "/external-coupons");
  const list = r.data || [];
  $("extTable").innerHTML = "<tr><th>券ID</th><th>手机号</th><th>来源商家</th><th>数值</th><th>状态</th><th>核销方式</th></tr>" +
    list.map(x => `<tr><td>${x.couponId}</td><td>${x.userPhone}</td><td>${x.sourceMerchantNo}</td><td>${x.prizeValue}</td><td>${x.status}</td><td>${x.verifyType}</td></tr>`).join("");
}

// ---------- Excel 下载（带 token 的 GET blob） ----------
async function downloadXlsx(url) {
  const r = await fetch(url, { headers: H });
  if (!r.ok) { alert("导出失败"); return; }
  const blob = await r.blob();
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "report.xlsx";
  a.click();
  URL.revokeObjectURL(a.href);
}

// ---------- 扫码核销（getUserMedia + jsQR） ----------
let scannerStream = null;
$("btnScan").onclick = async () => {
  const video = $("scanner");
  if (scannerStream) { scannerStream.getTracks().forEach(t => t.stop()); scannerStream = null; video.style.display = "none"; return; }
  try {
    scannerStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
    video.srcObject = scannerStream;
    video.style.display = "block";
    $("scanTip").textContent = "请将券二维码对准摄像头...（再次点击关闭）";
    tickScan();
  } catch (e) {
    alert("无法打开摄像头：" + e.message + "\n可用手动录入券编号代替");

  }
};
function tickScan() {
  const video = $("scanner");
  if (!scannerStream || video.readyState < 2) { setTimeout(tickScan, 500); return; }
  try {
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth; canvas.height = video.videoHeight;
    canvas.getContext("2d").drawImage(video, 0, 0);
    const code = jsQR(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data,
                      canvas.width, canvas.height);
    if (code && code.data) {
      const id = code.data.replace(/\D/g, "");
      if (id) { $("couponNo").value = id; $("scanTip").textContent = "已识别券编号 #" + id; }
    }
  } catch (e) { /* 单帧识别失败继续 */ }
  setTimeout(tickScan, 400);
}

// 初始化
loadWeights(); loadPrizes(); loadPublics(); loadGroups(); loadGroupWeights();
</script>
<script src="https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js"></script>
</body>
</html>
"""

ADMIN_LOGIN = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>平台后台登录</title>
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#263238; display:flex; align-items:center; justify-content:center; min-height:100vh; }
  .card { background:#fff; border-radius:14px; padding:28px; width:90%; max-width:360px; }
  h1 { font-size:19px; text-align:center; margin-bottom:16px; }
  .tip { font-size:12px; color:#888; text-align:center; margin-bottom:12px; }
  .btn { width:100%; padding:12px; border:none; border-radius:8px; background:#263238; color:#fff; font-size:15px; cursor:pointer; }
</style>
</head>
<body>
<div class="card">
  <h1>🏢 软件公司平台后台</h1>
  <div class="tip" id="err" style="color:#c62828;"></div>
  <input id="account" placeholder="管理员账号" style="width:100%;padding:11px;border:1px solid #ddd;border-radius:8px;font-size:15px;margin-bottom:12px;">
  <input id="pwd" type="password" placeholder="密码" style="width:100%;padding:11px;border:1px solid #ddd;border-radius:8px;font-size:15px;margin-bottom:12px;">
  <button class="btn" id="btnLogin">登 录</button>
</div>
<script>
const $ = id => document.getElementById(id);
$("btnLogin").onclick = async () => {
  $("err").textContent = "";
  const body = new URLSearchParams({ account: $("account").value.trim(), password: $("pwd").value });
  const r = await fetch("/api/admin/auth/login", { method: "POST", body }).then(r => r.json());
  if (r.code === 0) {
    localStorage.setItem("ibigou_admin_token", r.data);
    location.href = "index.html";
  } else {
    $("err").textContent = r.msg || "登录失败";
  }
};
</script>
</body>
</html>
"""

ADMIN_INDEX = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>平台后台</title>
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; color:#222; }
  .top { background:#263238; color:#fff; padding:12px 16px; }
  .top b { font-size:16px; }
  .tabs { display:flex; background:#fff; overflow-x:auto; border-bottom:1px solid #eee; }
  .tab { padding:12px 14px; font-size:13px; cursor:pointer; color:#666; border-bottom:2px solid transparent; white-space:nowrap; }
  .tab.active { color:#263238; border-bottom-color:#263238; font-weight:600; }
  .wrap { max-width:1000px; margin:0 auto; padding:16px; }
  .panel { display:none; }
  .panel.active { display:block; }
  .card { background:#fff; border-radius:12px; padding:16px; margin-bottom:14px; box-shadow:0 1px 4px rgba(0,0,0,.06); }
  .sec { font-weight:600; margin-bottom:10px; }
  label { display:block; font-size:13px; color:#666; margin:8px 0 3px; }
  input, select { padding:9px; border:1px solid #ddd; border-radius:8px; font-size:14px; }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:flex-end; }
  .btn { padding:9px 14px; border:none; border-radius:8px; background:#263238; color:#fff; font-size:14px; cursor:pointer; }
  .btn.gray { background:#9aa0a6; }
  .btn.green { background:#2e7d32; }
  table { width:100%; border-collapse:collapse; font-size:13px; }
  th, td { border:1px solid #eee; padding:7px 8px; text-align:left; }
  th { background:#f8f9fa; }
  .op a { color:#1a73e8; margin-right:8px; cursor:pointer; }
  .warn { background:#fff3cd; border:1px solid #ffe58f; color:#8a5a00; padding:10px; border-radius:8px; font-size:12px; margin:10px 0; }
</style>
</head>
<body>
<div class="top"><b>🏢 软件公司平台后台</b></div>
<div class="tabs" id="tabs">
  <div class="tab active" data-p="p1">商家账号管理</div>
  <div class="tab" data-p="p2">全局参数</div>
  <div class="tab" data-p="p3">公共池大盘</div>
  <div class="tab" data-p="p4">全平台报表</div>
  <div class="tab" data-p="p5">商家配置审计</div>
</div>
<div class="wrap">

<div class="panel active" id="p1">
  <div class="card">
    <div class="sec">新增商家账号</div>
    <div class="row">
      <input id="mNo" placeholder="商家编号">
      <input id="mName" placeholder="门店名称">
      <input id="mAcc" placeholder="登录账号">
      <input id="mPwd" type="password" placeholder="初始密码">
      <button class="btn green" id="btnAddMerchant">新增</button>
    </div>
  </div>
  <div class="card">
    <div class="sec">商家列表</div>
    <button class="btn" id="btnMerchants">刷新</button>
    <table id="mTable" style="margin-top:10px;"></table>
  </div>
</div>

<div class="panel" id="p2">
  <div class="card">
    <div class="sec">全局系统参数</div>
    <div class="warn">balance_deduct_rate=0 代表全平台关闭余额抵扣；ibigou_channel_switch=0 关闭宜必购渠道（所有端隐藏入口并拒绝下单）。</div>
    <label>余额最大抵扣百分比 balance_deduct_rate（0-100）</label>
    <input id="gRate" type="number" min="0" max="100">
    <label>宜必购渠道总开关 ibigou_channel_switch（0关闭/1开启）</label>
    <input id="gSwitch" type="number" min="0" max="1">
    <button class="btn" id="btnSaveConfig" style="margin-top:10px;">保存</button>
  </div>
</div>

<div class="panel" id="p3">
  <div class="card">
    <div class="sec">公共共享奖品池大盘</div>
    <button class="btn" id="btnBoard">刷新</button>
    <button class="btn gray" id="btnBoardXlsx">导出Excel</button>
    <table id="boardTable" style="margin-top:10px;"></table>
  </div>
</div>

<div class="panel" id="p4">
  <div class="card">
    <div class="sec">全平台报表（全部支持 Excel 导出）</div>
    <div class="row">
      <button class="btn" id="btnRCoupon">优惠券明细</button>
      <button class="btn gray" id="btnRCouponXlsx">导出</button>
      <button class="btn" id="btnRFlow">余额流水</button>
      <button class="btn gray" id="btnRFlowXlsx">导出</button>
      <button class="btn" id="btnRGrp">团购登记</button>
      <button class="btn gray" id="btnRGrpXlsx">导出</button>
      <button class="btn" id="btnRIbg">宜必购交易</button>
      <button class="btn gray" id="btnRIbgXlsx">导出</button>
    </div>
    <table id="reportTable" style="margin-top:10px;"></table>
  </div>
</div>

<div class="panel" id="p5">
  <div class="card">
    <div class="sec">商家配置只读审计（平台只能查看，不能修改商家业务配置）</div>
    <div class="row">
      <input id="auditNo" placeholder="商家编号">
      <button class="btn" id="btnAudit">查看</button>
    </div>
    <div id="auditOut" style="font-size:13px;margin-top:10px;"></div>
  </div>
</div>

</div>
<script>
const A = "/api/admin";
const TOKEN = localStorage.getItem("ibigou_admin_token");
if (!TOKEN) location.href = "login.html";
const H = { "X-Admin-Token": TOKEN };
const $ = id => document.getElementById(id);
async function api(url, body) {
  const opt = { method: "POST", headers: { ...H } };
  if (body) {
    opt.headers["Content-Type"] = "application/x-www-form-urlencoded";
    opt.body = new URLSearchParams(body).toString();
  }
  return fetch(url, opt).then(r => r.json());
}
function get(url) { return fetch(url, { headers: H }).then(r => r.json()); }
async function downloadXlsx(url) {
  const r = await fetch(url, { headers: H });
  if (!r.ok) { alert("导出失败"); return; }
  const blob = await r.blob();
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "report.xlsx";
  a.click();
  URL.revokeObjectURL(a.href);
}

document.querySelectorAll(".tab").forEach(t => t.onclick = () => {
  document.querySelectorAll(".tab").forEach(x => x.classList.remove("active"));
  document.querySelectorAll(".panel").forEach(x => x.classList.remove("active"));
  t.classList.add("active");
  $("p" + t.dataset.p.slice(1)).classList.add("active");
});

// ---------- 商家管理 ----------
async function loadMerchants() {
  const r = await get(A + "/merchant/list");
  const list = r.data || [];
  $("mTable").innerHTML = "<tr><th>编号</th><th>名称</th><th>账号</th><th>状态</th><th>操作</th></tr>" +
    list.map(m => `<tr><td>${m.merchantNo}</td><td>${m.merchantName}</td><td>${m.loginAccount}</td>
      <td>${m.status === 1 ? "启用" : "禁用"}</td>
      <td class="op">
        <a onclick="toggleM('${m.merchantNo}', ${m.status === 1 ? 0 : 1})">${m.status === 1 ? "禁用" : "启用"}</a>
        <a onclick="resetPwd('${m.merchantNo}')">重置密码</a>
      </td></tr>`).join("");
}
async function toggleM(no, en) { await api(A + "/merchant/" + no + (en ? "/enable" : "/disable")); loadMerchants(); }
async function resetPwd(no) {
  const p = prompt("输入新密码（至少6位）", "abc123");
  if (!p) return;
  const r = await api(A + "/merchant/" + no + "/reset-pwd", { newPwd: p });
  alert(r.code === 0 ? "✅ 已重置" : r.msg);
}
$("btnAddMerchant").onclick = async () => {
  const r = await api(A + "/merchant/create", {
    merchantNo: $("mNo").value.trim(), merchantName: $("mName").value.trim(),
    loginAccount: $("mAcc").value.trim(), loginPwd: $("mPwd").value });
  alert(r.code === 0 ? "✅ 商家已创建" : (r.msg || "创建失败"));
  loadMerchants();
};
$("btnMerchants").onclick = loadMerchants;

// ---------- 全局参数 ----------
async function loadConfig() {
  const r = await get(A + "/config/list");
  (r.data || []).forEach(c => {
    if (c.configKey === "balance_deduct_rate") $("gRate").value = c.configValue;
    if (c.configKey === "ibigou_channel_switch") $("gSwitch").value = c.configValue;
  });
}
$("btnSaveConfig").onclick = async () => {
  const r1 = await api(A + "/config/update", { key: "balance_deduct_rate", value: $("gRate").value });
  const r2 = await api(A + "/config/update", { key: "ibigou_channel_switch", value: $("gSwitch").value });
  alert(r1.code === 0 && r2.code === 0 ? "✅ 全局参数已保存" : (r1.msg || r2.msg || "保存失败"));
};

// ---------- 公共池大盘 ----------
$("btnBoard").onclick = () => loadBoard(false);
$("btnBoardXlsx").onclick = () => loadBoard(true);
async function loadBoard(xlsx) {
  if (xlsx) return downloadXlsx(A + "/public-pool-board/export");
  const r = await get(A + "/public-pool-board");
  const list = r.data || [];
  $("boardTable").innerHTML = "<tr><th>ID</th><th>投放商家</th><th>类型</th><th>数值</th><th>状态</th><th>宜必购</th></tr>" +
    list.map(p => `<tr><td>${p.publicId}</td><td>${p.sourceMerchantNo}</td><td>${p.prizeType}</td><td>${p.prizeValue}</td><td>${p.enabled === 1 ? "上架" : "下架"}</td><td>${p.isSupportIbigou === 1 ? "支持" : "仅线下"}</td></tr>`).join("");
}

// ---------- 报表 ----------
let reportMode = "coupons";
$("btnRCoupon").onclick = () => { reportMode = "coupons"; loadReport(false); };
$("btnRCouponXlsx").onclick = () => loadReport(true);
$("btnRFlow").onclick = () => { reportMode = "flows"; loadReport(false); };
$("btnRFlowXlsx").onclick = () => loadReport(true);
$("btnRGrp").onclick = () => { reportMode = "groups"; loadReport(false); };
$("btnRGrpXlsx").onclick = () => loadReport(true);
$("btnRIbg").onclick = () => { reportMode = "ibigou"; loadReport(false); };
$("btnRIbgXlsx").onclick = () => loadReport(true);
async function loadReport(xlsx) {
  const urlMap = { coupons: ["/report/coupons", "/report/coupons/export"],
    flows: ["/report/balance-flows", "/report/balance-flows/export"],
    groups: ["/report/group-records", "/report/group-records/export"],
    ibigou: ["/report/ibigou-orders", "/report/ibigou-orders/export"] };
  const [listUrl, xlsxUrl] = urlMap[reportMode];
  if (xlsx) return downloadXlsx(A + xlsxUrl);
  const r = await get(A + listUrl);
  const list = r.data || [];
  if (!list.length) { $("reportTable").innerHTML = "<tr><td>暂无数据</td></tr>"; return; }
  const keys = Object.keys(list[0]).filter(k => !k.includes("prizeInfo") || reportMode === "groups");
  $("reportTable").innerHTML = "<tr>" + keys.map(k => "<th>" + k + "</th>").join("") + "</tr>" +
    list.map(x => "<tr>" + keys.map(k => "<td>" + (x[k] === null || x[k] === undefined ? "" : x[k]) + "</td>").join("") + "</tr>").join("");
}

// ---------- 商家审计 ----------
$("btnAudit").onclick = async () => {
  const no = $("auditNo").value.trim();
  if (!no) { alert("请输入商家编号"); return; }
  const r = await get(A + "/merchant/" + no + "/config-audit");
  if (r.code !== 0) { alert(r.msg); return; }
  const d = r.data;
  const m = d.merchant;
  const ps = d.privatePools || [], pubs = d.publicPools || [], gps = d.groupPools || [];
  $("auditOut").innerHTML = `
    <div class="card"><b>商家基础</b>：${m ? m.merchantName + "（" + m.merchantNo + "）状态:" + (m.status === 1 ? "启用" : "禁用") : "不存在"}，权重 私有${m ? m.privatePoolWeight : "-"}/公共${m ? m.publicPoolWeight : "-"}，大类 折扣${m ? m.boxDiscountTotalWeight : "-"}/立减余额${m ? m.boxCouponTotalWeight : "-"}</div>
    <div class="card"><b>私有档位 ${ps.length}</b>：${ps.map(p => `#${p.prizeId} 类型${p.prizeType} 值${p.prizeValue} 权重${p.weight} 宜必购${p.isSupportIbigou === 1 ? "支持" : "否"}`).join("；") || "无"}</div>
    <div class="card"><b>公共投放 ${pubs.length}</b>：${pubs.map(p => `#${p.publicId} 值${p.prizeValue} ${p.enabled === 1 ? "上架" : "下架"}`).join("；") || "无"}</div>
    <div class="card"><b>专属池档位 ${gps.length}</b>：${gps.map(p => `#${p.groupPoolId} 渠道${p.channel} 值${p.prizeValue}`).join("；") || "无"}</div>`;
};

loadMerchants(); loadConfig();
</script>
</body>
</html>
"""


def main():
    files = {
        os.path.join(BASE, "merchant", "login.html"): MERCHANT_LOGIN,
        os.path.join(BASE, "merchant", "index.html"): MERCHANT_INDEX,
        os.path.join(BASE, "admin", "login.html"): ADMIN_LOGIN,
        os.path.join(BASE, "admin", "index.html"): ADMIN_INDEX,
    }
    for path, content in files.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("generated:", os.path.relpath(path, BASE), len(content), "bytes")


if __name__ == "__main__":
    main()
