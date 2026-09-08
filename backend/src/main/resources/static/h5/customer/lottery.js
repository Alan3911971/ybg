/* ============================================================
 * lottery.js  · YBG-C-FLOW-001-SPARK-001
 * 6 大中奖品类动效调度核心
 * 依赖: lottery.css
 * 全局: window.LotteryFX
 * 用法: LotteryFX.show(prizeType, payload, onClose)
 *       prizeType: 1=折扣 2=立减 3=余额 4=免单 5=谢谢
 *       payload: { value, name, channel, originalAmount }
 *       onClose: 关闭后回调（可选）
 * ============================================================ */
(function (global) {
  'use strict';

  // ---------- 静音状态 ----------
  var MUTE_KEY = 'ibigou_lottery_mute';
  function isMuted() {
    try { return localStorage.getItem(MUTE_KEY) === '1'; } catch (e) { return false; }
  }
  function setMuted(v) {
    try { localStorage.setItem(MUTE_KEY, v ? '1' : '0'); } catch (e) {}
  }

  // ---------- 事件上报 (sendBeacon + console fallback) ----------
  function _safeJSON(v){ try { return JSON.stringify(v == null ? {} : v); } catch(e){ return '{}'; } }
  var _reportEndpoint = '/api/customer/lottery/track';
  function report(name, payload){
    var body = _safeJSON({ event: name, payload: payload || {}, t: Date.now() });
    try {
      if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
        var blob = new Blob([body], { type: 'application/json' });
        var ok = navigator.sendBeacon(_reportEndpoint, blob);
        if (ok) return;
      }
    } catch (e) {
      try { console.error('[LotteryFX] sendBeacon failed', e); } catch(_){}
    }
    try {
      fetch(_reportEndpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body, keepalive: true }).catch(function(){});
    } catch (e) {
      try { console.error('[LotteryFX] report failed', name, e); } catch(_){}
    }
  }

  // ---------- 音频对象池（占位：URL 由 hermes 后补真实 mp3/ogg） ----------
  var AUDIO_POOL = {
    thanks:    null, // 谢谢参与 轻叮一声
    discount:  null, // 折扣券 数字燃烧
    cash:      null, // 立减券 金币
    goods:     null, // 实物 白光
    bigmoney:  null, // 大额余额 礼花
    freeme:    null  // 免单   礼炮+欢呼
  };
  var AUDIO_URL = {
    thanks:   './assets/lottery/thanks.ogg',
    discount: './assets/lottery/discount.ogg',
    cash:     './assets/lottery/cash.ogg',
    goods:    './assets/goods.ogg',
    bigmoney: './assets/lottery/bigmoney.ogg',
    freeme:   './assets/lottery/freeme.ogg'
  };
  function ensureAudio(key) {
    if (AUDIO_POOL[key]) return AUDIO_POOL[key];
    try {
      var a = new Audio(AUDIO_URL[key]);
      a.preload = 'none';
      AUDIO_POOL[key] = a;
      return a;
    } catch (e) { return null; }
  }
  function playSound(key) {
    if (isMuted()) return;
    var a = ensureAudio(key);
    if (!a) return;
    try { a.currentTime = 0; var p = a.play(); if (p && p.catch) p.catch(function(){}); } catch (e) {}
  }

  // ---------- 震动 ----------
  function vibrate(pattern) {
    try { if (navigator.vibrate) navigator.vibrate(pattern); } catch (e) {}
  }

  // ---------- 工具：随机数 ----------
  function rand(min, max) { return Math.random() * (max - min) + min; }
  function randInt(min, max) { return Math.floor(rand(min, max + 1)); }

  // ---------- 工具：弱网/低性能检测 ----------
  function isLowEnd() {
    try {
      var conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
      if (conn && (conn.saveData || (conn.effectiveType && /^(slow-2g|2g)$/.test(conn.effectiveType)))) return true;
      if (navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 2) return true;
    } catch (e) {}
    return false;
  }

  // ---------- 创建舞台 ----------
  function createStage(themeClass, locked) {
    var stage = document.createElement('div');
    stage.className = 'spark-stage ' + themeClass;
    if (isLowEnd()) stage.setAttribute('data-lowend', '1');

    var muteBtn = document.createElement('button');
    muteBtn.className = 'spark-mute';
    muteBtn.type = 'button';
    muteBtn.setAttribute('role', 'switch');
    muteBtn.setAttribute('aria-checked', isMuted() ? 'true' : 'false');
    muteBtn.setAttribute('aria-label', '\u9759\u97f3\u5207\u6362');
    muteBtn.textContent = isMuted() ? '\ud83d\udd0a' : '\ud83c\udfa7';
    muteBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var m = !isMuted(); setMuted(m);
      muteBtn.textContent = m ? '\ud83d\udd0a' : '\ud83c\udfa7';
      try { report('lottery_mute_toggle', { on: m }); } catch(e){}
    });
    stage.appendChild(muteBtn);

    if (!locked) {
      var skipBtn = document.createElement('button');
      skipBtn.className = 'spark-skip';
      skipBtn.type = 'button';
      skipBtn.textContent = '\u8df3\u8fc7';
      skipBtn.addEventListener('click', function (e) {
        e.stopPropagation(); stage.dispatchEvent(new Event('spark-close'));
        try { report('lottery_skip', { locked: !!locked }); } catch(e){}
      });
      stage.appendChild(skipBtn);
    }
    return stage;
  }

  // ---------- 关闭舞台 ----------
  function closeStage(stage, onClose, delay) {
    if (!stage || !stage.parentNode) return;
    stage.setAttribute('data-leaving', '1');
    setTimeout(function () {
      if (stage.parentNode) stage.parentNode.removeChild(stage);
      if (typeof onClose === 'function') try { onClose(); } catch (e) {}
    }, delay == null ? 350 : delay);
  }

  // ============================================================
  // 方案 1 · 谢谢参与
  // ============================================================
  function renderThanks(payload, onClose) {
    var stage = createStage('spark-thanks', false);
    var num = document.createElement('div');
    num.className = 'spark-num'; num.textContent = '\u8c22\u8bfe\u53c2\u4e0e';
    stage.appendChild(num);
    var title = document.createElement('div');
    title.className = 'spark-title';
    title.textContent = '\u672c\u6b21\u672a\u4e2d\u5956';
    stage.appendChild(title);
    var sub = document.createElement('div');
    sub.className = 'spark-sub'; sub.textContent = '\u4e0b\u6b21\u8fd8\u6709\u673a\u4f1a\uff0c\u62bd\u4e00\u6b21\u8bd5\u8bd5';
    stage.appendChild(sub);
    var cta = document.createElement('button');
    cta.className = 'spark-cta spark-cta--gold';
    cta.textContent = '\u518d\u62bd\u4e00\u6b21';
    cta.addEventListener('click', function () { stage.dispatchEvent(new Event('spark-close')); });
    stage.appendChild(cta);

    // 粒子
    var n = isLowEnd() ? 8 : 24;
    for (var i = 0; i < n; i++) {
      var p = document.createElement('span');
      p.className = 'thanks-particle';
      p.style.left = rand(10, 90) + '%';
      p.style.top  = rand(40, 70) + '%';
      p.style.setProperty('--dx', rand(-160, 160) + 'px');
      p.style.setProperty('--dy', rand(-180, -40) + 'px');
      p.style.animationDelay = (i * 0.05) + 's';
      stage.appendChild(p);
    }

    playSound('thanks');
    document.body.appendChild(stage);
    stage.addEventListener('spark-close', function () { try { report('lottery_close_cta'); } catch(e){} closeStage(stage, onClose); }, { once: true });
    setTimeout(function () { stage.dispatchEvent(new Event('spark-close')); }, 2600);
    setTimeout(done, 3000);
    setTimeout(done, 3000);
  }

  // ============================================================
  // 方案 2 · 折扣券（N 折）
  // ============================================================
  function renderDiscount(payload, onClose) {
    var value = Number(payload && payload.value) || 7;
    var stage = createStage('spark-discount', false);
    var flame = document.createElement('div');
    flame.className = 'discount-flame';
    stage.appendChild(flame);
    var num = document.createElement('div');
    num.className = 'spark-num';
    num.textContent = value + ' \u6298';
    stage.appendChild(num);
    var title = document.createElement('div');
    title.className = 'spark-title';
    title.textContent = '\u62bd\u4e2d ' + value + ' \u6298\uff01';
    stage.appendChild(title);
    var savePct = (10 - value) * 10;
    var sub = document.createElement('div');
    sub.className = 'spark-sub';
    sub.textContent = '\u4e0b\u6b21\u6d88\u8d39\u7acb\u7701 ' + savePct + '%';
    stage.appendChild(sub);
    var cta = document.createElement('button');
    cta.className = 'spark-cta';
    cta.textContent = '\u7acb\u5373\u4f7f\u7528';
    cta.addEventListener('click', function () { stage.dispatchEvent(new Event('spark-close')); });
    stage.appendChild(cta);

    var n = isLowEnd() ? 10 : 28;
    for (var i = 0; i < n; i++) {
      var s = document.createElement('span');
      s.className = 'discount-spark';
      s.style.left = rand(20, 80) + '%';
      s.style.top  = rand(50, 60) + '%';
      s.style.setProperty('--dx', rand(-180, 180) + 'px');
      s.style.setProperty('--dy', rand(-260, -120) + 'px');
      s.style.animationDelay = (i * 0.04) + 's';
      stage.appendChild(s);
    }

    playSound('discount');
    vibrate([20]);
    document.body.appendChild(stage);
    stage.addEventListener('spark-close', function () { try { report('lottery_close_cta'); } catch(e){} closeStage(stage, onClose); }, { once: true });
    setTimeout(function () { stage.dispatchEvent(new Event('spark-close')); }, 2800);
    setTimeout(done, 3200);
    setTimeout(done, 3200);
  }

  // ============================================================
  // 方案 3 · 立减券（X 元）
  // ============================================================
  function renderCash(payload, onClose) {
    var value = Number(payload && payload.value) || 10;
    var stage = createStage('spark-cash', false);
    var num = document.createElement('div');
    num.className = 'spark-num';
    num.textContent = '\xa5' + value;
    stage.appendChild(num);
    var title = document.createElement('div');
    title.className = 'spark-title';
    title.textContent = '\u7acb\u51cf \xa5' + value + '\uff01';
    stage.appendChild(title);
    var sub = document.createElement('div');
    sub.className = 'spark-sub';
    sub.textContent = '\u5df2\u8ba1\u5165\u60a8\u7684\u8d26\u6237';
    stage.appendChild(sub);
    var cta = document.createElement('button');
    cta.className = 'spark-cta spark-cta--gold';
    cta.textContent = '\u53bb\u4f7f\u7528';
    cta.addEventListener('click', function () { stage.dispatchEvent(new Event('spark-close')); });
    stage.appendChild(cta);

    var n = isLowEnd() ? 8 : 22;
    for (var i = 0; i < n; i++) {
      var c = document.createElement('span');
      c.className = 'cash-coin';
      c.style.left = rand(15, 85) + '%';
      c.style.setProperty('--sx', rand(-40, 40) + 'px');
      c.style.setProperty('--sy', rand(-30, -10) + 'vh');
      c.style.setProperty('--ex', rand(-120, 120) + 'px');
      c.style.setProperty('--ey', rand(40, 70) + 'vh');
      c.style.setProperty('--rot', randInt(360, 1080) + 'deg');
      c.style.animationDelay = (i * 0.04) + 's';
      stage.appendChild(c);
    }

    playSound('cash');
    vibrate([15]);
    document.body.appendChild(stage);
    stage.addEventListener('spark-close', function () { try { report('lottery_close_cta'); } catch(e){} closeStage(stage, onClose); }, { once: true });
    setTimeout(function () { stage.dispatchEvent(new Event('spark-close')); }, 2600);
  }

  // ============================================================
  // 方案 4 · 实物奖品（3D 旋转展示）
  // ============================================================
  function renderGoods(payload, onClose) {
    var name = (payload && payload.name) || '\u5b9e\u7269\u5956\u54c1';
    var emoji = (payload && payload.emoji) || '\ud83c\udf81';
    var stage = createStage('spark-goods', false);
    var rays = document.createElement('div');
    rays.className = 'goods-rays';
    stage.appendChild(rays);
    var img = document.createElement('div');
    img.className = 'spark-goods-img';
    img.textContent = emoji;
    stage.appendChild(img);
    var title = document.createElement('div');
    title.className = 'spark-title';
    title.textContent = '\u606d\u559c\uff01\u83b7\u5f97 ' + name;
    stage.appendChild(title);
    var sub = document.createElement('div');
    sub.className = 'spark-sub';
    sub.textContent = '\u8bf7\u524d\u5f80\u300c\u6211\u7684\u5956\u54c1\u300d\u67e5\u770b';
    stage.appendChild(sub);
    var cta = document.createElement('button');
    cta.className = 'spark-cta';
    cta.textContent = '\u67e5\u770b\u8be6\u60c5';
    cta.addEventListener('click', function () { stage.dispatchEvent(new Event('spark-close')); });
    stage.appendChild(cta);

    playSound('goods');
    document.body.appendChild(stage);
    stage.addEventListener('spark-close', function () { try { report('lottery_close_cta'); } catch(e){} closeStage(stage, onClose); }, { once: true });
    setTimeout(function () { stage.dispatchEvent(new Event('spark-close')); }, 3000);
    setTimeout(done, 3400);
  }

  // ============================================================
  // 方案 5 · 大额余额（史诗级 + 震屏）
  // ============================================================
  function renderBigMoney(payload, onClose) {
    var value = Number(payload && payload.value) || 100;
    var stage = createStage('spark-bigmoney', false);
    var rain = document.createElement('div');
    rain.className = 'bigmoney-rain';
    stage.appendChild(rain);
    var num = document.createElement('div');
    num.className = 'spark-num';
    num.textContent = '\xa5' + value;
    stage.appendChild(num);
    var title = document.createElement('div');
    title.className = 'spark-title';
    title.textContent = '\xa5' + value + ' \u4f59\u989d\u5230\u8d26\uff01';
    stage.appendChild(title);
    var sub = document.createElement('div');
    sub.className = 'spark-sub';
    sub.textContent = '\u5df2\u53d1\u653e\u5230\u60a8\u7684\u8d26\u6237';
    stage.appendChild(sub);
    var cta = document.createElement('button');
    cta.className = 'spark-cta spark-cta--gold';
    cta.textContent = '\u53bb\u67e5\u770b';
    cta.addEventListener('click', function () { stage.dispatchEvent(new Event('spark-close')); });
    stage.appendChild(cta);

    playSound('bigmoney');
    document.body.appendChild(stage);
    setTimeout(function () { stage.classList.add('shaking'); }, 700);
    vibrate([30, 50, 30]);
    stage.addEventListener('spark-close', function () { try { report('lottery_close_cta'); } catch(e){} closeStage(stage, onClose); }, { once: true });
    setTimeout(function () { stage.dispatchEvent(new Event('spark-close')); }, 2800);
  }

  // ============================================================
  // 方案 6 · 免单（史诗级 + 30s 锁定）
  // ============================================================
  function renderFreeMe(payload, onClose) {
    var original = Number(payload && payload.originalAmount) || 0;
    var stage = createStage('spark-freeme', true); // locked=true, no skip

    // 阶段 0：全黑 0.5s
    var black = document.createElement('div');
    black.className = 'freeme-black';
    stage.appendChild(black);

    // 阶段 0.5：金色冲击波
    var blast = document.createElement('div');
    blast.className = 'freeme-blast';
    stage.appendChild(blast);

    // 阶段 1.0：金光瀑布
    var fall = document.createElement('div');
    fall.className = 'freeme-fall';
    stage.appendChild(fall);

    // 阶段 1.5：¥0.00 数字爆破
    var num = document.createElement('div');
    num.className = 'spark-num';
    num.textContent = '\xa50.00';
    stage.appendChild(num);

    // 阶段 2.0：划掉的原价
    if (original > 0) {
      var strike = document.createElement('div');
      strike.className = 'freeme-strike';
      strike.textContent = '\u539f\u4ef7 \xa5' + original;
      stage.appendChild(strike);
    }

    // 阶段 2.5：礼花
    var confetti = document.createElement('div');
    confetti.className = 'freeme-confetti';
    var palette = ['#ff2d55','#ff5e3a','#ffd24c','#f6d365','#6c4cff','#ff5cd0','#2bd1ff','#00d68f'];
    var cn = isLowEnd() ? 24 : 80;
    for (var i = 0; i < cn; i++) {
      var s = document.createElement('span');
      s.style.left = rand(0, 100) + '%';
      s.style.top = rand(-5, 0) + 'vh';
      s.style.background = palette[randInt(0, palette.length - 1)];
      s.style.setProperty('--dx', rand(-80, 80) + 'px');
      s.style.setProperty('--rot', randInt(360, 1080) + 'deg');
      s.style.animationDelay = (2 + Math.random() * 1.2) + 's';
      s.style.animationDuration = rand(2, 3) + 's';
      confetti.appendChild(s);
    }
    stage.appendChild(confetti);

    // 阶段 3.0：旋转光环
    var ring = document.createElement('div');
    ring.className = 'freeme-ring';
    stage.appendChild(ring);

    // 阶段 3.0：主标题
    var title = document.createElement('div');
    title.className = 'spark-title';
    title.textContent = '\u5168\u5355\u514d\u5355\uff01';
    stage.appendChild(title);
    var sub = document.createElement('div');
    sub.className = 'spark-sub';
    sub.textContent = original > 0
      ? '\u539f\u4ef7 \xa5' + original + ' \u2192 \u5b9e\u4ed8 \xa50.00'
      : '\u5df2\u4e3a\u60a8\u8bb0\u5f55\uff0c\u8bf7\u5728\u672c\u5e97\u4efb\u610f\u6d88\u8d39\u65f6\u4f7f\u7528';
    stage.appendChild(sub);

    var extra = document.createElement('div');
    extra.className = 'spark-extra';
    extra.setAttribute('role', 'group');
    extra.setAttribute('aria-label', '免单交互按钮');
    var saveBtn = document.createElement('button');
    saveBtn.type = 'button';
    saveBtn.textContent = '📸 长按保存好运';
    saveBtn.setAttribute('aria-label', '长按保存好运图片');
    saveBtn.addEventListener('click', function(){
      try { report('freeme_save_click'); } catch(e){}
      try { alert('长按屏幕保存这张图片，分享给朋友😊'); } catch(e) {}
    });
    extra.appendChild(saveBtn);
    var shareBtn = document.createElement('button');
    shareBtn.type = 'button';
    shareBtn.textContent = '📤 分享给朋友';
    shareBtn.setAttribute('aria-label', '分享免单给朋友');
    shareBtn.addEventListener('click', function(){
      try { report('freeme_share_click'); } catch(e){}
      try {
        if (navigator.share) {
          navigator.share({ title: '我免单了！', text: '开盲盒免单了，进店看看！' }).catch(function(){});
        } else {
          window.open('https://api.qrserver.com/v1/create-qr-code/?size=240x240&data='+encodeURIComponent(window.location.href), '_blank');
        }
      } catch(e) {}
    });
    extra.appendChild(shareBtn);
    stage.appendChild(extra);

    var cta = document.createElement('button');
    cta.className = 'spark-cta spark-cta--red';
    cta.textContent = '\u518d\u62bd\u4e00\u6b21';
    cta.addEventListener('click', function () { stage.dispatchEvent(new Event('spark-close')); });
    stage.appendChild(cta);

    // 物理反馈：免单震动 3 次
    vibrate([60, 60, 60]);

    playSound('freeme');

    document.body.appendChild(stage);

    // 阶段 2.0 震屏
    setTimeout(function () { stage.classList.add('shaking'); }, 2000);

    // 30s 锁定后允许关闭（如果用户没主动点 CTA）
    var closed = false;
    stage.addEventListener('spark-close', function () {
      if (closed) return; closed = true;
      try { report('lottery_close_cta'); } catch(e){}
      closeStage(stage, onClose);
    }, { once: true });

    setTimeout(function () {
      if (!closed) stage.dispatchEvent(new Event('spark-close'));
    }, 3200); // 实际表现时长 3.2s，超 30s 锁定 CTA 可点
    setTimeout(done, 3700);
  }

  // ============================================================
  // 路由
  // ============================================================
  function show(prizeType, payload, onClose) {
    var pt = Number(prizeType);
    if (globalThis.__lotteryBusy) {
      try { console.warn('[LotteryFX] already busy, skip pt=' + pt); report('lottery_busy_skip', { prizeType: pt }); } catch(e){}
      if (typeof onClose === 'function') try { onClose(); } catch(e){}
      return;
    }
    globalThis.__lotteryBusy = true;
    var t0 = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
    function done(){
      if (globalThis.__lotteryDone) return;
      globalThis.__lotteryDone = true;
      var t1 = (typeof performance !== 'undefined' && performance.now) ? performance.now() : Date.now();
      try { report('lottery_close_auto', { prizeType: pt, duration_ms: Math.round(t1-t0) }); } catch(e){}
      globalThis.__lotteryBusy = false;
      if (typeof onClose === 'function') try { onClose(); } catch(e){}
    }
    try { globalThis.__lastPrizeType = pt; report('lottery_view', { prizeType: pt, scheme_id: Math.floor(Math.random()*9999), t0: Math.round(t0) }); } catch(e) {}
    switch (pt) {
      case 1: return renderDiscount(payload || {}, onClose);
      case 2: return renderCash(payload || {}, onClose);
      case 3: return renderBigMoney(payload || {}, onClose);
      case 4: return renderFreeMe(payload || {}, onClose);
      case 5: return renderThanks(payload || {}, onClose);
      case 6: return renderGoods(payload || {}, onClose); // 实物扩展
      default: return renderThanks({ name: '\u672a\u77e5\u5956\u54c1' }, onClose);
    }
  }

  global.LotteryFX = { show: show, isMuted: isMuted, setMuted: setMuted };
})(window);
