// ===== 头和工具 =====
const http = require('http');
const url = require('url');
const PORT = 8766;

function rid(p) { return (p || '') + '_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 7); }
function ok(data, msg) { return { code: 0, msg: msg || 'ok', data: data }; }
function err(code, msg) { return { code: code, msg: msg }; }
function nowStr() { var d = new Date(); return d.toISOString().slice(0, 19).replace('T', ' '); }
const JSON_HDR = { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type,Authorization' };
function send(res, code, obj) { res.writeHead(code, JSON_HDR); res.end(JSON.stringify(obj)); }
function readBody(req) { return new Promise(function(resolve) { var b = ''; req.on('data', function(c) { b += c; }); req.on('end', function() { var ctype = (req.headers['content-type']||'').toLowerCase(); try { if (ctype.indexOf('application/x-www-form-urlencoded') > -1) { var params = {}; b.split('&').forEach(function(kv){ if(!kv) return; var p2 = kv.split('='); params[decodeURIComponent(p2[0])] = decodeURIComponent((p2[1]||'').replace(/\+/g,' ')); }); resolve(params); } else if (ctype.indexOf('application/json') > -1) { resolve(b ? JSON.parse(b) : {}); } else { resolve(b ? JSON.parse(b) : {}); } } catch (e) { resolve({ _raw: b }); } }); }); }
// ===== 内存数据 =====
const PRIZE_LIST = [
  { prizeId: 1, prizeType: 1, prizeValue: 9, weight: 25, enabled: 1, prizeName: '9 折优惠券', emoji: '🎟️' },
  { prizeId: 2, prizeType: 1, prizeValue: 15, weight: 15, enabled: 1, prizeName: '8.5 折优惠券', emoji: '🎟️' },
  { prizeId: 3, prizeType: 1, prizeValue: 20, weight: 8, enabled: 1, prizeName: '8 折优惠券', emoji: '🎟️' },
  { prizeId: 4, prizeType: 2, prizeValue: 5, weight: 20, enabled: 1, prizeName: '立减 5 元', emoji: '💵' },
  { prizeId: 5, prizeType: 2, prizeValue: 10, weight: 10, enabled: 1, prizeName: '立减 10 元', emoji: '💵' },
  { prizeId: 6, prizeType: 3, prizeValue: 3, weight: 12, enabled: 1, prizeName: '¥3 余额', emoji: '💰' },
  { prizeId: 7, prizeType: 3, prizeValue: 20, weight: 3, enabled: 1, prizeName: '¥20 余额（本店抵扣）', emoji: '💰', storeOnly: true },
  { prizeId: 8, prizeType: 4, prizeValue: 0, weight: 4, enabled: 1, prizeName: '必购免单券', emoji: '🛒' },
  { prizeId: 9, prizeType: 1, prizeValue: 50, weight: 1, enabled: 1, prizeName: '5 折优惠券', emoji: '🎟️' },
  { prizeId: 10, prizeType: 3, prizeValue: 0.5, weight: 1, enabled: 1, prizeName: '¥0.5 余额', emoji: '💰' },
  { prizeId: 11, prizeType: 3, prizeValue: 88, weight: 1, enabled: 1, prizeName: '¥88 余额（本店抵扣）', emoji: '💰', storeOnly: true },
  { prizeId: 12, prizeType: 3, prizeValue: 0, weight: 0, enabled: 0, prizeName: '谢谢参与', emoji: '🙏' }
];
const GOODS_LIST = [
  { goodsId: 'G001', name: '芒果礼盒 1kg', price: 38.00, emoji: '🥭', stock: 100 },
  { goodsId: 'G002', name: '泰国椰青 2 个', price: 28.00, emoji: '🥥', stock: 100 },
  { goodsId: 'G003', name: '阳光玫瑰葡萄 1 串', price: 68.00, emoji: '🍇', stock: 50 },
  { goodsId: 'G004', name: '猫山王榴莲 1 个', price: 198.00, emoji: '🍈', stock: 30 }
];
const db = {
  merchants: {
    M1001: {
      merchantNo: 'M1001', name: '鲜果时光·宜必购合作店', phone: '13800138001', token: 'TK_M1001_DEMO',
      qrImageData: null, qrName: '', deductConfig: { percent: 80, dailyCap: 100, mode: 'A' },
      prizes: PRIZE_LIST, orders: [], balanceFlow: [], coupons: [],
      announcements: [{ announceId: 1, content: '欢迎光临，开盒抽中可抵扣现金', createTime: '2026-08-20 10:00:00' }],
      memberStatus: { level: 'FREE', expireTime: null }
    }
  },
  customers: {}, verifyCodes: {}, goods: GOODS_LIST, ibigouOrders: [], globalDrawCount: 28
};

function auth(req, res) {
  var tk = (req.headers['authorization'] || '').replace(/^Bearer\s+/i, '');
  if (!tk) { res.writeHead(401, JSON_HDR); res.end(JSON.stringify(err(401, '未登录'))); return null; }
  if (tk.indexOf('TK_M1001_') === 0) return db.merchants.M1001;
  if (tk.indexOf('TK_C_') === 0) { var ph = tk.slice(5); return db.customers[ph] || (db.customers[ph] = { userPhone: ph, token: tk, balance: 0, coupons: [], messages: [], orders: [], lastDraw: null }); }
  res.writeHead(401, JSON_HDR); res.end(JSON.stringify(err(401, 'token 无效'))); return null;
}
// ===== 路由：健康 =====
async function handle(req, res) {
  var u = url.parse(req.url, true);
  var p = u.pathname;
  var m = req.method.toUpperCase();
  var body = (m === 'POST' || m === 'PUT') ? await readBody(req) : (u.query || {});
  if (m === 'OPTIONS') { res.writeHead(204, JSON_HDR); return res.end(); }
  try {
    if (p === '/health') return send(res, 200, ok({ uptime: process.uptime(), merchants: Object.keys(db.merchants).length, customers: Object.keys(db.customers).length, orders: db.merchants.M1001.orders.length }));
    // ===== 顾客端 =====

    // 验证码
    if (p === '/api/customer/auth/send-code' && m === 'POST') {
      var ph = String(body.userPhone || '').trim();
      if (!/^1\d{10}$/.test(ph)) return send(res, 400, err(400, '手机号格式不对'));
      db.verifyCodes[ph] = { code: '123456', expire: Date.now() + 5 * 60 * 1000 };
      console.log('[mock] send-code', ph, '123456');
      return send(res, 200, ok({ ttl: 300 }));
    }

    // 登录
    if (p === '/api/customer/auth/login' && m === 'POST') {
      var ph2 = String(body.userPhone || '').trim();
      var code = String(body.code || '').trim();
      if (!db.verifyCodes[ph2] || db.verifyCodes[ph2].code !== code) return send(res, 400, err(400, '验证码错误'));
      if (db.verifyCodes[ph2].expire < Date.now()) return send(res, 400, err(400, '验证码过期'));
      var tk = 'TK_C_' + ph2;
      if (!db.customers[ph2]) db.customers[ph2] = { userPhone: ph2, token: tk, balance: 0, coupons: [], messages: [], orders: [], lastDraw: null };
      db.customers[ph2].token = tk;
      delete db.verifyCodes[ph2];
      return send(res, 200, ok({ token: tk, userPhone: ph2 }));
    }

    // 钱包
    if (p.indexOf('/api/customer/wallet/') === 0 && m === 'GET') {
      var ph3 = decodeURIComponent(p.split('/').pop());
      var c3 = db.customers[ph3] || (db.customers[ph3] = { userPhone: ph3, balance: 0, coupons: [], messages: [], orders: [] });
      return send(res, 200, ok({ balance: c3.balance, flow: c3.balanceFlow || [] }));
    }
    // 试算
    if (p === '/api/customer/offline/calc' && m === 'POST') {
      var me = auth(req, res); if (!me) return;
      var ph = String(body.userPhone || '').trim();
      var amt = parseFloat(body.amount) || 0;
      var useBal = body.useBalance !== false;
      var couponIds = Array.isArray(body.couponIds) ? body.couponIds : (body.couponIds ? [body.couponIds] : []);
      var c = db.customers[ph] || (db.customers[ph] = { userPhone: ph, balance: 0, coupons: [], messages: [], orders: [] });
      var couponDisc = 0, couponName = '', couponId = '';
      for (var i = 0; i < couponIds.length; i++) {
        var id = couponIds[i];
        var cu = c.coupons.find(function(x) { return x.id === id && x.status === 'unused'; });
        if (!cu) continue;
        var d = 0;
        if (cu.type === 1) d = +(amt * (1 - cu.value / 100)).toFixed(2);
        else if (cu.type === 2) d = Math.min(cu.value, amt);
        if (d > couponDisc) { couponDisc = d; couponName = cu.name; couponId = cu.id; }
      }
      var afterCoupon = Math.max(0, +(amt - couponDisc).toFixed(2));
      var balanceDisc = useBal ? Math.min(c.balance || 0, afterCoupon) : 0;
      var final = Math.max(0, +(afterCoupon - balanceDisc).toFixed(2));
      return send(res, 200, ok({ amount: amt, couponDiscount: couponDisc, couponName: couponName, couponId: couponId, balanceDiscount: balanceDisc, finalAmount: final, balance: c.balance || 0 }));
    }

    // 下单
    if (p === '/api/customer/offline/order' && m === 'POST') {
      var me2 = auth(req, res); if (!me2) return;
      var ph4 = String(body.userPhone || '').trim();
      var amt2 = parseFloat(body.amount) || 0;
      var c4 = db.customers[ph4] || (db.customers[ph4] = { userPhone: ph4, balance: 0, coupons: [], messages: [], orders: [] });
      var useBal2 = body.useBalance !== false;
      var couponIds2 = Array.isArray(body.couponIds) ? body.couponIds : (body.couponIds ? [body.couponIds] : []);
      var couponDisc2 = 0, couponId2 = '', couponName2 = '';
      for (var j = 0; j < couponIds2.length; j++) {
        var id2 = couponIds2[j];
        var cu2 = c4.coupons.find(function(x) { return x.id === id2 && x.status === 'unused'; });
        if (!cu2) continue;
        var d2 = 0;
        if (cu2.type === 1) d2 = +(amt2 * (1 - cu2.value / 100)).toFixed(2);
        else if (cu2.type === 2) d2 = Math.min(cu2.value, amt2);
        if (d2 > couponDisc2) { couponDisc2 = d2; couponName2 = cu2.name; couponId2 = cu2.id; }
      }
      var afterCoupon2 = Math.max(0, +(amt2 - couponDisc2).toFixed(2));
      var balanceDisc2 = useBal2 ? Math.min(c4.balance || 0, afterCoupon2) : 0;
      var final2 = Math.max(0, +(afterCoupon2 - balanceDisc2).toFixed(2));
      if (balanceDisc2 > 0) { c4.balance = +(c4.balance - balanceDisc2).toFixed(2); c4.balanceFlow = c4.balanceFlow || []; c4.balanceFlow.unshift({ time: nowStr(), amount: -balanceDisc2, type: '扣减', desc: '盲盒下单抵扣' }); }
      if (couponId2) { var cu2b = c4.coupons.find(function(x) { return x.id === couponId2; }); if (cu2b) cu2b.status = 'used'; }
      var orderNo = 'O' + Date.now().toString(36).toUpperCase();
      var order = { orderNo: orderNo, userPhone: ph4, merchantNo: body.merchantNo || 'M1001', amount: amt2, couponDiscount: couponDisc2, balanceDiscount: balanceDisc2, finalAmount: final2, couponName: couponName2, useBalance: useBal2, status: 'pending', channel: body.channel || '1', qrCodeUniqueKey: rid('QR'), voucherToken: rid('VT'), createTime: nowStr() };
      c4.orders = c4.orders || []; c4.orders.unshift(order);
      db.merchants.M1001.orders.unshift(order);
      return send(res, 200, ok({ orderNo: orderNo, qrCodeUniqueKey: order.qrCodeUniqueKey, voucherToken: order.voucherToken, finalAmount: final2, amount: amt2, couponDiscount: couponDisc2, balanceDiscount: balanceDisc2, couponName: couponName2 }));
    }

    if (p === '/api/customer/offline/order-b' && m === 'POST') {
      return send(res, 200, ok({ orderNo: 'B' + Date.now().toString(36).toUpperCase(), qrCodeUniqueKey: rid('QR'), voucherToken: rid('VT'), finalAmount: parseFloat(body.amount) || 0 }));
    }
    // 抽盒
    if (p === '/api/customer/draw/normal' && m === 'POST') {
      var ph5 = String(body.userPhone || '').trim();
      var mno = body.merchantNo || 'M1001';
      var me3 = db.merchants[mno];
      if (!me3) return send(res, 400, err(400, '商家不存在'));
      var pool = me3.prizes.filter(function(x) { return x.enabled; });
      var total = pool.reduce(function(s, x) { return s + x.weight; }, 0);
      var r = Math.random() * total;
      var pick = pool[0];
      for (var k = 0; k < pool.length; k++) { if (r < pool[k].weight) { pick = pool[k]; break; } r -= pool[k].weight; }
      var draw = { prizeId: pick.prizeId, prizeName: pick.prizeName, prizeEmoji: pick.emoji, prizeType: pick.prizeType, prizeValue: pick.prizeValue, storeOnly: !!pick.storeOnly, drawBatchNo: rid('DB'), drawTime: nowStr() };
      var c5 = db.customers[ph5] || (db.customers[ph5] = { userPhone: ph5, balance: 0, coupons: [], messages: [], orders: [] });
      if (pick.prizeType === 3) { c5.balance = +((c5.balance || 0) + pick.prizeValue).toFixed(2); c5.balanceFlow = c5.balanceFlow || []; c5.balanceFlow.unshift({ time: nowStr(), amount: pick.prizeValue, type: '入账', desc: '盲盒抽中' }); }
      else if (pick.prizeType === 1) { var cp1 = { id: rid('CP'), type: 1, name: pick.prizeName, value: pick.prizeValue, status: 'unused', createTime: nowStr() }; c5.coupons = c5.coupons || []; c5.coupons.unshift(cp1); }
      else if (pick.prizeType === 2) { var cp2 = { id: rid('CP'), type: 2, name: '立减 ¥' + pick.prizeValue, value: pick.prizeValue, status: 'unused', createTime: nowStr() }; c5.coupons = c5.coupons || []; c5.coupons.unshift(cp2); }
      else if (pick.prizeType === 4) { var cp4 = { id: rid('CP'), type: 4, name: '必购免单券', value: 0, status: 'unused', createTime: nowStr() }; c5.coupons = c5.coupons || []; c5.coupons.unshift(cp4); }
      c5.lastDraw = draw;
      db.globalDrawCount += 1;
      return send(res, 200, ok({ prize: draw, batchNo: draw.drawBatchNo, storeDrawCount: db.globalDrawCount }));
    }

    // 必购商品
    if (p === '/api/customer/ibigou/goods' && m === 'GET') return send(res, 200, ok(db.goods));

    if (p.indexOf('/api/customer/ibigou/orders/') === 0 && m === 'GET') {
      var ph6 = decodeURIComponent(p.split('/').pop());
      var list = db.ibigouOrders.filter(function(x) { return x.userPhone === ph6; });
      return send(res, 200, ok(list));
    }

    if (p === '/api/customer/ibigou/order' && m === 'POST') {
      var ph7 = String(body.userPhone || '').trim();
      var gd = db.goods.find(function(x) { return x.goodsId === body.goodsId; });
      if (!gd) return send(res, 400, err(400, '商品不存在'));
      if (gd.stock <= 0) return send(res, 400, err(400, '已售罄'));
      gd.stock -= 1;
      var o = { orderNo: rid('IB'), userPhone: ph7, goodsId: gd.goodsId, name: gd.name, emoji: gd.emoji, price: gd.price, status: 'paid', createTime: nowStr() };
      db.ibigouOrders.unshift(o);
      return send(res, 200, ok(o));
    }

    var mm = p.match(/^\/api\/customer\/ibigou\/order\/[^/]+\/refund$/);
    if (mm && m === 'POST') {
      var orderNo3 = p.split('/')[5];
      var o3 = db.ibigouOrders.find(function(x) { return x.orderNo === orderNo3; });
      if (!o3) return send(res, 400, err(400, '订单不存在'));
      o3.status = 'refunded';
      return send(res, 200, ok(o3));
    }
    // 消息
    if (p.indexOf('/api/customer/messages/') === 0 && m === 'GET') {
      var parts = p.split('/');
      var ph8 = decodeURIComponent(parts[4] || '');
      var action = parts[5] || '';
      var c6 = db.customers[ph8] || (db.customers[ph8] = { userPhone: ph8, balance: 0, coupons: [], messages: [], orders: [] });
      if (action === 'unread-count') return send(res, 200, ok({ count: (c6.messages || []).filter(function(x) { return !x.is_read; }).length }));
      if (action === 'read') { (c6.messages || []).forEach(function(x) { x.is_read = 1; }); return send(res, 200, ok({ count: 0 })); }
      return send(res, 200, ok(c6.messages || []));
    }
    if (p === '/api/customer/messages/read-all' && m === 'POST') {
      var ph9 = String(body.userPhone || '').trim();
      var c7 = db.customers[ph9];
      if (c7) (c7.messages || []).forEach(function(x) { x.is_read = 1; });
      return send(res, 200, ok({}));
    }

    // 收款码 SVG / 动态码 / 身份码
    if (p === '/api/customer/qr-svg' && m === 'POST') {
      var content = String(body.content || '');
      return send(res, 200, ok({ qrUrl: 'mock://qr/' + rid('Q') + '?data=' + encodeURIComponent(content), content: content }));
    }
    if (p.indexOf('/api/customer/qr/dynamic/') === 0 && m === 'GET') {
      var ph10 = decodeURIComponent(p.split('/').pop());
      var c8 = db.customers[ph10] || { userPhone: ph10 };
      var tok = rid('DYN'); c8.dynamicQrToken = tok;
      return send(res, 200, ok({ token: tok, qrUrl: 'mock://qr/' + tok, expire: 600 }));
    }
    if (p.indexOf('/api/customer/identity-qr/') === 0 && m === 'GET') {
      var ph11 = decodeURIComponent(p.split('/').pop());
      var c9 = db.customers[ph11] || { userPhone: ph11 };
      var tok2 = rid('ID'); c9.identityQrToken = tok2;
      return send(res, 200, ok({ token: tok2, qrUrl: 'mock://qr/' + tok2, expire: 86400 * 30 }));
    }

    if (p.indexOf('/api/customer/ibigou/assets/') === 0 && m === 'GET') {
      var ph12 = decodeURIComponent(p.split('/').pop());
      var c10 = db.customers[ph12] || { userPhone: ph12, balance: 0, coupons: [], messages: [] };
      return send(res, 200, ok({ balance: c10.balance || 0, coupons: c10.coupons || [], goods: db.goods }));
    }
    // ===== 平台端 =====
    // 平台管理员登录
    if (p === '/api/admin/auth/login' && m === 'POST') {
      var admAcc = String(body.account || '').trim();
      var admPwd = String(body.password || '').trim();
      // mock 演示账号: admin / Admin@2026
      if (admAcc !== 'admin' || admPwd !== 'Admin@2026') return send(res, 401, err(401, '账号或密码错误（演示 admin / Admin@2026）'));
      var tkA = 'TK_ADMIN_' + Date.now().toString(36);
      db.admin = db.admin || {};
      db.admin.token = tkA;
      return send(res, 200, ok({ token: tkA, account: admAcc, role: 'admin' }));
    }
    // 平台管理员鉴权（X-Admin-Token）
    function authAdmin(req, res){
      var tka = (req.headers['x-admin-token'] || req.headers['X-Admin-Token'] || '').trim();
      if (!tka) { send(res, 401, err(401, '未登录')); return null; }
      return tka;
    }
    // mock 商家列表给平台
    if (p === '/api/admin/merchant/list' && m === 'GET') {
      if (!authAdmin(req, res)) return;
      var list = Object.keys(db.merchants).map(function(no){
        var me = db.merchants[no];
        return { merchantNo: no, merchantName: me.name || no, loginAccount: 'm001', status: 1, memberExpireTime: '2026-12-31T00:00:00' };
      });
      return send(res, 200, ok(list));
    }
    // mock 全局配置
    if (p === '/api/admin/config' && m === 'GET') {
      if (!authAdmin(req, res)) return;
      return send(res, 200, ok({ platformName: '宜必购', version: '1.0.0', supportPhone: '400-000-0000' }));
    }
    if (p === '/api/admin/config' && m === 'POST') {
      if (!authAdmin(req, res)) return;
      return send(res, 200, ok({ ok: true }));
    }
    // mock 审计日志
    if (p === '/api/admin/audit/list' && m === 'GET') {
      if (!authAdmin(req, res)) return;
      return send(res, 200, ok([{ id: 1, account: 'admin', action: 'login', time: new Date().toISOString() }]));
    }

    // ===== 商家端 =====

    // 商家登录（支持两种方式：验证码 888888 或 账号密码 m001/smoke123）
    if (p === '/api/merchant/auth/login' && m === 'POST') {
      var tkM = '';
      if (body.code && String(body.code).trim() === '888888') {
        // 验证码登录
        tkM = 'TK_M1001_' + Date.now().toString(36);
      } else if (body.account && body.password) {
        // 账号密码登录（演示）
        var acc = String(body.account).trim();
        var pwd = String(body.password).trim();
        if (acc !== 'm001' || pwd !== 'smoke123') return send(res, 401, err(401, '账号或密码错误（演示 m001 / smoke123）'));
        tkM = 'TK_M1001_' + Date.now().toString(36);
      } else {
        return send(res, 400, err(400, '请提供 code=888888 或 {account,password}'));
      }
      db.merchants.M1001.token = tkM;
      return send(res, 200, ok({ token: tkM, merchantNo: 'M1001', name: db.merchants.M1001.name }));
    }

    // 商家 config
    if (p === '/api/merchant/config' && m === 'GET') return send(res, 200, ok({ qrImageData: db.merchants.M1001.qrImageData, qrName: db.merchants.M1001.qrName, deductConfig: db.merchants.M1001.deductConfig, prizes: db.merchants.M1001.prizes, shop: db.merchants.M1001 }));
    if (p === '/api/merchant/config' && m === 'POST') {
      var me4 = auth(req, res); if (!me4) return;
      if (body.qrImageData !== undefined) { db.merchants.M1001.qrImageData = body.qrImageData; db.merchants.M1001.qrName = body.qrName || ''; }
      if (body.deductConfig) db.merchants.M1001.deductConfig = body.deductConfig;
      if (body.prizes) db.merchants.M1001.prizes = body.prizes;
      return send(res, 200, ok({ ok: true }));
    }

    if (p === '/api/merchant/qr/upload' && m === 'POST') {
      var me5 = auth(req, res); if (!me5) return;
      db.merchants.M1001.qrImageData = body.qrImageData;
      db.merchants.M1001.qrName = body.qrName || '';
      return send(res, 200, ok({ qrName: db.merchants.M1001.qrName, size: (body.qrImageData || '').length }));
    }
    if (p === '/api/merchant/deduct-config/save' && m === 'POST') {
      var me6 = auth(req, res); if (!me6) return;
      db.merchants.M1001.deductConfig = { percent: body.percent, dailyCap: body.dailyCap, mode: body.mode };
      return send(res, 200, ok({ ok: true }));
    }
    if (p === '/api/merchant/prize-pool/save' && m === 'POST') {
      var me7 = auth(req, res); if (!me7) return;
      if (Array.isArray(body.prizes)) db.merchants.M1001.prizes = body.prizes;
      return send(res, 200, ok({ ok: true }));
    }
    // 订单
    if (p === '/api/merchant/orders' && m === 'GET') {
      var me8 = auth(req, res); if (!me8) return;
      var list2 = db.merchants.M1001.orders;
      if (u.query.status) list2 = list2.filter(function(x) { return (x.status || '') === u.query.status; });
      if (u.query.channel) list2 = list2.filter(function(x) { return (x.channel || '') === u.query.channel; });
      if (u.query.q) { var q = String(u.query.q).toLowerCase(); list2 = list2.filter(function(x) { return (x.orderNo || '').toLowerCase().indexOf(q) >= 0 || (x.userPhone || '').indexOf(q) >= 0; }); }
      return send(res, 200, ok(list2));
    }
    if (p === '/api/merchant/orders/export' && m === 'POST') {
      var me9 = auth(req, res); if (!me9) return;
      var rows = [['orderNo','userPhone','amount','couponDiscount','balanceDiscount','finalAmount','status','createTime']];
      db.merchants.M1001.orders.forEach(function(o) { rows.push([o.orderNo, o.userPhone, o.amount, o.couponDiscount || 0, o.balanceDiscount || 0, o.finalAmount, o.status, o.createTime]); });
      var csv = rows.map(function(r){ return r.map(function(v){ return String.fromCharCode(34) + String(v).replace(/"/g, String.fromCharCode(34)+String.fromCharCode(34)) + String.fromCharCode(34); }).join(','); }).join('\r\n');

res.writeHead(200,
{
'Content-Type':
'text/csv; charset=utf-8', 'Content-Disposition': 'attachment; filename=orders.csv', 'Access-Control-Allow-Origin': '*' });

return res.end('\uFEFF' + csv);

}


var mo = p.match(/^\/api\/merchant\/order\/([^/]+)\/(confirm|refund|trade-no)$/);

if (mo && m === 'POST') {

var
me10
=
auth(req,
res);
if
(!me10)
return;

var
orderNo4
=
mo[1];
var
action
=
mo[2];

var
o4
=
db.merchants.M1001.orders.find(function(x)
{
return
x.orderNo
===
orderNo4;
});

if
(!o4)
return
send(res,
400,
err(400,
'订单不存在'));

if
(action
===
'confirm')
{
o4.status
=
'paid';
o4.confirmTime
=
nowStr();
}

else
if
(action
===
'refund')
{
o4.status
=
'refunded';
o4.refundTime
=
nowStr();
}

else
if
(action
===
'trade-no')
{
o4.tradeNo
=
body.tradeNo;
}

return
send(res,
200,
ok(o4));

}


if
(p.indexOf('/api/merchant/order/voucher')
===
0
&&
m
===
'GET')
{

var
tkV
=
u.query.voucherToken;

var
o5
=
db.merchants.M1001.orders.find(function(x)
{
return
x.voucherToken
===
tkV;
});

if
(!o5)
return
send(res,
400,
err(400,
'凭证无效'));

return
send(res,
200,
ok(o5));

}
    // 核销券 / 扣减余额
    if (p === '/api/merchant/coupon/verify-manual' && m === 'POST') {
      var me11 = auth(req, res); if (!me11) return;
      var id3 = body.couponId;
      var found = null, owner = '';
      Object.keys(db.customers).forEach(function(ph) {
        var cu3 = db.customers[ph].coupons.find(function(x) { return x.id === id3; });
        if (cu3) { found = cu3; owner = ph; }
      });
      if (!found) return send(res, 400, err(400, '券不存在'));
      if (found.status === 'used') return send(res, 400, err(400, '券已使用'));
      found.status = 'used'; found.usedTime = nowStr();
      db.merchants.M1001.balanceFlow = db.merchants.M1001.balanceFlow || [];
      db.merchants.M1001.balanceFlow.unshift({ time: nowStr(), amount: found.value, type: '核销', desc: '手动核销券 ' + found.name, userPhone: owner });
      return send(res, 200, ok({ ok: true, coupon: found, userPhone: owner }));
    }

    if (p === '/api/merchant/balance/manual-deduct' && m === 'POST') {
      var me12 = auth(req, res); if (!me12) return;
      var ph13 = String(body.userPhone || '').trim();
      var amt3 = parseFloat(body.orderAmount) || 0;
      var c11 = db.customers[ph13];
      if (!c11) return send(res, 400, err(400, '顾客不存在'));
      var before = c11.balance || 0;
      var after = Math.max(0, +(before - amt3).toFixed(2));
      var real = +(before - after).toFixed(2);
      c11.balance = after; c11.balanceFlow = c11.balanceFlow || [];
      c11.balanceFlow.unshift({ time: nowStr(), amount: -real, type: '扣减', desc: '商家手动扣减' });
      db.merchants.M1001.balanceFlow = db.merchants.M1001.balanceFlow || [];
      db.merchants.M1001.balanceFlow.unshift({ time: nowStr(), amount: -real, type: '扣减', desc: '手动核销 ' + ph13 + ' 余额', userPhone: ph13 });
      return send(res, 200, ok({ before: before, after: after, deducted: real }));
    }

    // 扫码 / 身份码
    if (p.indexOf('/api/merchant/qr/verify') === 0 && m === 'GET') {
      var me13 = auth(req, res); if (!me13) return;
      var tkQ = u.query.qrToken;
      var orderQ = db.merchants.M1001.orders.find(function(x) { return x.qrCodeUniqueKey === tkQ; });
      if (orderQ) return send(res, 200, ok({ type: 'order', order: orderQ }));
      var cust = Object.values(db.customers).find(function(x) { return x.dynamicQrToken === tkQ || x.identityQrToken === tkQ; });
      if (cust) return send(res, 200, ok({ type: 'customer', userPhone: cust.userPhone, balance: cust.balance || 0 }));
      return send(res, 400, err(400, '二维码无效'));
    }
    if (p.indexOf('/api/merchant/identity/verify') === 0 && m === 'GET') {
      var me14 = auth(req, res); if (!me14) return;
      var content2 = u.query.content || '';
      var cust2 = Object.values(db.customers).find(function(x) { return x.identityQrToken === content2; });
      if (cust2) return send(res, 200, ok({ type: 'customer', userPhone: cust2.userPhone, balance: cust2.balance || 0 }));
      return send(res, 400, err(400, '身份码无效'));
    }
    // 公告
    if (p === '/api/merchant/announce/list' && m === 'GET') return send(res, 200, ok(db.merchants.M1001.announcements));
    if (p === '/api/merchant/announce/publish' && m === 'POST') {
      var me15 = auth(req, res); if (!me15) return;
      var ann = { announceId: (db.merchants.M1001.announcements[0] ? db.merchants.M1001.announcements[0].announceId : 0) + 1, content: String(body.content || '').slice(0, 500), createTime: nowStr() };
      db.merchants.M1001.announcements.unshift(ann);
      return send(res, 200, ok(ann));
    }
    if (p.indexOf('/api/merchant/announce/poll') === 0 && m === 'GET') {
      var me16 = auth(req, res); if (!me16) return;
      var afterId = parseInt(u.query.afterId) || 0;
      var listA = db.merchants.M1001.announcements.filter(function(x) { return x.announceId > afterId; });
      return send(res, 200, ok(listA));
    }

    // 消息
    if (p === '/api/merchant/messages' && m === 'GET') {
      var me17 = auth(req, res); if (!me17) return;
      var msgs = [];
      Object.keys(db.customers).forEach(function(ph) {
        var c12 = db.customers[ph];
        (c12.messages || []).forEach(function(m3) { if (!m3.is_read) msgs.push(Object.assign({}, m3, { userPhone: ph })); });
      });
      return send(res, 200, ok(msgs));
    }
    if (p === '/api/merchant/messages/unread-count' && m === 'GET') {
      var n = 0;
      Object.keys(db.customers).forEach(function(ph) { (db.customers[ph].messages || []).forEach(function(m4) { if (!m4.is_read) n++; }); });
      return send(res, 200, ok({ count: n }));
    }
    if (p === '/api/merchant/messages/read-all' && m === 'POST') {
      Object.keys(db.customers).forEach(function(ph) { (db.customers[ph].messages || []).forEach(function(m5) { m5.is_read = 1; }); });
      return send(res, 200, ok({}));
    }

    // 会员
    if (p === '/api/merchant/member/status' && m === 'GET') return send(res, 200, ok(db.merchants.M1001.memberStatus));
    if (p === '/api/merchant/member/plan' && m === 'GET') return send(res, 200, ok([{ planId: 'P-FREE', name: '免费版', price: 0 }, { planId: 'P-PRO', name: '专业版', price: 99 }, { planId: 'P-FLAGSHIP', name: '旗舰版', price: 299 }]));
    if (p === '/api/merchant/member/orders' && m === 'GET') return send(res, 200, ok([]));
    var mr = p.match(/^\/api\/merchant\/member\/renew-order($|\/)/);
    if (mr) return send(res, 200, ok({ orderNo: 'RENEW_' + Date.now().toString(36) }));

    // fallback
    return send(res, 404, err(404, 'mock: ' + m + ' ' + p + ' not implemented'));
  } catch (e) {
    console.error('[mock] error', e);
    return send(res, 500, err(500, e.message));
  }
}

http.createServer(handle).listen(PORT, function() {
  console.log('[mock] api-mock-server listening on http://127.0.0.1:' + PORT);
  console.log('[mock] 健康检查: http://127.0.0.1:' + PORT + '/health');
});