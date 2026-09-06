/* tts.js — 语音播报（Web Speech API，中文女声）
 * 提供 speak / speakPrize / speakPay 三个全局函数
 * draw.js / pay.js 通过 ../tts.js 引用
 * 所有播报前缀"宜必购盲盒"
 */
(function () {
  var voice = null;
  var ready = false;
  var BRAND = '宜必购盲盒。';

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

  // 基础播报
  function speak(text, opts) {
    opts = opts || {};
    if (!('speechSynthesis' in window) || !text) return;
    try {
      speechSynthesis.cancel();
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'zh-CN';
      if (voice) u.voice = voice;
      u.rate = opts.rate != null ? opts.rate : 1.0;
      u.pitch = opts.pitch != null ? opts.pitch : 1.0;
      u.volume = opts.volume != null ? opts.volume : 1.0;
      speechSynthesis.speak(u);
    } catch (e) {
      console.warn('[tts] speak failed', e);
    }
  }

  // 开奖播报：宜必购盲盒 + 恭喜中奖 + 奖品名 + 渠道
  function speakPrize(prizeName, emoji, channel, chName) {
    var msg = BRAND + '恭喜您中奖了！';
    if (chName) msg += '参与方式' + chName + '。';
    if (prizeName) msg += ' 您抽中' + prizeName + '。';
    if (chName) msg += '本次为' + chName + '渠道。';
    msg += '请前往付款结算页完成下单。';
    speak(msg, { rate: 0.98 });
  }

  // 支付播报：宜必购盲盒 + 实付金额，可选带订单金额与节省
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