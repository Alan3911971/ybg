/* tts.js - 语音播报（HTML5 Audio + Web Speech API + 原生TTS）



 *




 */
(function () {
  var voice = null;
  var ready = false;
  var BRAND = '宜必购盲盒。';
  var _audioEl = null;
  var _audioFallbackTimer = null;

  function pickVoice() {
    if (!('speechSynthesis' in window)) return;
    var list = speechSynthesis.getVoices();
    if (!list || !list.length) return;
    voice = null;
    for (var i = 0; i < list.length; i++) {
      var v = list[i];
      if (/zh(-|_)CN/i.test(v.lang)) { voice = v; break; }
    }
    if (!voice) {
      for (var j = 0; j < list.length; j++) {
        if (/^zh/i.test(list[j].lang)) { voice = list[j]; break; }
      }
    }
    if (!voice) voice = list[0];
    ready = !!voice;
  }

  if ('speechSynthesis' in window) {
    pickVoice();
    if (!ready) {
      try { speechSynthesis.onvoiceschanged = pickVoice; } catch (e) {}
      setTimeout(pickVoice, 300);
    }
  }

  function speakAudio(text, opts) {
    opts = opts || {};
    var spd = opts.spd != null ? opts.spd : 5;
    var origin = (window.location && window.location.origin) ? window.location.origin : '';
    var url = origin + '/api/tts?lan=zh&text=' + encodeURIComponent(text) + '&spd=' + spd + '&source=web';
    try {
      if (_audioEl) { _audioEl.pause(); _audioEl = null; }
      if (_audioFallbackTimer) { clearTimeout(_audioFallbackTimer); _audioFallbackTimer = null; }
      _audioEl = new Audio(url);
      var done = false;
      _audioFallbackTimer = setTimeout(function(){
        if (!done) { done = true; console.warn('[tts] Audio timeout, fallback'); speakWeb(text, opts); }
      }, 3000);
      _audioEl.addEventListener('playing', function(){
        if (_audioFallbackTimer) { clearTimeout(_audioFallbackTimer); _audioFallbackTimer = null; }
        console.log('[tts] Audio playing');
      });
      _audioEl.addEventListener('error', function(){
        if (_audioFallbackTimer) { clearTimeout(_audioFallbackTimer); _audioFallbackTimer = null; }
        if (!done) { done = true; console.warn('[tts] Audio error, fallback'); speakWeb(text, opts); }
      });
      _audioEl.play().then(function(){
        console.log('[tts] Audio play resolved');
      }).catch(function(e){
        if (_audioFallbackTimer) { clearTimeout(_audioFallbackTimer); _audioFallbackTimer = null; }
        if (!done) { done = true; console.warn('[tts] Audio rejected, fallback', e); speakWeb(text, opts); }
      });
      return true;
    } catch (e) {
      console.warn('[tts] Audio init failed', e);
      return false;
    }
  }

  function speakWeb(text, opts) {
    opts = opts || {};
    if (!('speechSynthesis' in window) || !text) return false;
    try {
      speechSynthesis.cancel();
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'zh-CN';
      if (voice) u.voice = voice;
      u.rate = opts.rate != null ? opts.rate : 1.0;
      u.pitch = opts.pitch != null ? opts.pitch : 1.0;
      u.volume = opts.volume != null ? opts.volume : 1.0;
      speechSynthesis.speak(u);
      return true;
    } catch (e) {
      console.warn('[tts] Web Speech failed', e);
      return false;
    }
  }

  function speakNative(text) {
    try {
      if (window.AndroidPrinter && typeof window.AndroidPrinter.speak === 'function') {
        window.AndroidPrinter.speak(text);
        return true;
      }
    } catch (e) {}
    return false;
  }

  function speak(text, opts) {
    opts = opts || {};
    if (!text) return;
    if (speakAudio(text, opts)) return;
    if (speakWeb(text, opts)) return;
    speakNative(text);
  }

  function speakPrize(prizeName, emoji, channel, chName, rank) {
    var msg = '宜必购便民生活圈，恭喜您！您是本店第' + (rank || '') + '位顾客，恭喜您开出' + (prizeName || '神秘礼品') + '奖品，请问商家本次商品金额，输入金额即可享受抵扣结算。';
    speak(msg, { rate: 0.98 });
  }

  function speakPay(pay, prizeName, input, save) {
    var msg = BRAND;
    var chName2 = '';
    try { chName2 = ({ '1':'本店消费','2':'美团','3':'饿了么','4':'抖音' })[(localStorage.getItem('ibigou_channel')||'1')] || ''; } catch(e){}
    if (chName2) msg += '参与方式' + chName2 + '。';
    if (typeof input === 'number' && input > 0) msg += '订单金额' + money(input) + '元。';
    if (save && save > 0) msg += '优惠节省' + money(save) + '元。';
    msg += '应付' + money(pay || 0) + '元。';
    if (prizeName) msg += '已使用' + prizeName + '。';
    msg += '请选择支付方式完成付款。宜必购盲盒只是做优惠，不做收款，请商家查收是否真实付款成功，请注意。';
    speak(msg, { rate: 0.98 });
  }

  function money(n) {
    var v = Math.round((n || 0) * 100) / 100;
    return v.toFixed(2);
  }

  window.speak = speak;
  window.speakPrize = speakPrize;
  window.speakPay = speakPay;
})();
