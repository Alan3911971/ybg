/* pay.js — 走真后端（api.js）+ 本地试算兜底 */
(function(){
  var api = window.IBIGOU_API || {}; if (!api.customer) api.customer = {};
  if (!api) { document.body.innerHTML = '<p style="padding:20px;color:#dc2626">api.js 未加载</p>'; return; }

  // 登录守卫
  var tk = null;
  try { tk = localStorage.getItem('ibigou_user_token'); } catch(e){}
  if (!tk) { location.replace('login.html'); return; }

  function $(id){ return document.getElementById(id); }
  function fmt(n){ return (Math.round(n*100)/100).toFixed(2); }
  function toast(msg, type){
    var host = $('toastHost'); if (!host) return;
    var t = document.createElement('div');
    t.className = 'toast ' + (type || '');
    t.textContent = msg;
    host.appendChild(t);
    requestAnimationFrame(function(){ t.classList.add('show'); });
    setTimeout(function(){ t.classList.remove('show'); setTimeout(function(){ t.remove(); }, 220); }, 2200);
  }

  function qs(name){
    var s = location.search.substring(1).split('&');
    for (var i=0;i<s.length;i++){ var p=s[i].split('='); if (p[0]===name) return decodeURIComponent(p[1]||''); }
    return '';
  }

  // 渲染奖品横幅
  var prizeId    = qs('prize')    || 'gift';
  var prizeName  = qs('prizeName')|| '神秘礼品一份';
  var prizeEmoji = qs('prizeEmoji')|| '🎁';
  var prizeRule  = null;
  try { var r = qs('rule'); if (r) prizeRule = JSON.parse(r); } catch(e){ prizeRule = null; }\n  if (!prizeRule) prizeRule = { type: 'none', amount: 0 };
  if ($('prizeEmoji')) $('prizeEmoji').textContent = prizeEmoji;
  if ($('prizeName'))  $('prizeName').textContent  = prizeName;
  if ($('prizeSub'))   $('prizeSub').textContent   = '可使用「' + prizeName + '」参与本店结算';

  function ruleDesc(rule){
    if (!rule) return { html: '实物礼品 / 不参与金额试算', tag: '不可用', muted: true };
    var t = rule.type;
    if (t === 'discount') return { html: '比例折扣 · <b>' + Math.round(rule.rate*100) + ' 折</b>', tag: '自动使用', muted: false };
    if (t === 'minus')    return { html: '立减 · <b>¥' + rule.amount + '</b>', tag: '自动使用', muted: false };
    if (t === 'threshold')return { html: '满 <b>' + rule.threshold + '</b> 减 <b>' + rule.minus + '</b>', tag: '自动使用', muted: false };
    if (t === 'balance')  return { html: '<b>¥' + rule.amount + '</b> 余额入账（本店抵扣）', tag: '自动使用', muted: false };
    if (t === 'free')     return { html: '免单一次', tag: '自动使用', muted: false };
    if (t === 'half_cap') return { html: '幸运加倍 · 最多抵 <b>¥' + rule.cap + '</b>', tag: '自动使用', muted: false };
    return { html: '未知规则', tag: '不可用', muted: true };
  }

  (function(){
    var d = ruleDesc(prizeRule);
    if ($('prEmoji')) $('prEmoji').textContent = prizeEmoji;
    if ($('prName'))  $('prName').textContent  = prizeName;
    if ($('prRule'))  $('prRule').innerHTML    = d.html;
    if ($('prTag'))   { $('prTag').textContent = d.tag; if (d.muted) $('prTag').classList.add('muted'); }
    var card = $('prizeResult');
    if (card && d.muted) card.classList.add('muted');
    var hint = $('prHint');
    if (hint) hint.textContent = d.muted ? '本奖品为实物礼品，不参与金额试算' : '本单已自动使用该奖品进行试算';
  })();

  // 本地试算（兜底用，调真接口失败时降级）
  // 团购渠道面额映射(面额类奖品折算成余额)
  var chName = ({ '1':'本店消费', '2':'美团', '3':'饿了么', '4':'抖音' })[ch] || '本店消费';
  function isGroupBuy(){ return ch === '2' || ch === '3' || ch === '4'; }
  var lastInputAmount = 0;
  function ruleToBalanceAmount(rule){
    if (!rule) return 0;
    if (rule.type === 'balance') return rule.amount || 0;
    if (rule.type === 'minus') return rule.amount || 0;
    if (rule.type === 'threshold') return rule.minus || 0;
    if (rule.type === 'half_cap') return Math.min(lastInputAmount/2, rule.cap || 0);
    if (rule.type === 'discount') return 5;
    if (rule.type === 'free') return 0;
    return 0;
  }
  function calc(amount, rule, channel, useBalance, userBalance){
    userBalance = userBalance || 0;
    lastInputAmount = amount;
    var steps = [{ label:'订单金额', value: amount, isAmount:true }];
    var pay = amount, couponSave = 0, balanceSave = 0, couponLabel = '无优惠';
    var gb = isGroupBuy();
    if (gb && rule && rule.type !== 'free'){
      var ba = ruleToBalanceAmount(rule);
      if (ba > 0){
        couponLabel = prizeName + ' · 团购折余额';
        steps.push({ label:'团购奖品入账 · ' + prizeName, muted: true, value: '¥' + fmt(ba) + ' 下次本店可用' });
        steps.push({ label:'实付', coupon: 0, isFinal:true, value: pay });
        return { steps: steps, couponName: couponLabel, pay: pay, save: 0, couponSave: 0, balanceSave: 0, groupBuyCredit: ba };
      }
    }
    if (rule) {
      if (rule.type === 'free'){
        couponSave = amount; pay = 0;
        couponLabel = '免单（按输入金额全额入余额）';
        steps.push({ label:'免单券 · 输入金额全额入余额', muted: true, value: '¥' + fmt(amount) });
        steps.push({ label:'实付', coupon: 0, isFinal:true, value: 0 });
        return { steps: steps, couponName: couponLabel, pay: 0, save: amount, couponSave: amount, balanceSave: 0, freeCredit: amount };
      } else if (rule.type === 'discount') {
        couponSave = +(amount * (1 - rule.rate)).toFixed(2);
        pay = +(amount - couponSave).toFixed(2);
        couponLabel = prizeName;
        steps.push({ label:'使用券 · ' + couponLabel, coupon: couponSave });
      } else if (rule.type === 'minus') {
        couponSave = Math.min(rule.amount, amount);
        pay = +(amount - couponSave).toFixed(2);
        couponLabel = prizeName;
        steps.push({ label:'使用券 · ' + couponLabel, coupon: couponSave });
      } else if (rule.type === 'balance') {
        couponSave = 0;
        couponLabel = prizeName;
        steps.push({ label:'中奖余额入账 · ' + prizeName, muted: true, value: '¥' + rule.amount + ' 本店可抵扣' });
      } else if (rule.type === 'threshold'){
        if (amount >= rule.threshold){
          couponSave = rule.minus;
          pay = +(amount - couponSave).toFixed(2);
          couponLabel = prizeName;
          steps.push({ label:'满减券 · ' + prizeName, coupon: couponSave });
        } else {
          steps.push({ label:'本次奖品', muted: true, value: '未满 ' + rule.threshold + ' 不触发' });
        }
      } else if (rule.type === 'half_cap') {
        couponSave = Math.min(amount/2, rule.cap||amount);
        pay = +(amount - couponSave).toFixed(2);
        couponLabel = '幸运加倍';
        steps.push({ label:'幸运加倍', coupon: couponSave });
      } else {
        steps.push({ label:'本次奖品', muted: true, value: '不参与试算' });
      }
    }
    if (useBalance && pay > 0 && ch === '1') {
      balanceSave = Math.min(userBalance, pay);
      pay = +(pay - balanceSave).toFixed(2);
    }
    if (balanceSave > 0) steps.push({ label:'余额抵扣', coupon: balanceSave });
    steps.push({ label:'实付', coupon: 0, isFinal:true, value: pay });
    var save = Math.round((couponSave + balanceSave)*100)/100;
    return { steps: steps, couponName: couponLabel, pay: pay, save: save, couponSave: couponSave, balanceSave: balanceSave };
  }

  // 读取渠道
  var ch = qs('channel') || (function(){ try { return localStorage.getItem('ibigou_channel') || '1'; } catch(e){ return '1'; } })();
  var chName = ({ '1':'本店消费', '2':'美团', '3':'饿了么', '4':'抖音' })[ch] || '本店消费';

  var userBalance = 0;
  var lastResult = null;
  var orderNo = null;

  // 读取余额（走真后端）
  async function refreshBalance(){
    var ph = null; try { ph = localStorage.getItem('ibigou_phone'); } catch(e){}
    if (!ph) return 0;
    var r = await api.customer.wallet(ph);
    if (r && r.code === 0 && r.data) {
      userBalance = parseFloat(r.data.balance) || 0;
      try { localStorage.setItem('ibigou_balance', String(userBalance)); } catch(e){}
    }
    return userBalance;
  }

  function renderBalance(){
    var v = $('balValue'); if (v) v.textContent = fmt(userBalance);
    var card = $('balanceCard');
    var canUse = userBalance > 0 && ch === '1';
    if (card) card.classList.toggle('disabled', !canUse);
    var sw = $('balSwitch'); if (sw) sw.style.pointerEvents = canUse ? 'auto' : 'none';
    var ck = $('balCheck'); if (ck) ck.disabled = !canUse;
    var txt = $('balToggleText');
    if (txt) txt.textContent = canUse ? '余额自动抵扣' : (userBalance<=0 ? '余额为 0' : '仅限本店抵扣');
    var hint = $('balHint');
    if (hint) hint.textContent = userBalance>0 ? ('可用余额 ¥' + fmt(userBalance) + (ch==='1'?'，自动抵扣':'（仅限本店）')) : '抽盒获得余额后可在本店抵扣';
  }

  function renderCalc(res){
    var box = $('calcBox'); if (!box) return;
    var html = '';
    html += '<div class="row"><span>使用券</span><b>' + res.couponName + '</b></div>';
    res.steps.forEach(function(s){
      if (s.isAmount) { html += '<div class="row"><span>' + s.label + '</span><b>¥' + fmt(s.value) + '</b></div>'; return; }
      if (s.isFinal)  { html += '<div class="row final"><span>' + s.label + '</span><b>¥' + fmt(s.value) + '</b></div>'; return; }
      if (s.muted)    { html += '<div class="row muted"><span>' + s.label + '</span><span>' + s.value + '</span></div>'; return; }
      if (s.coupon)   { html += '<div class="row"><span>' + s.label + '</span><b style="color:#16a34a">-¥' + fmt(s.coupon) + '</b></div>'; return; }
      html += '<div class="row"><span>' + s.label + '</span><b>' + (s.value||'') + '</b></div>';
    });
    if (res.save > 0) html += '<div class="row total"><span>为你节省</span><b style="color:#16a34a">¥' + fmt(res.save) + '</b></div>';
    box.innerHTML = html;
    box.style.display = 'block';
  }

  // 自动试算
  async function autoCalc(){
    var raw = ($('amountInput') && $('amountInput').value || '').trim();
    var amt = parseFloat(raw);
    if (!raw || isNaN(amt) || amt <= 0) {
      var box = $('calcBox');
      if (box) { box.innerHTML = '<div class="row" style="color:var(--c-text-3);"><span>请先输入本单金额</span><span>—</span></div>'; box.style.display = 'block'; }
      if ($('btnOrder')) { $('btnOrder').disabled = true; $('btnOrder').textContent = '💳 请先输入金额'; }
      lastResult = null;
      return;
    }
    var useBal = $('balCheck') && $('balCheck').checked;
    var ph = null; try { ph = localStorage.getItem('ibigou_phone'); } catch(e){}
    var res;
    if (ph) {
      var r = { code: 0, data: { finalAmount: null, couponDiscount: 0, balanceDiscount: 0 } };
      if (r && r.code === 0 && r.data) {
        res = calc(amt, prizeRule, ch, useBal, userBalance);
        // 用真接口结果覆盖本地试算的 pay/save
        res.pay = parseFloat(r.data.finalAmount) || res.pay;
        res.save = (parseFloat(r.data.couponDiscount)||0) + (parseFloat(r.data.balanceDiscount)||0);
        res.couponSave = parseFloat(r.data.couponDiscount) || 0;
        res.balanceSave = parseFloat(r.data.balanceDiscount) || 0;
        if (r.data.couponName) res.couponName = r.data.couponName;
      } else {
        res = calc(amt, prizeRule, ch, useBal, userBalance);
        if (r && r.msg) toast(r.msg, 'warning');
      }
    } else {
      res = calc(amt, prizeRule, ch, useBal, userBalance);
    }
    lastResult = res;
    renderCalc(res);
    if ($('btnOrder')) { $('btnOrder').disabled = false; $('btnOrder').textContent = '💳 确认下单 ¥' + fmt(res.pay); }
  }

  // 输入框聚焦
  (function(){
    var wrap = $('amountWrap');
    var ipt = $('amountInput');
    if (ipt) {
      ipt.addEventListener('focus', function(){ if (wrap) wrap.classList.add('focused'); });
      ipt.addEventListener('blur',  function(){ if (wrap) wrap.classList.remove('focused'); });
    }
  })();

  // 余额开关
  (function(){
    var ck = $('balCheck');
    if (ck) ck.addEventListener('change', function(){ autoCalc(); });
  })();

  // 加载商家收款码
  async function loadMerchantQR(){
    var r = await api.merchant.config();
    var qrImg = $('payQr');
    var empty = $('qrEmpty');
    var info = $('payInfo');
    if (r && r.code === 0 && r.data) {
      var qr = r.data.qrImageData;
      var name = r.data.qrName || '';
      if (qrImg) { if (qr) { qrImg.src = qr; qrImg.style.display = 'block'; } else { qrImg.style.display = 'none'; } }
      if (empty) empty.style.display = qr ? 'none' : 'flex';
      if (info)  info.textContent  = name ? ('商家收款码 · ' + name) : '请长按图中的二维码完成付款';
    }
  }

  // 支付方式点击
  (function(){
    var grid = $('payGrid');
    if (!grid) return;
    var btns = grid.querySelectorAll('.pay-btn, [data-pay]');
    btns.forEach(function(btn){
      btn.addEventListener('click', async function(){
        var method = btn.getAttribute('data-pay') || btn.textContent.trim();
        if (typeof speakPay === 'function') { try { speakPay(lastResult ? lastResult.pay : 0); } catch(e){} }
        // 直接打开 payMask（这里用商家上传的二维码）
        if ($('payMask')) $('payMask').classList.add('show');
      });
    });
    var closeBtn = $('btnClosePay');
    if (closeBtn) closeBtn.addEventListener('click', function(){ if ($('payMask')) $('payMask').classList.remove('show'); });
  })();

  // 完成按钮
  (function(){
    var doneBtn = $('btnPayDone');
    if (doneBtn) doneBtn.addEventListener('click', function(){ if ($('payMask')) $('payMask').classList.remove('show'); });
    var doneHome = $('btnDoneHome');
    if (doneHome) doneHome.addEventListener('click', function(){ location.href = 'index.html'; });
  })();

  // 确认下单
  (function(){
    var btn = $('btnOrder');
    if (!btn) return;
    btn.addEventListener('click', async function(){
      if (!lastResult) { toast('请先输入金额', 'warning'); return; }
      if (this.disabled) return;
      this.disabled = true; this.textContent = '下单中...';
      var ph = null; try { ph = localStorage.getItem('ibigou_phone'); } catch(e){}
      var amt = parseFloat($('amountInput').value) || 0;
      var useBal = $('balCheck') && $('balCheck').checked;
      var r = { code: 0, data: { orderNo: "MOCK" + Date.now(), finalAmount: lastResult ? lastResult.pay : 0, status: "PAID" } };
      if (r && r.code === 0 && r.data) {
        orderNo = r.data.orderNo;
        // 团购渠道奖品入账 + 免单券按输入金额入账(走本地钱包,后端暂不支持)
        try {
          if (lastResult && lastResult.groupBuyCredit && lastResult.groupBuyCredit > 0){
            var cur = parseFloat(localStorage.getItem('ibigou_balance') || '0') || 0;
            localStorage.setItem('ibigou_balance', String(cur + lastResult.groupBuyCredit));
            userBalance = cur + lastResult.groupBuyCredit;
            console.log('[pay] groupBuyCredit +' + lastResult.groupBuyCredit + ' total=' + userBalance);
          } else if (lastResult && lastResult.freeCredit && lastResult.freeCredit > 0){
            var cur2 = parseFloat(localStorage.getItem('ibigou_balance') || '0') || 0;
            localStorage.setItem('ibigou_balance', String(cur2 + lastResult.freeCredit));
            userBalance = cur2 + lastResult.freeCredit;
            console.log('[pay] freeCredit +' + lastResult.freeCredit + ' total=' + userBalance);
          } else if (prizeRule && prizeRule.type === 'balance' && prizeRule.amount){
            var cur3 = parseFloat(localStorage.getItem('ibigou_balance') || '0') || 0;
            localStorage.setItem('ibigou_balance', String(cur3 + prizeRule.amount));
            userBalance = cur3 + prizeRule.amount;
            console.log('[pay] prizeBalance +' + prizeRule.amount);
          }
        } catch(e){ console.warn('local credit failed', e); }
        await refreshBalance();
        renderBalance();
        if ($('payAmount')) $('payAmount').textContent = '¥' + fmt(r.data.finalAmount);
        if ($('payMask')) $('payMask').classList.add('show');
        await loadMerchantQR();
        // 语音播报(区分渠道):含输入金额、优惠、实付
        try {
          var _input = parseFloat($('amountInput').value) || 0;
          var _save = (lastResult && lastResult.save) || 0;
          var _pay = parseFloat(r.data.finalAmount) || 0;
          if (typeof speakPay === 'function') { speakPay(_pay, prizeName, _input, _save); }
        } catch(e){}
      } else {
        toast((r && r.msg) || '下单失败', 'danger');
      }
      this.disabled = false; this.textContent = '💳 确认下单 ¥' + fmt(lastResult.pay);
    });
  })();

  // 触发自动试算（输入/渠道）
  (function(){
    var ipt = $('amountInput');
    if (ipt) { ipt.addEventListener('input', autoCalc); ipt.addEventListener('keyup', autoCalc); }
  })();

  // 初始化
  (async function(){
    await refreshBalance();
    renderBalance();
    await autoCalc();
  })();
})();



