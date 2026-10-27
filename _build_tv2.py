# -*- coding: utf-8 -*-
"""
电视大屏 tv.html 视觉重设计 -> tv-v2.html

铁律：**只改视觉层，不动几何骨架。**
APK 的原生视频播放器是一个独立于 WebView 的窗口，位置写死在
NativeVideoPlayer 里（vLeft=w*0.60+12d, vTop=68d, vw=w*0.40-28d, vh=h-276d）。
页面这套几何一旦漂移，视频就会错位压到队列上 —— 而且改页面改不回来，
必须重新编译 APK。所以几何常量在这里是"契约"，不是样式。

保留不动：顶栏 56 / 字幕 56 / 左 60% 右 40% / 右区 padding 12-16-12-12 /
         广告框 h-276（=444@720）/ 底部行 128 + margin 12（中奖排缩到 288）/
         右下二维码专属区 184×184（跨底部行 128 + 字幕 56 两排高度，码 148）/
         字幕右端收缩 204 / 5 列 19.4%+0.75% /
         队列滚动 12s / 中奖滚动 18s / 轮询 5000ms / 所有接口与业务逻辑

改的：配色（冷蓝 -> 暖炭）、层级（号码从 82 到 96、叫号横幅不再整块橙红）、
      桌型色（5 个饱和色 -> 降饱和 + 左侧色条）、emoji 全换（4.4 有豆腐块风险）、
      时间格式、列头信息重排、中奖弹窗版式

跑法：python _build_tv2.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import _tv_win_fx
from _tv_win_fx import WIN_CSS, WIN_HTML, WIN_JS

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, 'tv.html')
OUT = os.path.join(BASE, '_redesign_v3', 'tv-v2.html')

# ═════════════════════════════════════════════════════════════════════=
# 新 head + style
# ═════════════════════════════════════════════════════════════════════=
HEAD = '''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>宜必购共享营销系统 · 电视大屏</title>
<style>
/* ══════════════════════════════════════════════════════════════════════
   色板 —— 门店里给顾客看的屏：深靛蓝底 + 明亮桌型色 + 金色焦点
   底    #0b1220 -> #141d3a（渐变，另加两团柔光，不做死色块）
   面板  #131f3a · 卡 #18243f · 描边 rgba(96,165,250,.18)
   主字  #f1f5f9 · 次字 #94a3b8 · 弱字 #64748b
   品牌  #38bdf8 天蓝（标题 / 字幕）    强调 #fbbf24 金（叫号 / 中奖）
   桌型  A蓝 #3b82f6 · B绿 #10b981 · C橙 #f59e0b · D紫 #a855f7 · E粉 #ec4899
   ══════════════════════════════════════════════════════════════════════ */
*{margin:0;padding:0;box-sizing:border-box;}
html,body{height:100%;overflow:hidden;}
/* html 必须 hidden：glow 溢出 body 时，html 的 visible 会让浏览器改用 body 的
   overflow 并把文档撑出滚动条，挤掉 24px 宽、fixed 字幕跟着错位 */
body{font-family:"PingFang SC","Microsoft YaHei","Heiti SC",sans-serif;
     color:#f1f5f9;overflow:hidden;position:relative;background:#0b1220;
     background:-webkit-linear-gradient(155deg,#141d3a 0%,#0d1428 55%,#0b1220 100%);
     background:linear-gradient(155deg,#141d3a 0%,#0d1428 55%,#0b1220 100%);}

/* 两团柔光：径向渐变纯画出来，不用滤镜模糊（4.4 支持不全且掉帧）。
   万一渐变不被解析，glow 就是透明的，不会变成一块死色 —— 降级安全 */
.glow{position:absolute;border-radius:50%;z-index:0;pointer-events:none;}
.glow-a{left:-130px;top:-170px;width:640px;height:640px;
        background:-webkit-radial-gradient(circle,rgba(56,189,248,.17),rgba(56,189,248,0) 68%);
        background:radial-gradient(circle,rgba(56,189,248,.17),rgba(56,189,248,0) 68%);}
.glow-b{right:-90px;bottom:-150px;width:540px;height:540px;
        background:-webkit-radial-gradient(circle,rgba(251,191,36,.11),rgba(251,191,36,0) 68%);
        background:radial-gradient(circle,rgba(251,191,36,.11),rgba(251,191,36,0) 68%);}

/* ══ 顶栏 56px（APK 契约） ══ */
.header{height:56px;display:flex;justify-content:space-between;align-items:center;
        padding:0 22px;position:relative;z-index:10;background:#101a33;
        background:-webkit-linear-gradient(180deg,#16223f,#0e1729);
        background:linear-gradient(180deg,#16223f,#0e1729);
        border-bottom:1px solid rgba(96,165,250,.18);}
.brand{display:flex;align-items:center;}
.header h1{font-size:22px;font-weight:800;letter-spacing:.5px;color:#e0f2fe;
           text-shadow:0 0 18px rgba(56,189,248,.45);}
.merchant-no{font-size:12px;color:#7dd3fc;background:rgba(56,189,248,.12);
             border:1px solid rgba(56,189,248,.28);border-radius:11px;
             padding:2px 10px;margin-left:11px;letter-spacing:.5px;}
.right-group{display:flex;align-items:center;}
.wait-total{font-size:13px;color:#94a3b8;margin-right:14px;letter-spacing:.3px;}
.wait-total b{color:#fbbf24;font-size:19px;font-weight:800;margin:0 3px;
              text-shadow:0 0 12px rgba(251,191,36,.4);}
.tbtn{background:rgba(56,189,248,.12);color:#93c5fd;padding:5px 13px;
      border:1px solid rgba(56,189,248,.22);border-radius:13px;font-size:12px;
      cursor:pointer;margin-left:8px;font-family:inherit;letter-spacing:.5px;}
.time{font-size:18px;font-weight:700;color:#e0f2fe;margin-left:16px;
      font-family:"Helvetica Neue",Arial,sans-serif;letter-spacing:.5px;font-variant-numeric:tabular-nums;}
.time em{font-style:normal;font-size:11px;color:#64748b;margin-right:12px;letter-spacing:.5px;}
.time span{font-size:12px;color:#64748b;}

/* ══ 主区：绝对定位撑满，不用 vh/calc（APK 契约：左60 / 右40） ══ */
.main{position:absolute;top:56px;left:0;right:0;bottom:56px;display:flex;z-index:1;}
.left{width:60%;display:flex;flex-direction:column;padding:14px 0 14px 16px;}
/* 右区 padding 是 APK 视频窗口的锚：vTop=68(56+12) vLeft=w*.6+12 vw=40%-28 */
.right{width:40%;position:relative;display:flex;flex-direction:column;padding:12px 16px 12px 12px;}

/* ══ 叫号横幅：整屏焦点，橙金渐变 + 白字巨号 ══ */
.call-area{flex:0 0 178px;border-radius:12px;display:flex;align-items:center;padding:0 30px;
           margin-bottom:12px;position:relative;overflow:hidden;background:#c2410c;
           background:-webkit-linear-gradient(135deg,#fbbf24 0%,#f97316 46%,#ea580c 100%);
           background:linear-gradient(135deg,#fbbf24 0%,#f97316 46%,#ea580c 100%);
           box-shadow:0 6px 22px rgba(234,88,12,.32);}
.call-area:before{content:"";position:absolute;left:-40px;top:-120px;width:320px;height:320px;
                  border-radius:50%;background:rgba(255,255,255,.13);}
.call-area:after{content:"";position:absolute;right:-60px;bottom:-110px;width:280px;height:280px;
                 border-radius:50%;background:rgba(255,255,255,.08);}
.call-label{font-size:15px;color:#fff7d6;margin-right:24px;letter-spacing:4px;
            flex-shrink:0;font-weight:700;position:relative;z-index:2;
            text-shadow:0 1px 2px rgba(120,53,15,.35);}
.call-area .ticket{font-size:98px;font-weight:900;color:#fff;line-height:1;
                   flex-shrink:0;font-family:"Helvetica Neue",Arial,sans-serif;
                   letter-spacing:2px;position:relative;z-index:2;
                   text-shadow:0 3px 10px rgba(124,45,18,.4);
                   -webkit-animation:breathe 2.4s ease-in-out infinite;
                   animation:breathe 2.4s ease-in-out infinite;}
.call-area .no-call{font-size:74px;font-weight:900;color:rgba(255,255,255,.28);line-height:1;
                    flex-shrink:0;font-family:"Helvetica Neue",Arial,sans-serif;
                    letter-spacing:2px;position:relative;z-index:2;}
.call-info{font-size:27px;color:#fff7ed;font-weight:700;margin-left:26px;
           letter-spacing:1px;position:relative;z-index:2;
           text-shadow:0 1px 3px rgba(124,45,18,.35);}
@-webkit-keyframes breathe{0%,100%{-webkit-transform:scale(1);opacity:1;}
                           50%{-webkit-transform:scale(1.03);opacity:.94;}}
@keyframes breathe{0%,100%{transform:scale(1);opacity:1;}50%{transform:scale(1.03);opacity:.94;}}

/* ══ 队列：5 列必须 inline-block + 19.4%（4.4 的 flex:1 均分只渲染前 3 列） ══ */
.queue-area{flex:1;border-radius:12px;padding:12px;overflow:hidden;position:relative;
            background:rgba(19,31,58,.72);border:1px solid rgba(96,165,250,.16);
            box-shadow:0 4px 18px rgba(0,0,0,.22);}
.queue-header{display:block;margin-bottom:11px;font-size:0;}
.queue-header .col-label{display:inline-block;vertical-align:top;width:19.4%;margin-right:0.75%;
                         text-align:center;font-size:15px;font-weight:800;color:#fff;
                         padding:9px 0 10px;border-radius:9px;background:#1e2c4d;}
.queue-header .col-label:last-child{margin-right:0;width:19.4%;}
.col-wait{font-size:11px;color:rgba(255,255,255,.82);margin-top:4px;font-weight:400;}
.col-min{font-size:11px;color:rgba(255,255,255,.55);margin-top:2px;font-weight:400;}
.queue-header .col-A{background:#2563eb;box-shadow:0 3px 10px rgba(37,99,235,.35);}
.queue-header .col-B{background:#059669;box-shadow:0 3px 10px rgba(5,150,105,.35);}
.queue-header .col-C{background:#d97706;box-shadow:0 3px 10px rgba(217,119,6,.35);}
.queue-header .col-D{background:#7c3aed;box-shadow:0 3px 10px rgba(124,58,237,.35);}
.queue-header .col-E{background:#db2777;box-shadow:0 3px 10px rgba(219,39,119,.35);}
.queue-cols{display:block;font-size:0;overflow:hidden;}
.queue-col{display:inline-block;vertical-align:top;width:19.4%;margin-right:0.75%;
           height:100%;overflow:hidden;position:relative;font-size:0;}
/* 高度 100% 相对 queue-cols（JS 实测后写的 px）：列内容在列内就被裁掉，
   否则滚动动画的合成层会把溢出内容画过 queue-area 的边界、盖住底部字幕 */
.queue-col:last-child{margin-right:0;width:19.4%;}
.queue-col-inner{-webkit-animation:scrollUp 12s linear infinite;
                 animation:scrollUp 12s linear infinite;}
@-webkit-keyframes scrollUp{0%{-webkit-transform:translateY(0);}
                            100%{-webkit-transform:translateY(-50%);}}
@keyframes scrollUp{0%{transform:translateY(0);}100%{transform:translateY(-50%);}}
.ticket-item{font-size:26px;font-weight:700;padding:9px 4px;background:#18243f;
             border:1px solid rgba(96,165,250,.14);border-left:4px solid #334155;
             border-radius:9px;text-align:center;margin-bottom:9px;
             font-family:"Helvetica Neue",Arial,sans-serif;letter-spacing:1.5px;}
.ticket-item.ticket-A{color:#93c5fd;border-left-color:#3b82f6;}
.ticket-item.ticket-B{color:#6ee7b7;border-left-color:#10b981;}
.ticket-item.ticket-C{color:#fcd34d;border-left-color:#f59e0b;}
.ticket-item.ticket-D{color:#d8b4fe;border-left-color:#a855f7;}
.ticket-item.ticket-E{color:#f9a8d4;border-left-color:#ec4899;}

/* ══ 右区：广告框（高度由 flex 撑到 h-276，JS 再兜一层 px） ══ */
.ad-wrap{flex:1;background:#131f3a;border:1px solid rgba(96,165,250,.18);
         border-radius:12px;position:relative;overflow:hidden;
         display:flex;align-items:center;justify-content:center;
         box-shadow:0 4px 18px rgba(0,0,0,.22);}
.ad-wrap img{width:100%;height:100%;object-fit:contain;background:#131f3a;}
.ad-wrap video{max-width:100%;max-height:100%;object-fit:contain;}
.ad-tag{position:absolute;top:10px;right:10px;background:rgba(11,18,32,.72);color:#93c5fd;
        padding:4px 11px;border-radius:12px;font-size:11px;letter-spacing:1px;z-index:10;
        border:1px solid rgba(96,165,250,.2);}
.ad-empty{color:#475569;font-size:22px;letter-spacing:2px;}
/* ══ 底部行：高 128 是契约（广告框 444 = h-276 的前提），总高不能动 ══
   最近中奖这一排宽度缩到 288（484 - 间距12 - 二维码区184），给右下角二维码让位 */
.bottom-row{flex:0 0 128px;height:128px;margin-top:12px;}
.wins-area{width:288px;height:128px;background:#131f3a;border:1px solid rgba(251,191,36,.22);
           border-radius:12px;padding:8px 12px;overflow:hidden;position:relative;
           box-shadow:0 4px 18px rgba(0,0,0,.22);}
.wins-title{font-size:13px;color:#fbbf24;font-weight:800;margin-bottom:4px;letter-spacing:2px;}
.wins-viewport{overflow:hidden;height:88px;position:relative;}
.wins-track{-webkit-animation:winScroll 18s linear infinite;
            animation:winScroll 18s linear infinite;}

/* ══ 右下角二维码专属区（184×184，跨「底部行128 + 字幕56」两排高度） ══
   两排各自缩窄让出这块竖区：中奖排 484->288，字幕排右端收缩 204
   码 148（原来 124 / 50 都太小）；页面码走自家 /api/tv/{no}/qr.svg，不依赖外网
   放在 body 层（不在 .main 内）：.main 有 z-index:1 会形成层叠上下文，
   块放里面会被 z-index:60 的滚动字幕盖掉下半截 */
.qr-zone{position:absolute;right:16px;bottom:12px;width:184px;height:184px;z-index:70;
         background:#131f3a;border:1px solid rgba(96,165,250,.32);border-radius:12px;
         padding:4px 0;text-align:center;font-size:0;overflow:hidden;
         box-shadow:0 6px 22px rgba(0,0,0,.3);}
.qr-img{display:inline-block;vertical-align:top;width:148px;height:148px;background:#fff;
        padding:5px;-webkit-box-sizing:border-box;
        box-sizing:border-box;border-radius:8px;}
.qr-img svg{width:100%;height:100%;display:block;}
.qr-fallback{width:100%;height:100%;display:-webkit-box;-webkit-box-orient:horizontal;
             -webkit-box-pack:center;-webkit-box-align:center;
             color:#94a3b8;font-size:26px;font-weight:900;letter-spacing:2px;}
.qr-note{display:block;margin-top:2px;font-size:17px;color:#fbbf24;font-weight:900;
         letter-spacing:3px;line-height:1.2;}
.win-line{font-size:15px;color:#cbd5e1;white-space:nowrap;overflow:hidden;
          text-overflow:ellipsis;height:28px;line-height:28px;}
.win-line b{color:#fbbf24;font-weight:800;}
.win-line-empty{color:#475569;}
@-webkit-keyframes winScroll{0%{-webkit-transform:translateY(0);}
                             100%{-webkit-transform:translateY(-50%);}}
@keyframes winScroll{0%{transform:translateY(0);}100%{transform:translateY(-50%);}}

/* ══ 盲盒活动屏（activity 模式）：左区主视觉 + 大二维码 ══ */
.blindbox-hero{display:none;flex-direction:column;align-items:center;justify-content:center;
  height:100%;padding:16px 18px;position:relative;overflow:hidden;border-radius:12px;
  background:rgba(19,31,58,.72);border:1px solid rgba(251,191,36,.22);
  box-shadow:0 4px 18px rgba(0,0,0,.22);}
body.mode-call .blindbox-hero{display:none;}
body.mode-activity .blindbox-hero{display:flex;}
.bb-glow{position:absolute;border-radius:50%;z-index:0;pointer-events:none;}
.bb-glow-a{left:-90px;top:-110px;width:360px;height:360px;
  background:-webkit-radial-gradient(circle,rgba(251,191,36,.16),rgba(251,191,36,0) 68%);
  background:radial-gradient(circle,rgba(251,191,36,.16),rgba(251,191,36,0) 68%);}
.bb-glow-b{right:-70px;bottom:-90px;width:300px;height:300px;
  background:-webkit-radial-gradient(circle,rgba(56,189,248,.14),rgba(56,189,248,0) 68%);
  background:radial-gradient(circle,rgba(56,189,248,.14),rgba(56,189,248,0) 68%);}
.bb-title{position:relative;z-index:2;font-size:30px;font-weight:900;color:#fbbf24;
  letter-spacing:3px;text-shadow:0 0 22px rgba(251,191,36,.4);margin-bottom:6px;}
.bb-sub{position:relative;z-index:2;font-size:16px;color:#cbd5e1;letter-spacing:1px;
  margin-bottom:18px;text-align:center;line-height:1.6;}
.bb-qr{position:relative;z-index:2;width:300px;height:300px;background:#fff;border-radius:16px;
  padding:14px;box-sizing:border-box;box-shadow:0 10px 34px rgba(0,0,0,.4);}
.bb-qr svg{width:100%;height:100%;display:block;}
.bb-qr-fallback{width:100%;height:100%;display:-webkit-box;-webkit-box-orient:horizontal;
  -webkit-box-pack:center;-webkit-box-align:center;color:#94a3b8;font-size:22px;font-weight:900;letter-spacing:2px;}
.bb-note{position:relative;z-index:2;margin-top:14px;font-size:20px;font-weight:900;
  color:#fbbf24;letter-spacing:3px;}
/* activity 模式：叫号块隐藏、右下小码隐藏（hero 已承担二维码）、中奖排与字幕让位收回、等待数隐藏 */
body.mode-activity .call-block{display:none;}
body.mode-activity .qr-zone{display:none;}
body.mode-activity .wins-area{width:auto;}
body.mode-activity .ticker{right:0;}
body.mode-activity .wait-total{display:none;}

/* ══ 底部字幕 56px（APK 契约） ══
   右端收缩 204 = 二维码区184 + 右边距16 + 间隙4 —— 两排一起缩窄，让出右下角二维码区 */
.ticker{position:absolute;bottom:0;left:0;right:204px;height:56px;overflow:hidden;z-index:60;
        display:flex;align-items:center;justify-content:center;background:#101a33;
        background:-webkit-linear-gradient(180deg,#0e1729,#16223f);
        background:linear-gradient(180deg,#0e1729,#16223f);
        border-top:1px solid rgba(96,165,250,.2);}
.ticker-inner{white-space:nowrap;font-size:22px;font-weight:700;letter-spacing:1.5px;
              color:#7dd3fc;text-shadow:0 0 14px rgba(56,189,248,.35);}

/* ══ 中奖弹窗 ══ */
/* 旧版只有一套金色静态模板（fadeIn + pulse），四种奖品长得一模一样、无音效。
   新版：4 种奖品模式（免单/余额/折扣券/立减券）+ 全屏流光 + 波纹扩散 + 合成音效。
   样式与配色拆分见 _tv_win_fx.py —— 颜色不写死在 CSS，由 JS 按模式内联设置。
   注意：4.4 / Chromium 30 禁 CSS 变量，模式色只能用 style 逐项赋值。 */
__WIN_CSS__

/* ══ 声音提示层（默认不显示，行为与原版一致） ══ */
.audio-hint{display:none;position:absolute;top:0;left:0;right:0;bottom:0;
            background:rgba(6,10,20,.94);align-items:center;justify-content:center;
            z-index:99999;cursor:pointer;}
.ah-box{text-align:center;color:#f1f5f9;}
.ah-icon{width:62px;height:62px;border:2px solid #fbbf24;border-radius:50%;
         margin:0 auto 22px;position:relative;}
.ah-icon:before{content:"";position:absolute;left:50%;top:14px;width:2px;height:22px;
                margin-left:-1px;background:#fbbf24;}
.ah-icon:after{content:"";position:absolute;left:50%;bottom:13px;width:7px;height:7px;
               margin-left:-3px;border-radius:50%;background:#fbbf24;}
.ah-title{font-size:28px;font-weight:700;}
.ah-sub{font-size:16px;color:#94a3b8;margin-top:12px;}
</style>
</head>
'''

# ═════════════════════════════════════════════════════════════════════=
# 新 body（结构不变，只换标记与文案；emoji 全部去掉）
# ═════════════════════════════════════════════════════════════════════=
BODY = '''<body>

<div class="glow glow-a"></div>
<div class="glow glow-b"></div>

<div class="header">
  <div class="brand">
    <h1 id="merchantName">加载中...</h1>
    <span class="merchant-no" id="merchantNoLabel"></span>
  </div>
  <div class="right-group">
    <span class="wait-total" id="waitTotal">等待中 <b>--</b> 桌</span>
    <button class="tbtn" onclick="toggleMute()" id="muteBtn">声音 开</button>
    <button class="tbtn" onclick="enterFullscreen()">全屏</button>
    <div class="time" id="clock"></div>
  </div>
</div>

<div class="main" id="main">
  <!-- 左区：叫号屏（call-block）与盲盒活动屏（blindbox-hero）二选一 -->
  <div class="left">
    <!-- 叫号屏：当前叫号 + 5 列桌型队列（activity 模式由 .blindbox-hero 接管） -->
    <div class="call-block">
      <div class="call-area">
        <div class="call-label">当前叫号</div>
        <div id="callingTicket" class="no-call">--</div>
        <div class="call-info" id="callingInfo"></div>
      </div>
      <div class="queue-area" id="queueArea"></div>
    </div>
    <!-- 盲盒活动屏：左区主视觉 + 大二维码（扫码参与盲盒） -->
    <div class="blindbox-hero" id="bbHero">
      <div class="bb-glow bb-glow-a"></div>
      <div class="bb-glow bb-glow-b"></div>
      <div class="bb-title">扫码抽盲盒</div>
      <div class="bb-sub">到店扫码参与盲盒抽奖<br>中奖免单 / 打折 / 送余额</div>
      <div class="bb-qr" id="bbQr"><div class="bb-qr-fallback">盲盒</div></div>
      <div class="bb-note">扫码参与盲盒活动</div>
    </div>
  </div>
  <!-- 右区：广告（原生视频窗口叠加在此区域）+ 最近中奖；右下角二维码专属区由浮层/APK 原生层显示，页面不生成避免双码 -->
  <div class="right">
    <div class="ad-wrap" id="adArea">
      <div class="ad-tag">广告</div>
      <div class="ad-empty">暂无广告</div>
    </div>
    <div class="bottom-row">
      <div class="wins-area">
        <div class="wins-title">最近中奖</div>
        <div class="wins-viewport"><div class="wins-track" id="winsList"><div class="win-line win-line-empty">暂无中奖记录</div></div></div>
      </div>
    </div>
  </div>
</div>

<!-- 滚动字幕（右端让位给二维码区；activity 模式收回） -->
<div class="ticker">
  <div class="ticker-inner" id="tickerText">到店扫码参与盲盒抽奖，中奖免单 / 打折 / 送余额　·　欢迎光临，我们将竭诚为您服务</div>
</div>

<!-- 右下角二维码专属区：跨底部行与字幕两排高度；APK 由原生层显示，页面不生成避免双码 -->
<div class="qr-zone" id="qrFloat">
  <div class="qr-img" id="qrImg"><div class="qr-fallback">取号</div></div>
  <div class="qr-note">扫码取号</div>
</div>


<!-- 声音提示层（电视 APK 走原生播报，网页版自动初始化） -->
<div id="audioHint" class="audio-hint" onclick="enableAudio()">
  <div class="ah-box">
    <div class="ah-icon"></div>
    <div class="ah-title">声音未开启</div>
    <div class="ah-sub">点击屏幕任意位置开启叫号播报</div>
  </div>
</div>

<!-- 中奖全屏弹窗（tv-v2 动效版：波纹层 / 双流光层 / 卡片） -->
__WIN_HTML__
'''

# ═════════════════════════════════════════════════════════════════════=
# JS 定点替换：只改渲染出来的 HTML 片段和两处显示格式，逻辑一行不动
# ═════════════════════════════════════════════════════════════════════=
REPLACES = [
    # 1) 列头：内联样式 -> 语义 class（色只为线条，不做整块饱和底）
    (
        """      html += '<div class="col-label ' + m.colClass + '">' + m.label +
              '<div style="font-size:11px;color:rgba(255,255,255,0.75);margin-top:3px;">等待' + cnt + '桌</div>' +
              '<div style="font-size:10px;color:rgba(255,255,255,0.55);margin-top:2px;">约' + waitMin + '分钟</div></div>';""",
        """      html += '<div class="col-label ' + m.colClass + '">' + m.label +
              '<div class="col-wait">等待 ' + cnt + ' 桌</div>' +
              '<div class="col-min">约 ' + waitMin + ' 分</div></div>';"""
    ),
    # 2) 顶栏等待桌数：数字加粗突出
    (
        """    if(wt) wt.textContent = '等待中：' + waiting.length + ' 桌';""",
        """    if(wt) wt.innerHTML = '等待中 <b>' + waiting.length + '</b> 桌';"""
    ),
    # 3) 列高实测：列头改成两行标签后 46px 不再成立，改成按实际高度算，避免最后一行被切
    (
        """    document.getElementById('queueArea').innerHTML = html;""",
        """    document.getElementById('queueArea').innerHTML = html;
    fitQueueCols();"""
    ),
    # 4) 空队列/无中奖的占位色
    (
        """    box.innerHTML = '<div class="win-line" style="color:#475569;">暂无中奖记录</div>';""",
        """    box.innerHTML = '<div class="win-line win-line-empty">暂无中奖记录</div>';"""
    ),
    # 5) 时间：日期小字 + 时分大字 + 秒弱化
    (
        """  var el = document.getElementById('clock');
  if(el) el.textContent = d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes())+':'+pad(d.getSeconds());""",
        """  var el = document.getElementById('clock');
  var wk = '日一二三四五六'.charAt(d.getDay());
  if(el) el.innerHTML = '<em>' + (d.getMonth()+1) + '月' + d.getDate() + '日 周' + wk + '</em>'
                      + pad(d.getHours()) + ':' + pad(d.getMinutes())
                      + '<span>:' + pad(d.getSeconds()) + '</span>';"""
    ),
    # 6) 静音按钮文案（原来带 emoji，4.4 有豆腐块风险）
    (
        """  if(btn) btn.textContent = muted ? '🔇 声音关' : '🔊 声音开';""",
        """  if(btn) btn.textContent = muted ? '声音 关' : '声音 开';"""
    ),
    # 7) 广告框高度：相对 main 实测，不再用 window.innerHeight 推算
    (
        """function layoutTV(){
  var h = window.innerHeight || document.documentElement.clientHeight || 720;
  var mainEl = document.getElementById('main');
  var adEl = document.getElementById('adArea');
  if(mainEl) mainEl.style.height = (h - 112) + 'px';
  if(adEl) adEl.style.height = (h - 276) + 'px';
}""",
        """function layoutTV(){
  /* main 的高度交给 CSS 的 top:56/bottom:56 绝对定位 —— 不依赖视口测量。
     原实现拿 window.innerHeight 推算，但 WebView 有状态栏 / 没全屏时
     innerHeight < 屏高，会算出偏小的高度，屏幕底部平白留一条黑边。
     ad 框改成相对 main 实测高度来算，依赖方向才对。
     164 = 右区上下 padding 24 + wins 128 + margin 12 */
  var mainEl = document.getElementById('main');
  var adEl = document.getElementById('adArea');
  if(!mainEl || !adEl) return;
  var h = mainEl.clientHeight;
  if(h > 200) adEl.style.height = (h - 164) + 'px';
}"""
    ),
    # 8) 中奖弹屏：整段 showWin 换成"4 种模式 + 流光波纹动效 + Web Audio 合成音效"
    #    动效与音效逻辑放在 _tv_win_fx.py，这里只做替换锚点
    (
        """function showWin(win){
  var desc = document.getElementById('winDesc');
  if(desc) desc.textContent = win.prizeName || '恭喜您抽中奖品';
  var overlay = document.getElementById('winOverlay');
  overlay.classList.add('show');
  playTts('恭喜中奖，' + (win.prizeName || '大奖'));
  setTimeout(function(){ overlay.classList.remove('show'); }, 10000);
}""",
        WIN_JS.strip()
    ),
]

# 新增函数：实测列高（插在 layoutTV 之前）
FIT_FN = '''
/* 列高实测：列头是两行标签，高度随字号变，不能写死 46px；
   量出来再把 .queue-cols 撑起来，否则最后一行会被 queue-area 裁掉 */
function fitQueueCols(){
  var area = document.getElementById('queueArea');
  if(!area) return;
  var qh = area.querySelector('.queue-header');
  var qc = area.querySelector('.queue-cols');
  if(!qh || !qc) return;
  var h = area.clientHeight - 24 - qh.offsetHeight - 11;
  if(h > 60) qc.style.height = h + 'px';
}
'''


def main():
    src = open(SRC, encoding='utf-8').read()

    p_script = src.index('<script>')
    js = src[p_script:]

    # 中奖弹屏动效模块注入（CSS / HTML 占位符 → _tv_win_fx.py 的实际内容）
    head_out = HEAD.replace('__WIN_CSS__', WIN_CSS.strip())
    body_out = BODY.replace('__WIN_HTML__', WIN_HTML.strip())
    for token, blob in (('__WIN_CSS__', WIN_CSS), ('__WIN_HTML__', WIN_HTML)):
        if token in head_out or token in body_out:
            print('  [FAIL] 占位符没替换干净: %s' % token)
            sys.exit(1)

    # 定点替换
    for old, new in REPLACES:
        if old not in js:
            print('  [FAIL] 找不到替换锚点：%r' % old.splitlines()[0][:70])
            sys.exit(1)
        js = js.replace(old, new, 1)

    # 插入 fitQueueCols
    anchor = '/* ===== 广告框固定大小'
    if anchor not in js:
        print('  [FAIL] 找不到 layoutTV 注释锚点')
        sys.exit(1)
    js = js.replace(anchor, FIT_FN.strip() + '\n\n' + anchor, 1)

    # 页面二维码渲染与模式切换已移入主脚本（refresh 内调用 applyTvMode / renderTvQr），
    # 由服务端按 tv_mode 决定 qr.svg 编码地址，并避免首屏模式判断滞后，故此处不再单独追加。
    js += ''

    out = head_out + body_out + js
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'w', encoding='utf-8').write(out)

    print('生成: %s  (%.0f KB, %d 行)' % (
        os.path.relpath(OUT, BASE),
        len(out.encode('utf-8')) / 1024,
        len(out.splitlines())))

    # ===== 生成后自检 =====
    import _kiosk_audit
    fatal, warn, nodes = _kiosk_audit.audit(OUT)
    print()
    if fatal:
        print('!! 兼容性致命项 %d 处，产物不合格' % fatal)
        sys.exit(1)
    if nodes > 200:
        print('!! DOM 节点 %d 超预算' % nodes)
        sys.exit(1)
    print('产物合格：致命 0 / DOM %d' % nodes)


if __name__ == '__main__':
    main()
