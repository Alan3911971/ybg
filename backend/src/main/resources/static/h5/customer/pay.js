/* pay.js — 走真后端（api.js）+ 本地试算兜底 */
(function(){
  var api = window.IBIGOU_API || {}; if (!api.customer) api.customer = {};
  if (!api) { document.body.innerHTML = '<p style="padding:20px;color:#dc2626">api.js 未加载</p>'; return; }

  // 登录守卫
  var tk = null;
  try { tk = localStorage.getItem('ibigou_user_token'); } catch(e){}
  if (!tk) { location.replace('/h5/customer/login.html?redirect=' + encodeURIComponent('pay.html' + location.search)); return; }

  // 微信内提前静默授权获取openid（避免点支付时才跳转，2026-09-10）
  (function(){
    var ua = navigator.userAgent.toLowerCase();
    if (ua.indexOf('micromessenger') < 0) return;
    var hasOpenid = false;
    try { hasOpenid = !!sessionStorage.getItem('ibigou_wx_openid'); } catch(e){}
    if (hasOpenid) return;
    if (location.search.indexOf('openid=') >= 0) return;
    var cur = location.href.split('#')[0];
    fetch('/api/pay/wx/oauth-url?redirect=' + encodeURIComponent(cur))
      .then(function(r){ return r.json(); })
      .then(function(j){
        if (j.code === 0 && j.data && j.data.url) { location.href = j.data.url; }
      })
      .catch(function(){});
  })();

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
  try { var r = qs('rule'); if (r) prizeRule = JSON.parse(r); } catch(e){ prizeRule = null; }
  if (!prizeRule) prizeRule = { type: 'none', amount: 0 };
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

  // 读取渠道（提前声明，下面 calc 会用到）
  var ch = qs('channel') || (function(){ try { return localStorage.getItem('ibigou_channel') || '1'; } catch(e){ return '1'; } })();
  var chName = ({ '1':'本店消费', '2':'美团', '3':'饿了么', '4':'抖音' })[ch] || '本店消费';

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
    // 测试阶段锁定：0.01 元订单不应用任何优惠，实付保底 0.01
    if (amount === 0.01) {
      return { steps: [{ label:'订单金额', value: amount, isAmount:true }, { label:'实付', coupon: 0, isFinal:true, value: 0.01 }], couponName: '无优惠', pay: 0.01, save: 0, couponSave: 0, balanceSave: 0, testLock: true };
    }
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

  var userBalance = 0;
  var lastResult = null;
  var orderNo = null;
var _payPending = false;  // 标记用户已跳转APP支付, 返回时自动完成

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
    var mno = (function(){ try { var m = location.search.match(/merchantNo=([^&]+)/); if (m) { localStorage.setItem('ibigou_merchant_no', m[1]); return m[1]; } var _s = localStorage.getItem('ibigou_merchant_no'); if (_s === 'M001') { localStorage.removeItem('ibigou_merchant_no'); return ''; } return _s || ''; } catch(e){ return ''; } })();
    if (ph) {
      try {
        if (!mno || mno === '') { alert('请扫描商家二维码进入'); location.replace('/h5/customer/login.html'); return; }
    var r = await api.customer.offlineCalc({
          userPhone: ph, merchantNo: mno, orderAmount: amt,
          rule: (amt !== 0.01 && prizeRule && prizeRule.type && prizeRule.type !== 'none') ? JSON.stringify(prizeRule) : undefined
        });
        if (r && r.code === 0 && r.data) {
          var d = r.data;
          res = calc(amt, prizeRule, ch, useBal, userBalance);
          res.pay = parseFloat(d.payAmount) || res.pay;
          res.save = (parseFloat(d.afterCoupon) - parseFloat(d.payAmount)) || res.save;
          res.couponSave = (parseFloat(d.afterCoupon) < amt) ? (amt - parseFloat(d.afterCoupon)) : 0;
          res.balanceSave = parseFloat(d.actualDeduct) || 0;
          if (d.coupon && d.coupon.couponName) res.couponName = d.coupon.couponName;
          if (d.receiveQrImgWechat) res.qrWechat = d.receiveQrImgWechat;
          if (d.receiveQrImgAlipay) res.qrAlipay = d.receiveQrImgAlipay;
          if (d.receiveQrImgUnionpay) res.qrUnionpay = d.receiveQrImgUnionpay;
          if (d.receiveQrImgOther) res.qrOther = d.receiveQrImgOther;
        } else {
          res = calc(amt, prizeRule, ch, useBal, userBalance);
          if (r && r.msg) toast(r.msg, 'warning');
        }
      } catch(e) {
        console.warn('[pay] calc api error', e);
        res = calc(amt, prizeRule, ch, useBal, userBalance);
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

  // 显示支付二维码（支持静态码和API支付两种模式）
  var currentPayOrderNo = null;
  var payPollTimer = null;
  function showMerchantQR(method, payData){
    var qrImg = $('payQr');
    var empty = $('qrEmpty');
    var info = $('payInfo');
    var placeholder = empty ? empty.querySelector('.qrPlaceholder, .ri') : null;
    var eh = empty ? empty.querySelector('.eh') : null;
    var qrUrl = null, label = '商家收款码';
    var typeMap = {
      wechat: { key: 'qrWechat', label: '微信收款码' },
      alipay: { key: 'qrAlipay', label: '支付宝收款码' },
      unionpay: { key: 'qrUnionpay', label: '云闪付收款码' },
      other: { key: 'qrOther', label: '商家聚合收款码' }
    };
    var t = typeMap[method] || typeMap.other;
    // API支付模式：使用支付接口返回的二维码
    if (payData && payData.payMode == 1) {
      label = payData.label || t.label;
      if (payData.qrCode) {
        // 调用后端生成二维码SVG
        fetch('/api/customer/qr-svg', {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-User-Token': (function(){ try { return localStorage.getItem('ibigou_user_token') || ''; } catch(e){ return ''; } })() },
          body: 'content=' + encodeURIComponent(payData.qrCode)
        }).then(function(r){ return r.json(); }).then(function(r){
          if (r.code === 0 && r.data && r.data.svg) {
            if (empty) {
              empty.innerHTML = '<div style="width:200px;height:200px;margin:0 auto;">' + r.data.svg + '</div>';
              empty.style.display = 'flex';
              empty.style.justifyContent = 'center';
              empty.style.alignItems = 'center';
            }
            if (qrImg) qrImg.style.display = 'none';
          }
        }).catch(function(){});
        if (info) info.textContent = '请使用' + label.replace('收款码','').replace('支付','') + '扫码支付，支付成功后自动关闭';
        // 开始轮询支付状态
        startPayPolling(payData.orderNo);
        return;
      } else {
        if (info) info.textContent = '支付通道未配置，请选择其他方式';
        return;
      }
    }
    // 静态码模式：使用商家上传的收款码图片
    if (lastResult && lastResult[t.key]) {
      qrUrl = lastResult[t.key];
      label = t.label;
    }
    if (eh) eh.textContent = label;
    if (qrUrl) {
      if (qrImg) { qrImg.src = qrUrl; qrImg.style.display = 'block'; }
      if (empty) empty.style.display = 'none';
      if (info) info.textContent = '长按二维码识别完成付款';
    } else {
      if (qrImg) qrImg.style.display = 'none';
      if (empty) {
        empty.style.display = 'flex';
        if (placeholder) placeholder.textContent = '[ 商家未上传' + label + ' ]';
      }
      if (info) info.textContent = '商家未上传' + label + '，请选择其他方式';
    }
  }

  // 轮询支付状态
  function startPayPolling(orderNo){
    if (payPollTimer) clearInterval(payPollTimer);
    payPollTimer = setInterval(function(){
      fetch('/api/customer/pay/query?orderNo=' + encodeURIComponent(orderNo), { headers: { 'X-User-Token': (function(){ try { return localStorage.getItem('ibigou_user_token') || ''; } catch(e){ return ''; } })() } })
        .then(function(r){ return r.json(); })
        .then(function(r){
          if (r.code === 0 && r.data && r.data.paid) {
            clearInterval(payPollTimer);
            payPollTimer = null;
            toast('支付成功', 'success');
            setTimeout(function(){ location.reload(); }, 1500);
          }
        }).catch(function(){});
    }, 3000);
  }

  // 支付方式点击
  (function(){
    var grid = $('payGrid');
    if (!grid) return;
    var btns = grid.querySelectorAll('.pay-btn, [data-pay]');
    var payLabels = { wechat:'微信', alipay:'支付宝', unionpay:'云闪付' };
    var paySchemes = {
      wechat: 'weixin://scanqrcode',
      alipay: 'alipays://platformapi/startapp?saId=10000007',
      unionpay: 'uppay://platformapi/startapp?saId=10000007'
    };
    var scanSteps = { wechat: [], alipay: [], unionpay: [] };
    function isInWeChat(){
      var ua = navigator.userAgent.toLowerCase();
      return ua.indexOf('micromessenger') >= 0;
    }
    function getUrlParam(name){
      var m = new RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
      return m ? decodeURIComponent(m[1]) : '';
    }
    // 微信静默授权回调带回 openid → 存 sessionStorage 复用
    (function(){
      var oid = getUrlParam('openid');
      if (oid) { try { sessionStorage.setItem('ibigou_wx_openid', oid); } catch(e){} }
    })();
    // 微信内 JSAPI 拉起（wx.chooseWXPay）
    function launchJsapi(js){
      if (!window.wx) { showMerchantQR('wechat', null); return; }
      var curUrl = location.href.split('#')[0];
      fetch('/api/pay/wx/jssdk-config?url=' + encodeURIComponent(curUrl))
        .then(function(r){ return r.json(); })
        .then(function(j){
          if (j.code !== 0 || !j.data) { showMerchantQR('wechat', null); return; }
          var cfg = j.data;
          wx.config({
            debug: false,
            appId: cfg.appId,
            timestamp: cfg.timestamp,
            nonceStr: cfg.nonceStr,
            signature: cfg.signature,
            jsApiList: ['chooseWXPay']
          });
          wx.ready(function(){
            wx.chooseWXPay({
              timestamp: js.timeStamp,
              nonceStr: js.nonceStr,
              package: js.package,
              signType: js.signType,
              paySign: js.paySign,
              success: function(res){
                toast('支付成功', 'success');
                setTimeout(function(){ location.href = '/h5/customer/index.html'; }, 1500);
              },
              fail: function(err){ showMerchantQR('wechat', null); }
            });
          });
          wx.error(function(err){ showMerchantQR('wechat', null); });
        })
        .catch(function(){ showMerchantQR('wechat', null); });
    }
    btns.forEach(function(btn){
      btn.addEventListener('click', async function(){
        var method = btn.getAttribute('data-pay') || btn.textContent.trim();
        // 先调用支付接口，获取支付信息（支持静态码、API拉起、二维码）
        if (currentPayOrderNo) {
          try {
            // scene：wechat 非微信内=mweb直接拉起；wechat 微信内=jsapi（公众号内拉起）；alipay=wap；其他=二维码
            var scene = '';
            if (method === 'wechat' && !isInWeChat()) scene = 'mweb';
            else if (method === 'wechat' && isInWeChat()) scene = 'jsapi';
            else if (method === 'alipay') scene = 'wap';
            var openid = '';
            if (scene === 'jsapi') {
              openid = getUrlParam('openid') || (function(){ try { return sessionStorage.getItem('ibigou_wx_openid') || ''; } catch(e){ return ''; } })();
            }
            if (scene === 'jsapi' && !openid) {
              // 微信内静默授权：跳转公众号授权拿 openid（回调带回原页）
              var cur = location.href.split('#')[0];
              fetch('/api/pay/wx/oauth-url?redirect=' + encodeURIComponent(cur))
                .then(function(r){ return r.json(); })
                .then(function(j){
                  if (j.code === 0 && j.data && j.data.url) {
                    location.href = j.data.url;
                  } else {
                    showMerchantQR(method, null);
                  }
                })
                .catch(function(){ showMerchantQR(method, null); });
              return;
            }
            var resp = await fetch('/api/customer/pay/create', {
              method: 'POST',
              headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-User-Token': (function(){ try { return localStorage.getItem('ibigou_user_token') || ''; } catch(e){ return ''; } })() },
              body: 'orderNo=' + encodeURIComponent(currentPayOrderNo) + '&payType=' + encodeURIComponent(method) + (scene ? '&scene=' + scene : '') + (openid ? '&openid=' + encodeURIComponent(openid) : '')
            });
            var payData = await resp.json();
            if (payData.code === 0 && payData.data) {
              var d = payData.data;
              if (d.jsapi) {
                // 微信内 JSAPI 拉起收银台
                launchJsapi(d.jsapi);
                return;
              }
              if (d.mwebUrl) {
                // 微信H5支付：跳转拉起微信收银台
                location.href = d.mwebUrl;
                return;
              }
              if (d.wapForm) {
                // 支付宝WAP支付：提交form拉起支付宝收银台
                var wf = document.createElement('div');
                wf.style.display = 'none';
                wf.innerHTML = d.wapForm;
                document.body.appendChild(wf);
                var wform = wf.querySelector('form');
                if (wform) { wform.submit(); return; }
              }
              showMerchantQR(method, d);
            } else {
              showMerchantQR(method, null);
            }
          } catch(e) {
            showMerchantQR(method, null);
          }
        } else {
          showMerchantQR(method, null);
        }
        var lb = payLabels[method] || '其它';
        var emoji = method === 'wechat' ? '💚' : (method === 'alipay' ? '💙' : (method === 'unionpay' ? '💜' : '❓'));
        var color = method === 'wechat' ? '#1aad19' : (method === 'alipay' ? '#1677ff' : (method === 'unionpay' ? '#e60012' : '#9095a8'));
        var amt = (lastResult && lastResult.pay) || 0;
        var inWeChat = isInWeChat();
        var scheme = paySchemes[method];
        if (scheme && !inWeChat) {
          // Try to launch APP via iframe (stays on page)
          var iframe = document.createElement('iframe');
          iframe.style.cssText = 'display:none;width:0;height:0;';
          iframe.src = scheme;
          document.body.appendChild(iframe);
          setTimeout(function(){ try { document.body.removeChild(iframe); } catch(e){} }, 3000);
        }
        // Show step guide below QR
        var info = $('payInfo');
        if (info && method !== 'other') {
          var steps = scanSteps[method] || [];
          var stepsHtml = '';
          for (var si = 0; si < steps.length; si++) {
            stepsHtml += '<div style="display:flex;align-items:center;gap:6px;padding:3px 0;font-size:12px;color:var(--c-text-2);">'
              + '<span style="flex-shrink:0;width:18px;height:18px;border-radius:50%;background:' + color + ';color:#fff;display:inline-flex;align-items:center;justify-content:center;font-size:10px;font-weight:700;">' + (si+1) + '</span>'
              + '<span>' + steps[si] + '</span>'
              + '</div>';
          }
          var tip = '长按识别上方二维码完成付款';
          info.innerHTML = '<div style="text-align:left;max-width:280px;margin:0 auto;">' + stepsHtml + '</div>'
            + '<div style="text-align:center;margin-top:4px;font-size:11px;color:var(--c-text-3);">' + tip + '</div>';
        } else if (info && method === 'other') {
          info.textContent = '长按上方二维码识别完成付款';
        }
      });
    });
  })();

  // 完成按钮
  (function(){
    function finishPay(){
      if (_payPending) _payPending = false;
      toast('支付完成，正在返回首页', 'success');
      // 顾客端不播报，改为商家端播报（announce系统）
      setTimeout(function(){ location.href = '/h5/customer/index.html'; }, 1500);
    }
    var doneBtn = $('btnPayDone');
    if (doneBtn) doneBtn.addEventListener('click', finishPay);

    // 监听从APP返回浏览器: 自动完成支付
    function _onReturnFromApp(){
      if (_payPending) {
        _payPending = false;
        setTimeout(function(){
          if ($('payMask')) $('payMask').classList.add('show');
          var payInfo = $('payInfo');
          if (payInfo) payInfo.textContent = '已返回，正在完成支付...';
          toast('检测到已返回，正在完成支付', 'success');
          setTimeout(finishPay, 1000);
        }, 500);
      }
    }
    document.addEventListener('visibilitychange', function(){
      if (document.visibilityState === 'visible') _onReturnFromApp();
    });
    window.addEventListener('pageshow', function(e){
      if (e.persisted) _onReturnFromApp();
    });

    // 拦截浏览器返回键: 强制结束付款流程
    // 压入一个历史状态, 用户按返回时触发 popstate 而不是真的后退
    try {
      history.pushState({ payGuard: true }, '', location.href);
    } catch(e){}
    window.addEventListener('popstate', function(e){
      // 用户按了返回键 -> 强制完成支付结束订单
      if ($('payMask')) $('payMask').classList.add('show');
      finishPay();
    });
    var doneHome = $('btnDoneHome');
    if (doneHome) doneHome.addEventListener('click', function(){ location.href = '/h5/customer/index.html'; });
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
      // 测试阶段：真实支付固定 0.01 元（防止误扣大额，验收后移除本段）
      if (amt > 0.01) {
        toast('测试阶段仅支持 0.01 元支付，请将金额改为 0.01', 'warning');
        this.disabled = false; this.textContent = '💳 确认下单 ¥' + fmt(lastResult ? lastResult.pay : 0);
        return;
      }
      var useBal = $('balCheck') && $('balCheck').checked;
      var mno2 = (function(){ try { var m = location.search.match(/merchantNo=([^&]+)/); if (m) { localStorage.setItem('ibigou_merchant_no', m[1]); return m[1]; } var _s = localStorage.getItem('ibigou_merchant_no'); if (_s === 'M001') { localStorage.removeItem('ibigou_merchant_no'); return ''; } return _s || ''; } catch(e){ return ''; } })();
      var r;
      try {
        r = await api.customer.offlineOrder({
          userPhone: ph, merchantNo: mno2, orderAmount: amt,
          paidAmount: lastResult ? lastResult.pay : amt,
          rule: (amt !== 0.01 && prizeRule && prizeRule.type && prizeRule.type !== 'none') ? JSON.stringify(prizeRule) : undefined
        });
      } catch(e) {
        console.warn('[pay] order api error', e);
        r = { code: -1, msg: '下单失败：' + e.message };
      }
      if (r && r.code === 0 && r.data) {
        orderNo = r.data.offlineOrderNo || r.data.orderNo || ('ORD' + Date.now());
        currentPayOrderNo = orderNo;
        await refreshBalance();
        renderBalance();
        var _finalAmt = (r.data.payAmount != null) ? r.data.payAmount : (lastResult ? lastResult.pay : 0);
        if ($('payAmount')) $('payAmount').textContent = '¥' + fmt(parseFloat(_finalAmt));
        if ($('payMask')) $('payMask').classList.add('show');
        if ($('qrEmpty')) $('qrEmpty').style.display = 'none';
        if ($('payQr')) $('payQr').style.display = 'none';
        // API mode: hide unionpay/other, only wechat/alipay (2026-09-10)
        var isApiMode = !(lastResult && lastResult.qrWechat);
        var _ub = document.querySelector('[data-pay="unionpay"]');
        var _ob = document.querySelector('[data-pay="other"]');
        if (isApiMode) {
          if (_ub) _ub.style.display = 'none';
          if (_ob) _ob.style.display = 'none';
        } else {
          if (_ub) _ub.style.display = '';
          if (_ob) _ob.style.display = '';
        }
        // 语音播报(区分渠道):含输入金额、优惠、实付
        try {
          var _input = parseFloat($('amountInput').value) || 0;
          var _save = (lastResult && lastResult.save) || 0;
          var _pay = parseFloat(r.data.finalAmount) || 0;
          // 顾客端不播报，改为商家端播报（announce系统）
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



