# -*- coding: utf-8 -*-
"""生成顾客端 H5 三页（盲盒抽奖/钱包/宜必购）到 backend 静态资源。

import sys as _sys

API 同源相对路径 /api；生产域名 ybgtc.com 反向代理后不变。"""
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


BASE = r"D:\reasonix\ibigou-blindbox\backend\src\main\resources\static\h5\customer"

PAGE_INDEX = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>宜必购盲盒抽奖</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; color:#222; }
  .wrap { max-width: 640px; margin: 0 auto; padding: 16px 16px 40px; }
  .card { background:#fff; border-radius:12px; padding:16px; margin-bottom:14px; box-shadow:0 1px 4px rgba(0,0,0,.06); }
  h1 { font-size:20px; text-align:center; margin:12px 0 4px; }
  .sub { text-align:center; color:#888; font-size:12px; margin-bottom:16px; }
  label { display:block; font-size:13px; color:#666; margin:10px 0 4px; }
  input, select { width:100%; padding:10px; border:1px solid #ddd; border-radius:8px; font-size:15px; }
  .btn { display:block; width:100%; padding:12px; border:none; border-radius:8px; background:#ff6b35;
         color:#fff; font-size:16px; font-weight:600; cursor:pointer; margin-top:12px; }
  .btn.gray { background:#9aa0a6; }
  .btn.blue { background:#1a73e8; }
  .btn:disabled { background:#ccc; }
  .tag { display:inline-block; padding:2px 8px; border-radius:10px; font-size:12px; margin-right:6px; }
  .tag.orange { background:#fff3e0; color:#e65100; }
  .tag.blue { background:#e3f2fd; color:#1565c0; }
  .tag.green { background:#e8f5e9; color:#2e7d32; }
  .tag.gray { background:#f1f3f4; color:#666; }
  .prize { text-align:center; padding:18px; }
  .prize .value { font-size:34px; font-weight:700; color:#ff6b35; }
  .prize .name { margin-top:6px; color:#444; }
  .tip { font-size:12px; color:#888; margin-top:8px; line-height:1.6; }
  .warn { background:#fff3cd; border:1px solid #ffe58f; color:#8a5a00; padding:10px; border-radius:8px;
          font-size:12px; margin-top:10px; line-height:1.6; }
  .row { display:flex; gap:8px; }
  .row .btn { flex:1; }
  .hide { display:none; }
  .modal-mask { position:fixed; inset:0; background:rgba(0,0,0,.45); display:none; align-items:center; justify-content:center; z-index:99; }
  .modal { background:#fff; border-radius:14px; width:88%; max-width:420px; padding:20px; }
  .link-bar { display:flex; justify-content:center; gap:16px; font-size:13px; color:#1a73e8; margin-top:8px; }
  .link-bar a { text-decoration:none; }
</style>
</head>
<body>
<div class="wrap">
  <h1>🎁 宜必购盲盒</h1>
  <div class="sub" id="shopName">线下门店盲盒抽奖 · 奖励下次消费可用</div>

  <div class="card">
    <label>我的手机号（唯一身份）</label>
    <input id="phone" type="tel" maxlength="11" placeholder="请输入手机号">
    <label>抽奖门店</label>
    <input id="merchantNo" placeholder="二维码进入自动带入，也可手输商家编号">
  </div>

  <div class="card">
    <button class="btn" id="btnDraw">✨ 参与盲盒抽奖</button>
    <div class="tip">抽到奖励后请完成下方任一操作，奖励立即变为可用。</div>
  </div>

  <!-- 团购专属盲盒 -->
  <div class="card">
    <div style="font-weight:600;margin-bottom:4px;">🥡 团购到店专享盲盒</div>
    <div class="tip">美团团购 / 饿了么团购到店客户专享；奖励本次团购不可用。</div>
    <label>渠道</label>
    <select id="groupChannel">
      <option value="1">美团</option>
      <option value="2">饿了么</option>
    </select>
    <label>本次团购消费金额</label>
    <input id="groupAmount" type="number" min="0.01" step="0.01" placeholder="请输入团购消费金额">
    <button class="btn blue" id="btnGroupDraw">参与团购专属盲盒抽奖</button>
    <div class="warn">团购券真实核销请在美团 / 饿了么 App 完成；本系统仅做登记与奖励发放。</div>
  </div>

  <div class="link-bar">
    <a href="wallet.html">👛 我的钱包</a>
    <a href="ibigou.html">🛒 宜必购商城</a>
  </div>
</div>

<!-- 中奖结果弹窗 -->
<div class="modal-mask" id="modalMask">
  <div class="modal">
    <div class="prize">
      <div class="value" id="prizeValue">--</div>
      <div class="name" id="prizeName">--</div>
    </div>
    <div class="warn" id="prizeWarn"></div>
    <div class="row" style="margin-top:14px;">
      <button class="btn blue" id="optOffline">① 本店自营下单</button>
      <button class="btn gray" id="optGroup">②③ 团购登记</button>
    </div>
    <div class="tip" id="afterDrawTip"></div>
    <button class="btn gray" id="optClose" style="margin-top:10px;">先不操作，稍后可用</button>
  </div>
</div>

<script>
const API = "/api/customer";
const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.search);
if (params.get("merchantNo")) $("merchantNo").value = params.get("merchantNo");
const saved = localStorage.getItem("ibigou_phone");
if (saved) $("phone").value = saved;

let lastDraw = null;   // 最近一次开奖结果
let lastCoupons = [];  // 下单用可用券

async function api(path, body) {
  const opt = { method: "POST", headers: {} };
  if (body) {
    opt.headers["Content-Type"] = "application/x-www-form-urlencoded";
    opt.body = new URLSearchParams(body).toString();
  }
  const r = await fetch(API + path, opt);
  return r.json();
}
function phone() {
  const p = $("phone").value.trim();
  if (!/^1\d{10}$/.test(p)) { alert("请输入正确的手机号"); return null; }
  localStorage.setItem("ibigou_phone", p);
  return p;
}
function merchant() {
  const m = $("merchantNo").value.trim();
  if (!m) { alert("请输入商家编号"); return null; }
  return m;
}
function prizeName(t, v) {
  if (t === 1) return "折扣券 " + v + " 折";
  return {1:"折扣券",2:"立减券",3:"普通余额",4:"团购免单余额"}[t] + " " + v + "元";
}

// ---------- 普通盲盒 ----------
$("btnDraw").onclick = async () => {
  const p = phone(), m = merchant();
  if (!p || !m) return;
  $("btnDraw").disabled = true;
  const r = await api("/draw/normal", { merchantNo: m, userPhone: p });
  $("btnDraw").disabled = false;
  if (r.code !== 0) { alert(r.msg || "抽奖失败"); return; }
  showDrawResult(r.data, m, p);
};

// ---------- 团购专属盲盒 ----------
$("btnGroupDraw").onclick = async () => {
  const p = phone(), m = merchant();
  if (!p || !m) return;
  const amt = parseFloat($("groupAmount").value);
  if (!(amt > 0)) { alert("请输入大于 0 的团购消费金额"); return; }
  const ch = +$("groupChannel").value;
  $("btnGroupDraw").disabled = true;
  const r = await api("/draw/group", { merchantNo: m, userPhone: p, channel: ch, qrCodeUniqueKey: "QR-" + m + "-" + ch });
  $("btnGroupDraw").disabled = false;
  if (r.code !== 0) { alert(r.msg || "抽奖失败"); return; }
  // 团购场景：展示中奖 + 自动登记闭环（本次团购不可抵扣）
  showDrawResult(r.data, m, p, true);
  await api("/group/register", {
    merchantNo: m, userPhone: p, channel: ch,
    groupAmount: String(amt), drawBatchNo: r.data.drawBatchNo,
    prizeInfo: JSON.stringify(r.data)
  });
  $("afterDrawTip").textContent = "✅ 登记成功，奖励已存入账户，下次消费可用（本次团购不可抵扣）";
};

// ---------- 结果展示 ----------
function showDrawResult(draw, m, p, groupMode) {
  lastDraw = { ...draw, merchantNo: m, userPhone: p };
  $("prizeValue").textContent = draw.prizeValue + " 元";
  $("prizeName").textContent = prizeName(draw.prizeType, draw.prizeValue);
  if (groupMode) {
    $("prizeWarn").textContent = "本次团购不可抵扣，奖励已存入账户，下次到店本店自营消费或宜必购渠道可以使用";
  } else {
    $("prizeWarn").textContent = "奖励暂不可用（can_use_after_draw=0），完成下方任一操作后立即可用";
  }
  $("modalMask").style.display = "flex";
  loadUsableCoupons(p, m);
}

$("optClose").onclick = () => { $("modalMask").style.display = "none"; };

// ③ 团购登记闭环
$("optGroup").onclick = async () => {
  if (!lastDraw) return;
  const ch = confirm("选择登记渠道：确定=美团(1)，取消=饿了么(2)") ? 1 : 2;
  const amt = prompt("请输入本次团购消费金额", "100");
  if (!(parseFloat(amt) > 0)) return;
  const r = await api("/group/register", {
    merchantNo: lastDraw.merchantNo, userPhone: lastDraw.userPhone, channel: ch,
    groupAmount: String(parseFloat(amt)), drawBatchNo: lastDraw.drawBatchNo,
    prizeInfo: JSON.stringify(lastDraw)
  });
  alert(r.code === 0 ? "✅ 登记成功，奖励已变为可用" : (r.msg || "登记失败"));
  $("modalMask").style.display = "none";
};

// ① 本店自营下单（券 + 余额抵扣）
$("optOffline").onclick = async () => {
  if (!lastDraw) return;
  $("modalMask").style.display = "none";
  const w = await apiGet("/wallet/" + lastDraw.userPhone);
  if (!w.data) { alert("钱包查询失败"); return; }
  const avail = (w.data.available || []).filter(c => c.sourceMerchantNo === lastDraw.merchantNo);
  if (avail.length === 0 && !(parseFloat(w.data.balance) > 0)) {
    alert("暂无可用的券或余额，可直接现金支付或稍后再试");
  }
  lastCoupons = avail;
  const msg = ["【本店自营下单】",
    avail.length ? "可选券:\n" + avail.map((c,i) => `${i+1}. 立减${c.prizeValue}元(编号${c.couponId})`).join("\n") : "无可用券",
    "余额可用: " + w.data.balance + " 元",
    "请输入订单金额:"].join("\n");
  const amount = prompt(msg, "100");
  if (!(parseFloat(amount) > 0)) return;
  const couponIdx = avail.length ? prompt("选择券序号(不选输入0)", "1") : "0";
  const couponId = avail.length && +couponIdx >= 1 && +couponIdx <= avail.length
      ? avail[+couponIdx - 1].couponId : null;
  const deduct = prompt("余额抵扣金额(0=不使用)", "0");
  const r = await api("/offline/order", {
    userPhone: lastDraw.userPhone, merchantNo: lastDraw.merchantNo,
    couponId: couponId || "", deductBalance: deduct || "0",
    orderAmount: String(parseFloat(amount)), drawBatchNo: lastDraw.drawBatchNo
  });
  alert(r.code === 0
      ? "✅ 下单成功，订单号: " + r.data.offlineOrderNo + "，实付: " + r.data.payAmount + " 元"
      : (r.msg || "下单失败"));
};

function apiGet(path) {
  return fetch(API + path).then(r => r.json());
}
async function loadUsableCoupons(p, m) {
  try {
    const w = await apiGet("/wallet/" + p);
    const avail = (w.data.available || []).filter(c => c.sourceMerchantNo === m);
    $("afterDrawTip").textContent = avail.length
        ? "当前本店可用券 " + avail.length + " 张；完成下方操作后本次奖品也可用"
        : "完成下方任一操作，本次奖品立即可用";
  } catch (e) { /* ignore */ }
}
</script>
</body>
</html>
"""

PAGE_WALLET = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>我的钱包</title>
<style>
  * { box-sizing: border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; color:#222; }
  .wrap { max-width:640px; margin:0 auto; padding:16px 16px 40px; }
  .card { background:#fff; border-radius:12px; padding:16px; margin-bottom:14px; box-shadow:0 1px 4px rgba(0,0,0,.06); }
  h1 { font-size:20px; text-align:center; margin:12px 0 4px; }
  .sub { text-align:center; color:#888; font-size:12px; margin-bottom:16px; }
  input { width:100%; padding:10px; border:1px solid #ddd; border-radius:8px; font-size:15px; }
  .btn { width:100%; padding:12px; border:none; border-radius:8px; background:#1a73e8; color:#fff; font-size:15px; margin-top:10px; cursor:pointer; }
  .balance { text-align:center; padding:10px 0; }
  .balance .num { font-size:32px; font-weight:700; color:#ff6b35; }
  .balance .lab { color:#888; font-size:12px; margin-top:4px; }
  .sec-title { font-weight:600; margin:14px 0 8px; font-size:15px; }
  .coupon { border:1px solid #eee; border-radius:10px; padding:10px 12px; margin-bottom:8px; font-size:13px; line-height:1.7; }
  .tag { display:inline-block; padding:2px 8px; border-radius:10px; font-size:11px; margin-left:4px; }
  .tag.green { background:#e8f5e9; color:#2e7d32; }
  .tag.orange { background:#fff3e0; color:#e65100; }
  .tag.gray { background:#f1f3f4; color:#666; }
  .flow { font-size:12px; border-bottom:1px dashed #eee; padding:8px 0; display:flex; justify-content:space-between; }
  .flow .amt { font-weight:600; }
  .flow .plus { color:#2e7d32; } .flow .minus { color:#c62828; }
  .link-bar { display:flex; justify-content:center; gap:16px; font-size:13px; color:#1a73e8; margin-top:8px; }
  .link-bar a { text-decoration:none; }
</style>
</head>
<body>
<div class="wrap">
  <h1>👛 我的钱包</h1>
  <div class="sub">优惠券与余额资产 · 可用性以系统校验为准</div>
  <div class="card">
    <input id="phone" type="tel" maxlength="11" placeholder="请输入手机号">
    <button class="btn" id="btnLoad">查询</button>
  </div>
  <div class="card hide" id="balanceCard">
    <div class="balance">
      <div class="num" id="balanceNum">0.00 元</div>
      <div class="lab">账户总可用余额（余额可跨门店、宜必购渠道使用）</div>
    </div>
  </div>
  <div id="sections"></div>
  <div class="link-bar">
    <a href="index.html">🎁 去抽奖</a>
    <a href="ibigou.html">🛒 宜必购商城</a>
  </div>
</div>
<script>
const API = "/api/customer";
const $ = id => document.getElementById(id);
const saved = localStorage.getItem("ibigou_phone");
if (saved) $("phone").value = saved;

const typeName = {1:"折扣券",2:"立减券",3:"普通余额",4:"团购免单余额"};
const statusName = {0:"未使用",1:"已核销",2:"已过期",3:"已作废"};

$("btnLoad").onclick = async () => {
  const p = $("phone").value.trim();
  if (!/^1\d{10}$/.test(p)) { alert("请输入正确的手机号"); return; }
  localStorage.setItem("ibigou_phone", p);
  const r = await fetch(API + "/wallet/" + p).then(r => r.json());
  if (r.code !== 0) { alert(r.msg || "查询失败"); return; }
  const w = r.data;
  $("balanceCard").classList.remove("hide");
  $("balanceNum").textContent = w.balance + " 元";
  render(w);
};

function couponHtml(c) {
  const ext = c.sourceMerchantNo ? "（发行商家 " + c.sourceMerchantNo + "）" : "";
  const ibg = c.isSupportIbigou === 1 ? '<span class="tag green">支持宜必购渠道</span>' : '<span class="tag gray">仅线下门店可用</span>';
  const valueTxt = c.prizeType === 1 ? c.prizeValue + " 折" : c.prizeValue + " 元";
  return `<div class="coupon">${typeName[c.prizeType]||"券"} ${valueTxt} ${ext}<br>
    编号 #${c.couponId} · ${statusName[c.status]||c.status} ${ibg}</div>`;
}

function render(w) {
  const sections = $("sections");
  let html = "";
  if ((w.available||[]).length) {
    html += `<div class="sec-title">✅ 可用券</div>` + w.available.map(couponHtml).join("");
  }
  if ((w.pending||[]).length) {
    html += `<div class="sec-title">⏳ 暂不可用券（完成流程后可消费）</div>` + w.pending.map(couponHtml).join("");
  }
  if ((w.used||[]).length) {
    html += `<div class="sec-title">📌 已核销</div>` + w.used.map(couponHtml).join("");
  }
  if ((w.expiredOrVoid||[]).length) {
    html += `<div class="sec-title">🗂 已过期 / 已作废</div>` + w.expiredOrVoid.map(couponHtml).join("");
  }
  if ((w.flows||[]).length) {
    html += `<div class="sec-title">💧 余额流水</div>`;
    const ft = {1:"发放",2:"扣减",3:"退款退回"};
    const vt = {1:"线下自动",2:"手工",3:"宜必购渠道"};
    html += w.flows.map(f => {
      const plus = f.amount >= 0;
      return `<div class="flow"><span>${ft[f.flowType]||""} ${vt[f.verifyType]||""} · ${(f.createTime||"").replace("T"," ")}</span>
              <span class="amt ${plus?"plus":"minus"}">${f.amount} 元</span></div>`;
    }).join("");
  }
  if (!html) html = '<div class="card" style="color:#888;font-size:13px;">暂无资产，去抽奖试试吧</div>';
  sections.innerHTML = html;
}
</script>
</body>
</html>
"""

PAGE_IBIGOU = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>宜必购商城</title>
<style>
  * { box-sizing:border-box; margin:0; padding:0; }
  body { font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif; background:#f5f6f8; color:#222; }
  .wrap { max-width:640px; margin:0 auto; padding:16px 16px 40px; }
  .card { background:#fff; border-radius:12px; padding:16px; margin-bottom:14px; box-shadow:0 1px 4px rgba(0,0,0,.06); }
  h1 { font-size:20px; text-align:center; margin:12px 0 4px; }
  .sub { text-align:center; color:#888; font-size:12px; margin-bottom:16px; }
  input, select { width:100%; padding:10px; border:1px solid #ddd; border-radius:8px; font-size:15px; margin-top:6px; }
  .btn { width:100%; padding:12px; border:none; border-radius:8px; background:#ff6b35; color:#fff; font-size:15px; margin-top:10px; cursor:pointer; }
  .btn.blue { background:#1a73e8; }
  .btn.gray { background:#9aa0a6; }
  .goods { border:1px solid #eee; border-radius:10px; padding:12px; margin-bottom:8px; display:flex; justify-content:space-between; align-items:center; }
  .goods .name { font-weight:600; }
  .goods .price { color:#ff6b35; font-weight:700; }
  .tag { display:inline-block; padding:2px 8px; border-radius:10px; font-size:11px; }
  .tag.green { background:#e8f5e9; color:#2e7d32; }
  .tag.gray { background:#f1f3f4; color:#666; }
  .order { border:1px solid #eee; border-radius:10px; padding:12px; margin-bottom:8px; font-size:13px; line-height:1.8; }
  .refund-btn { background:#c62828; color:#fff; border:none; border-radius:6px; padding:4px 10px; font-size:12px; cursor:pointer; margin-right:6px; }
  .link-bar { display:flex; justify-content:center; gap:16px; font-size:13px; color:#1a73e8; margin-top:8px; }
  .link-bar a { text-decoration:none; }
  .warn { background:#fff3cd; border:1px solid #ffe58f; color:#8a5a00; padding:10px; border-radius:8px; font-size:12px; margin-top:10px; line-height:1.6; }
</style>
</head>
<body>
<div class="wrap">
  <h1>🛒 宜必购商城</h1>
  <div class="sub">内部渠道 · 优惠券与余额抵扣</div>
  <div class="card">
    <input id="phone" type="tel" maxlength="11" placeholder="请输入手机号">
    <button class="btn blue" id="btnLoad">加载商品与可用资产</button>
    <div class="warn hide" id="channelWarn">渠道暂未开放</div>
  </div>
  <div class="card" id="goodsCard"></div>
  <div class="card" id="assetsCard"></div>
  <div class="card" id="ordersCard"></div>
  <div class="link-bar">
    <a href="index.html">🎁 去抽奖</a>
    <a href="wallet.html">👛 我的钱包</a>
  </div>
</div>
<script>
const API = "/api/customer";
const $ = id => document.getElementById(id);
const saved = localStorage.getItem("ibigou_phone");
if (saved) $("phone").value = saved;
const typeName = {1:"折扣券",2:"立减券",3:"普通余额",4:"团购免单余额"};

async function get(path) { return fetch(API + path).then(r => r.json()); }
async function post(path, body) {
  return fetch(API + path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(body).toString()
  }).then(r => r.json());
}
function phone() {
  const p = $("phone").value.trim();
  if (!/^1\d{10}$/.test(p)) { alert("请输入正确的手机号"); return null; }
  localStorage.setItem("ibigou_phone", p);
  return p;
}

let assets = null;
let orders = [];

$("btnLoad").onclick = async () => {
  const p = phone();
  if (!p) return;
  const g = await get("/ibigou/goods");
  const a = await get("/ibigou/assets/" + p);
  if (g.code !== 0) {
    $("channelWarn").classList.remove("hide");
    return;
  }
  $("channelWarn").classList.add("hide");
  // 商品
  $("goodsCard").innerHTML = '<div class="sec" style="font-weight:600;margin-bottom:8px;">商品列表</div>' +
    (g.data||[]).map(x => `<div class="goods"><div class="name">${x.goodsName}</div><div class="price">${x.price} 元</div></div>`).join("") ||
    '<div style="color:#888;font-size:13px;">暂无商品</div>';
  // 资产
  assets = a.data || {};
  const rate = assets.balanceDeductRate;
  $("assetsCard").innerHTML = `
    <div style="font-weight:600;margin-bottom:8px;">可用资产（抵扣上限 = 实付 × ${rate}%）</div>
    <div style="font-size:13px;margin-bottom:6px;">余额：<b style="color:#ff6b35;">${assets.balance} 元</b></div>
    <div style="font-size:13px;margin-bottom:6px;">可用券：
      ${(assets.coupons||[]).map(c => `<span class="tag green">${typeName[c.prizeType]||""} ${c.prizeValue}元#${c.couponId}</span>`).join(" ") || "（无）"}
    </div>
    <div style="font-size:12px;color:#888;">下单规则：券(支持宜必购) + 余额(≤实付×${rate}%) 抵扣，剩余金额支付</div>
    <button class="btn" id="btnOrder">下单</button>
    <input id="orderAmount" type="number" min="0.01" step="0.01" placeholder="订单金额" style="margin-top:8px;">
    <select id="orderCoupon" style="margin-top:6px;">
      <option value="">不使用优惠券</option>
      ${(assets.coupons||[]).map(c => `<option value="${c.couponId}">${typeName[c.prizeType]||""} ${c.prizeType === 1 ? c.prizeValue + "折" : c.prizeValue + "元"}</option>`).join("")}
    </select>
    <input id="orderDeduct" type="number" min="0" step="0.01" placeholder="余额抵扣金额(0=不使用)" style="margin-top:6px;">`;
  $("btnOrder").onclick = doOrder;
  await loadOrders(p);
};

async function doOrder() {
  const p = phone();
  const amount = $("orderAmount").value;
  const couponId = $("orderCoupon").value;
  const deduct = $("orderDeduct").value || "0";
  if (!(parseFloat(amount) > 0)) { alert("请输入订单金额"); return; }
  const body = { userPhone: p, orderAmount: String(parseFloat(amount)), deductBalance: String(parseFloat(deduct)) };
  if (couponId) body.couponId = couponId;
  const r = await post("/ibigou/order", body);
  alert(r.code === 0
      ? "✅ 下单成功 订单号: " + r.data.ibigouOrderNo + " 实付: " + r.data.payAmount + " 元"
      : (r.msg || "下单失败"));
  if (r.code === 0) { $("btnLoad").onclick(); }
}

async function loadOrders(p) {
  const r = await get("/ibigou/orders/" + p);
  orders = r.data || [];
  const ft = {0:"未退款",1:"全额退款",2:"部分退款"};
  $("ordersCard").innerHTML = '<div style="font-weight:600;margin-bottom:8px;">我的订单</div>' +
    (orders.map(o => `
      <div class="order">
        <div>订单号: ${o.ibigouOrderNo} · ${ft[o.refundStatus]||""}</div>
        <div>金额: ${o.orderAmount} 元，券抵扣: ${o.couponId?"有":""}，余额抵扣: ${o.deductBalance} 元，实付: ${o.payAmount} 元</div>
        <div style="color:#888;font-size:12px;">下单: ${(o.createTime||"").replace("T"," ")}</div>
        ${o.refundStatus === 0 ? `<button class="refund-btn" onclick="refund('${o.ibigouOrderNo}', true)">全额退款</button>
          <button class="refund-btn" onclick="refund('${o.ibigouOrderNo}', false)">部分退款</button>` : ""}
      </div>`).join("")) || '<div style="color:#888;font-size:13px;">暂无订单</div>';
}

async function refund(orderNo, full) {
  const p = phone();
  let body = { userPhone: p };
  if (!full) {
    const amt = prompt("请输入部分退款金额", "10");
    if (!(parseFloat(amt) > 0)) return;
    body.refundAmount = String(parseFloat(amt));
  }
  const r = await post(`/ibigou/order/${orderNo}/refund`, body);
  alert(r.code === 0 ? "✅ 退款成功" : (r.msg || "退款失败"));
  if (r.code === 0) { $("btnLoad").onclick(); }
}
</script>
</body>
</html>
"""


def main():
    os.makedirs(BASE, exist_ok=True)
    files = {
        "index.html": PAGE_INDEX,
        "wallet.html": PAGE_WALLET,
        "ibigou.html": PAGE_IBIGOU,
    }
    for name, content in files.items():
        path = os.path.join(BASE, name)
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("generated:", path, len(content), "bytes")


if __name__ == "__main__":
    main()
