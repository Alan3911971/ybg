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
    var mno = (function(){ try { return localStorage.getItem('ibigou_merchant_no') || 'M001'; } catch(e){ return 'M001'; } })();
    if (ph) {
      try {
        var r = await api.customer.offlineCalc({
          userPhone: ph, merchantNo: mno, orderAmount: amt
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


  // 摄像头扫码
  function startCameraScan(label){
    var mask = $('payMask');
    var video = $('scanVideo');
    var videoWrap = $('scanVideoWrap');
    var qrEmpty = $('qrEmpty');
    var payQr = $('payQr');
    var payInfo = $('payInfo');

    // 隐藏聚合码区域，显示摄像头
    if (qrEmpty) qrEmpty.style.display = 'none';
    if (payQr) payQr.style.display = 'none';
    if (videoWrap) videoWrap.style.display = 'block';
    if (payInfo) payInfo.textContent = label + '扫码：请对准商家收款码';

    if (mask) mask.classList.add('show');

    // 停止之前的视频流
    if (window._payScanStream) {
      try { window._payScanStream.getTracks().forEach(function(t){ t.stop(); }); } catch(e){}
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      if (payInfo) payInfo.textContent = '当前浏览器不支持摄像头扫码，请长按下方二维码识别';
      if (qrEmpty) qrEmpty.style.display = 'block';
      if (videoWrap) videoWrap.style.display = 'none';
      return;
    }

    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
      .then(function(stream){
        window._payScanStream = stream;
        if (video) {
          video.srcObject = stream;
          video.setAttribute('playsinline', '');
          video.play();
        }
      })
      .catch(function(err){
        if (payInfo) payInfo.textContent = '无法访问摄像头：' + (err.message || '请授权') + '，请长按二维码识别';
        if (qrEmpty) qrEmpty.style.display = 'block';
        if (videoWrap) videoWrap.style.display = 'none';
      });
  }

  function stopCameraScan(){
    if (window._payScanStream) {
      try { window._payScanStream.getTracks().forEach(function(t){ t.stop(); }); } catch(e){}
      window._payScanStream = null;
    }
  }



  // 你扫我：微信/支付宝直接调起APP付款码
  (function(){
    var ysGrid = $('ysGrid');
    if (!ysGrid) return;
    // 微信付款码 scheme, 支付宝付款码 scheme
    var ysSchemes = {
      wechat: 'weixin://pay/scanqrcode',
      alipay: 'alipays://platformapi/startapp?appId=20000056'
    };
    ysGrid.querySelectorAll('.ys-btn').forEach(function(btn){
      btn.addEventListener('click', function(){
        var method = btn.getAttribute('data-ys');
        var label = method === 'wechat' ? '微信' : '支付宝';
        var color = method === 'wechat' ? '#1aad19' : '#1677ff';
        // 高亮选中
        ysGrid.querySelectorAll('.ys-btn').forEach(function(b){
          b.style.background = '#fff'; b.style.color = b.getAttribute('data-ys') === 'wechat' ? '#1aad19' : '#1677ff';
        });
        btn.style.background = method === 'wechat' ? '#f0fff0' : '#f0f5ff';
        // 显示提示区域
        var ysQrWrap = $('ysQrWrap');
        var ysQrTitle = $('ysQrTitle');
        var ysQrBox = $('ysQrBox');
        if (ysQrWrap) ysQrWrap.style.display = 'block';
        if (ysQrTitle) { ysQrTitle.textContent = label + '付款码'; ysQrTitle.style.color = color; }
        var amt = (lastResult && lastResult.pay) || 0;
        if (ysQrBox) {
          ysQrBox.innerHTML = '<div style="width:200px;padding:16px 12px;background:#f7f8fb;border:2px dashed ' + color + ';border-radius:10px;text-align:center;font-size:12px;color:var(--c-text-2);line-height:1.8;"><span style="font-size:28px;display:block;margin-bottom:6px;">' + (method === 'wechat' ? '💚' : '💙') + '</span>正在打开' + label + '付款码<br>¥' + amt.toFixed(2) + '<br>请在' + label + '中出示给商家扫码</div>';
        }
        // 调起对应APP的付款码
        var scheme = ysSchemes[method];
        if (scheme) {
          toast('正在打开' + label + '付款码', 'success');
          if (typeof speak === 'function') { try { speak('宜必购盲盒。正在打开' + label + '付款码，请出示给商家扫码。'); } catch(e){} }
          _payPending = true;
          window.location.href = scheme;
          setTimeout(function(){
            if (ysQrBox) {
              ysQrBox.innerHTML = '<div style="width:200px;padding:16px 12px;background:#f7f8fb;border:2px dashed ' + color + ';border-radius:10px;text-align:center;font-size:12px;color:var(--c-text-2);line-height:1.8;"><span style="font-size:28px;display:block;margin-bottom:6px;">' + (method === 'wechat' ? '💚' : '💙') + '</span>' + label + '付款码已调起<br>¥' + amt.toFixed(2) + '<br>请在' + label + '中出示给商家扫码</div>';
            }
          }, 1500);
        }
      });
    });
  })();

  // 扫码方向切换：我扫你 / 你扫我
  (function(){
    var toggle = $('scanToggle');
    if (!toggle) return;
    var grid = $('payGrid');
    var yousweep = $('yousweepArea');
    var qrEmpty = $('qrEmpty');
    var payQr = $('payQr');
    var payInfo = $('payInfo');
    toggle.querySelectorAll('.st-btn').forEach(function(btn){
      btn.addEventListener('click', async function(){
        var mode = btn.getAttribute('data-mode');
        toggle.querySelectorAll('.st-btn').forEach(function(b){ b.classList.remove('active'); });
        btn.classList.add('active');
        if (mode === 'isweep') {
          // 我扫你：显示APP按钮，隐藏你扫我区域
          if (grid) grid.style.display = 'grid';
          if (yousweep) yousweep.style.display = 'none';
          if (qrEmpty) qrEmpty.style.display = 'none';
          if (payQr) payQr.style.display = 'none';
          if (payInfo) payInfo.textContent = '选择付款方式调起APP扫一扫';
        } else {
          // 你扫我：隐藏APP按钮，显示微信/支付宝选择
          if (grid) grid.style.display = 'none';
          if (yousweep) yousweep.style.display = 'block';
          if (qrEmpty) qrEmpty.style.display = 'none';
          if (payQr) payQr.style.display = 'none';
          if (payInfo) payInfo.textContent = '选择付款方式生成付款码';
          var ysQrWrap = $('ysQrWrap');
          if (ysQrWrap) ysQrWrap.style.display = 'none';
        }
      });
    });
  })();

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
    btns.forEach(function(btn){
      btn.addEventListener('click', async function(){
        var method = btn.getAttribute('data-pay') || btn.textContent.trim();
        if (method === 'other') {
          if ($('qrEmpty')) $('qrEmpty').style.display = 'block';
          if ($('payQr')) $('payQr').style.display = 'none';
          if ($('payInfo')) $('payInfo').textContent = '请长按下方二维码识别完成付款';
          await loadMerchantQR();
          if ($('payMask')) $('payMask').classList.add('show');
        } else {
          var lb = payLabels[method] || '对应';
          var scheme = paySchemes[method];
          if (scheme) {
            if ($('payInfo')) $('payInfo').textContent = '正在打开' + lb + '扫一扫...';
            toast('正在打开' + lb + '扫一扫', 'success');
            window.location.href = scheme;
            setTimeout(function(){
              if ($('payInfo')) $('payInfo').textContent = lb + '扫一扫已调起，请在' + lb + '中完成扫码支付';
            }, 1500);
          } else {
            startCameraScan(lb);
          }
        }
      });
    });
  })();

  // 完成按钮
  (function(){
    function finishPay(){
      if (_payPending) _payPending = false;
      toast('支付完成，正在返回首页', 'success');
      if (typeof speak === 'function') { try { speak('宜必购盲盒。支付完成，正在返回首页。请商家查收是否真实付款成功，宜必购盲盒只是做优惠，不做收款，请注意。'); } catch(e){} }
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
      var useBal = $('balCheck') && $('balCheck').checked;
      var mno2 = (function(){ try { return localStorage.getItem('ibigou_merchant_no') || 'M001'; } catch(e){ return 'M001'; } })();
      var r;
      try {
        r = await api.customer.offlineOrder({
          userPhone: ph, merchantNo: mno2, orderAmount: amt,
          paidAmount: lastResult ? lastResult.pay : amt
        });
      } catch(e) {
        console.warn('[pay] order api error', e);
        r = { code: -1, msg: '下单失败：' + e.message };
      }
      if (r && r.code === 0 && r.data) {
        orderNo = r.data.offlineOrderNo || r.data.orderNo || ('ORD' + Date.now());
        await refreshBalance();
        renderBalance();
        var _finalAmt = (r.data.payAmount != null) ? r.data.payAmount : (lastResult ? lastResult.pay : 0);
        if ($('payAmount')) $('payAmount').textContent = '¥' + fmt(parseFloat(_finalAmt));
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



