/* draw.js - 3D 盲盒（单个立体旋转盒 + 15s 揭晓） */
(function(){
  var tk = null;
  try { tk = localStorage.getItem("ibigou_user_token"); } catch(e){}
  if (!tk) { location.replace("/h5/customer/login.html"); return; }

  var PRIZE_POOL = [
    { id:"discount",  name:"9 折优惠券",         emoji:"🎟️", rule:{type:"discount", rate:0.9},                      weight:16 },
    { id:"cash5",     name:"¥5 立减券",          emoji:"💵", rule:{type:"minus", amount:5},                          weight:14 },
    { id:"gift",      name:"神秘礼品一份",       emoji:"🎁", rule:null,                                              weight:14 },
    { id:"vip",       name:"VIP 折扣券",         emoji:"💎", rule:{type:"discount", rate:0.95},                     weight:12 },
    { id:"fullminus", name:"满 100 减 20 券",    emoji:"✨", rule:{type:"threshold", threshold:100, minus:20},      weight:10 },
    { id:"cash10",    name:"¥10 立减券",         emoji:"💸", rule:{type:"minus", amount:10},                         weight:10 },
    { id:"giftbag",   name:"购物礼包",           emoji:"🛍️", rule:null,                                              weight:8  },
    { id:"bigmoney",  name:"¥20 余额",           emoji:"💰", amount:20, rule:{type:"balance", amount:20},               weight:6  },
    { id:"suprise",   name:"惊喜盲盒",           emoji:"📦", rule:null,                                              weight:5  },
    { id:"lucky2x",   name:"幸运加倍券",         emoji:"🍀", rule:{type:"half_cap", cap:30},                          weight:3  },
    { id:"freeme",    name:"免单一次（按输入金额全额入余额）", emoji:"👑", rule:{type:"free"},                                  weight:1  },
    { id:"thanks",    name:"谢谢参与",           emoji:"🙏", rule:null,                                              weight:2  }
  ];
  var mainPrize = null;


  var MERCHANT_NO = (function(){ try { var m = location.search.match(/merchantNo=([^&]+)/); if (m) return m[1]; return localStorage.getItem('ibigou_merchant_no') || 'M001'; } catch(e){ return 'M001'; } })();

  // 商家名称 -> 水果类型映射
  var SHOP_FRUIT_MAP = {
    "鲜果": ["🥭","🍎","🍊","🍇","🍉","🍌"],
    "奶茶": ["🧋","🍦","🍰","🍩","🍬","🍫"],
    "火锅": ["🥲","🍗","🥤","🍖","🥚","🍟"],
    "烧烤": ["🍖","🥗","🥨","🍯","🍞","🥟"],
    "咖啡": ["☕","🍵","🍬","🍪","🍰","🍫"],
    "烘焙": ["🥐","🥞","🍪","🍰","🥧","🍭"],
    "水果": ["🥭","🍎","🍊","🍇","🍉","🍌"],
    "零食": ["🍯","🍫","🍬","🥛","🥘","🍟"],
    "甜品": ["🍰","🍭","🍬","🍮","🥧","🍨"],
    "默认": ["🎁","🎒","🎉","🎊","🧨","🎵"]
  };

  function getShopFruits(){
    var shopName = "";
    try {
      var el = document.querySelector(".shop-name");
      if (el) shopName = el.textContent || "";
    } catch(e){}
    if (!shopName) shopName = "默认";
    for (var key in SHOP_FRUIT_MAP) {
      if (shopName.indexOf(key) >= 0) return SHOP_FRUIT_MAP[key];
    }
    return SHOP_FRUIT_MAP["默认"];
  }

  function setCubeFruits(){
    var fruits = getShopFruits();
    var faces = document.querySelectorAll(".cube .face");
    faces.forEach(function(f, i){
      f.textContent = fruits[i % fruits.length];
    });
  }
  setCubeFruits();

  function qs(name){
    var s = location.search.substring(1).split("&");
    for (var i=0;i<s.length;i++){ var p=s[i].split("="); if (p[0]===name) return decodeURIComponent(p[1]||""); }
    return "";
  }
  var channelMap = { "1":"本店消费", "2":"美团", "3":"饿了么", "4":"抖音" };
  var ch = qs("channel") || localStorage.getItem("ibigou_channel") || "1";
  var chName = channelMap[ch] || "本店消费";
  var channelChipEl = document.getElementById("channelChip");
  if (channelChipEl) channelChipEl.textContent = chName;
  try { localStorage.setItem("ibigou_channel", ch); } catch(e){}

  // 实时开奖列表
  var SAMPLE = ["138****6621","159****0083","176****2210","185****4499","133****7752","186****1120","151****3344","137****0098","170****6655","188****1234","139****5566","152****9911","158****7733","177****4422","135****8800","180****6611","137****2299","139****0086","158****7711","188****5566"];
  var SAMPLE_PRIZE = [{e:"🎟️",n:"9折优惠券"},{e:"💵",n:"¥5立减券"},{e:"💰",n:"¥20余额"},{e:"👑",n:"免单一次"},{e:"🎁",n:"神秘礼品"},{e:"✨",n:"满减优惠券"},{e:"💎",n:"VIP折扣"},{e:"🛍️",n:"购物礼包"},{e:"📦",n:"惊喜盲盒"},{e:"🍀",n:"幸运加倍"},{e:"🎯",n:"精准优惠"}];
  function rnd(arr){ return arr[Math.floor(Math.random()*arr.length)]; }
  function buildFeed(){
    var track = document.getElementById("liveTrack");
    if (!track) return;
    // ??? wrap ?? mask ??????????
    if (track.parentElement && !track.parentElement.classList.contains("live-track-wrap")){
      var wrap = document.createElement("div");
      wrap.className = "live-track-wrap";
      track.parentElement.insertBefore(wrap, track);
      wrap.appendChild(track);
    }
    var list = [];
    for (var i=0;i<14;i++){
      var p = rnd(SAMPLE_PRIZE);
      list.push({ phone: rnd(SAMPLE), prize: p });
    }
    var html = "";
    // ???????????CSS animation translateX(-50%)?
    list.concat(list).forEach(function(it){
      html += '<div class="live-item">'
            + '<span class="ic">' + it.prize.e + '</span>'
            + '<span class="ph">' + it.phone + '</span>'
            + '<span class="pz">?? ' + it.prize.n + '</span>'
            + '</div>';
    });
    track.innerHTML = html;
  }
  buildFeed();

  // 浮动粒子
  function buildParticles(){
    var host = document.getElementById("particles");
    if (!host) return;
    var emojis = ["🎟️","💵","💰","👑","🎁","✨","💎","🛍️","📦","🍀","🎯","⚡","🔥","🏆","🥇","🥈","💸","🎉"];
    var html = "";
    for (var i=0;i<10;i++){
      var e = emojis[Math.floor(Math.random()*emojis.length)];
      var left = 5 + Math.random()*85;
      var delay = Math.random()*5;
      var dur = 4 + Math.random()*3;
      html += '<span class="particle" style="left:'+left+'%;bottom:8%;animation-delay:'+delay+'s;animation-duration:'+dur+'s;">' + e + '</span>';
    }
    host.innerHTML = html;
  }
  buildParticles();

  // 抽一个奖项
  async function pickPrize(){
    var ph = null; try { ph = localStorage.getItem('ibigou_phone'); } catch(e){}
    var api = window.IBIGOU_API;
    if (api && ph) {
      var r = await api.customer.drawNormal(ph, MERCHANT_NO);
      if (r && r.code === 0 && r.data && r.data.prize) {
        var pr = r.data.prize;
        var storeOnly = !!pr.storeOnly;
        var rule = null;
        if (pr.prizeType === 1) rule = { type:'discount', rate: 1 - pr.prizeValue/100 };
        else if (pr.prizeType === 2) rule = { type:'minus', amount: pr.prizeValue };
        else if (pr.prizeType === 3) rule = { type:'balance', amount: pr.prizeValue, storeOnly: storeOnly };
        else if (pr.prizeType === 4) rule = { type:'free', storeOnly: true };
        return { id:'api_'+pr.prizeId, name: pr.prizeName, emoji: pr.prizeEmoji || '🎁', rule: rule, amount: pr.prizeType===3 ? pr.prizeValue : 0, storeOnly: storeOnly };
      }
      console.warn('[draw] draw api failed, fallback', r);
    }
    var pool = PRIZE_POOL;
    var total = pool.reduce(function(s,p){return s+p.weight;}, 0);
    var rr = Math.random() * total;
    for (var i=0;i<pool.length;i++){ rr -= pool[i].weight; if (rr <= 0) return pool[i]; }
    return pool[pool.length-1];
  }

  var rolling = false;
  var startT = 0;

  async function startDraw(){
    if (rolling) return;
    rolling = true;
    mainPrize = await pickPrize();

    document.getElementById("prompt").classList.remove("show");
    document.getElementById("btnAgain").style.display = "none";
    document.getElementById("btnOpen").style.display = "none";

    var cube = document.getElementById("cube");
    cube.classList.remove("opened");
    // 强制重置 transform 后重新启动动画
    cube.style.animation = "none";
    cube.offsetHeight; // 触发 reflow
    cube.style.animation = "";
    cube.classList.add("rolling");

    startT = performance.now();
    requestAnimationFrame(tick);
  }

  function tick(now){
    if (!rolling) return;
    var elapsed = now - startT;
    var remain = Math.max(0, 8000 - elapsed);
    var chip = document.getElementById("timeChip");
    if (chip) chip.textContent = (remain/1000).toFixed(1) + "s";

    if (elapsed >= 8000){
      onStop();
      return;
    }
    requestAnimationFrame(tick);
  }

  function onStop(){
    rolling = false;
    var cube = document.getElementById("cube");
    cube.classList.remove("rolling");
    cube.classList.add("opened");

    // 6 个面统一显示当前奖品的 emoji（顶/底也用同一图标）
    var faces = cube.querySelectorAll(".face");
    var em = mainPrize.emoji || "🥭";
    faces.forEach(function(f){ f.textContent = em; });

    // 舞台内提示：rank + 奖品
    var rank = (Math.floor(Math.random()*80) + 12);
    var stageR = document.getElementById("stageRankN");
    var stageP = document.getElementById("stagePrize");
    if (stageR) stageR.textContent = rank;
    if (stageP) stageP.innerHTML = '开中 <span class="em">' + mainPrize.emoji + '</span> ' + mainPrize.name;
    var sp = document.getElementById("stagePrompt");
    if (sp) sp.classList.add("show");

    // 桌面端完整卡片（兼容）
    document.getElementById("rank").textContent = rank;
    document.getElementById("prizeName").textContent = mainPrize.name;
    document.getElementById("line1").textContent = "恭喜你！";
    document.getElementById("prompt").classList.add("show");
    var ba2 = document.getElementById("btnAgain"); if (ba2) ba2.style.display = "block";
    var ba2 = document.getElementById("btnAgain"); if (ba2) ba2.style.display = "block";

    // 金额输入已迁移到 pay.html，本页不再内嵌金额框

    fireworks();

    // 语音播报 + 字幕显示
    var voiceText = '宜必购盲盒。恭喜您中奖了！';
    if (chName) voiceText += '参与方式' + chName + '。';
    if (mainPrize.name) voiceText += '您抽中' + mainPrize.name + '。';
    voiceText += '正在跳转付款结算页。';
    var vs = document.getElementById("voiceSubtitle");
    if (vs) { vs.textContent = voiceText; vs.style.display = "block"; }
    // 确保语音播报完整中奖内容
    try { speak(voiceText, { rate: 0.98 }); } catch(e){}
    if (typeof speakPrize === "function") {
      try { speakPrize(mainPrize.name, mainPrize.emoji, ch, chName); } catch(e){}
    }

    // 倒计时结束 -> 2秒后自动跳转结算页（不再显示 bigReveal 弹窗）
    setTimeout(function(){
      location.href = buildPayUrl(mainPrize || { id:"gift", name:"神秘礼品", emoji:"🎁" });
    }, 2000);

    // 入账统一移到 pay.js 处理（按渠道 + 输入金额）

    try {
      console.log("[draw] result", { channel: ch, prize: mainPrize.id });
    } catch(e){}
  }

  function showBigReveal(prize, rank){
    var el = document.getElementById("bigReveal");
    document.getElementById("bigPrizeName").textContent = prize.emoji + "  " + prize.name;
    document.getElementById("bigRank").textContent = chName + " 第 " + rank + " 位顾客";
    el.classList.add("show");
  }
  // "查看我的奖品"：跳到独立付款结算页 pay.html（带奖品+规则）
  document.getElementById("bigOk").addEventListener("click", function(){
    document.getElementById("bigReveal").classList.remove("show");
    location.href = buildPayUrl(mainPrize || { id:"gift", name:"神秘礼品", emoji:"🎁" });
  });

  function fireworks(){
    var fw = document.getElementById("fw");
    if (!fw) return;
    fw.innerHTML = "";
    var colors = ["#ff5b3e","#ffd54a","#ff9567","#16a34a","#2563eb","#fff","#a78bfa"];
    for (var i=0;i<60;i++){
      var sp = document.createElement("span");
      sp.className = "spark";
      sp.style.background = colors[i%colors.length];
      sp.style.color = colors[i%colors.length];
      sp.style.left = (10 + Math.random()*80) + "%";
      sp.style.top = (10 + Math.random()*60) + "%";
      sp.style.setProperty("--dx", (Math.random()*240-120) + "px");
      sp.style.setProperty("--dy", (Math.random()*240-120) + "px");
      sp.style.animationDelay = (Math.random()*0.3) + "s";
      fw.appendChild(sp);
    }
    setTimeout(function(){ fw.innerHTML = ""; }, 1800);
  }

  document.getElementById("btnOpen").addEventListener("click", function(){
    // 用户交互 → 预热 TTS（部分手机浏览器需要先激活语音权限）
    if (typeof speak === "function") {
      try { speak(" ", { volume: 0, rate: 1.0 }); } catch(e){}
    }
    startDraw();
  });
  // "去付款结算"按钮：直接跳到 pay.html（金额输入在那边）
  document.getElementById("btnAgain").addEventListener("click", function(){
    location.href = buildPayUrl(mainPrize || { id:"gift", name:"神秘礼品", emoji:"🎁" });
  });

  // 构造 pay.html URL（带奖品规则 JSON）
  function buildPayUrl(p){
    var url = "/h5/customer/pay.html"
      + "?prize=" + encodeURIComponent(p.id)
      + "&prizeName=" + encodeURIComponent(p.name)
      + "&prizeEmoji=" + encodeURIComponent(p.emoji)
      + "&channel=" + encodeURIComponent(ch);
    if (p.rule) url += "&rule=" + encodeURIComponent(JSON.stringify(p.rule));
    if (p.amount) url += "&amount=" + encodeURIComponent(p.amount);
    return url;
  }
})();