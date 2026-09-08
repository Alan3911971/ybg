/* ============================================================
 * 宜必购盲盒 V2 — 共享 API 客户端（fetch 封装）
 * - 自动 Base：默认 http://127.0.0.1:8766
 * - token 走 localStorage.ibigou_user_token / ibigou_merchant_token
 * - 失败回退：网络断开时返回 code=599 不阻塞前端
 * ============================================================ */
(function(){
  var BASE = (window.IBIGOU_API_BASE) || 'http://127.0.0.1:8766';

  function lsGet(k, d){ try { var v=localStorage.getItem(k); if (v==null) return d; try { return JSON.parse(v); } catch(e){ return v; } } catch(e){ return d; } }
  function lsSet(k, v){ try { localStorage.setItem(k, JSON.stringify(v)); } catch(e){} }
  function lsDel(k){ try { localStorage.removeItem(k); } catch(e){} }

  function token(){ return lsGet('ibigou_user_token', null) || lsGet('ibigou_merchant_token', null); }

  function fullUrl(path){
    if (/^https?:/i.test(path)) return path;
    return BASE + (path.indexOf('/')===0?path:'/'+path);
  }

  async function request(method, path, body, opts){
    opts = opts || {};
    var url = fullUrl(path);
    var headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
    var tk = token();
    if (tk) headers['Authorization'] = 'Bearer ' + tk;
    var init = { method: method, headers: headers };
    if (body !== undefined && body !== null) {
      if (typeof body === 'string') init.body = body;
      else init.body = JSON.stringify(body);
    }
    try {
      var resp = await fetch(url, init);
      var ct = resp.headers.get('content-type') || '';
      var data;
      if (ct.indexOf('application/json') >= 0) data = await resp.json();
      else data = await { _raw: await resp.text() };
      return data;
    } catch (e) {
      console.warn('[api] net error', method, path, e.message);
      return { code: 599, msg: '网络错误：' + e.message + '（请确认后端 8766 在跑）' };
    }
  }

  var api = {
    base: BASE,
    setBase: function(b){ BASE = b; },

    get:  function(path, opts){ return request('GET',  path, null, opts); },
    post: function(path, body, opts){ return request('POST', path, body||{}, opts); },
    put:  function(path, body, opts){ return request('PUT',  path, body||{}, opts); },
    del:  function(path, opts){ return request('DELETE', path, null, opts); },

    // 顾客
    customer: {
      sendCode: function(phone){ return request('POST', '/api/customer/auth/send-code', { userPhone: phone }); },
      login:    function(phone, code){ return request('POST', '/api/customer/auth/login', { userPhone: phone, code: code }); },
      wallet:   function(phone){ return request('GET', '/api/customer/wallet/' + encodeURIComponent(phone)); },
      drawNormal: function(phone, mno){ return request('POST', '/api/customer/draw/normal', { userPhone: phone, merchantNo: mno||'M1001' }); },
      offlineCalc: function(body){ return request('POST', '/api/customer/offline/calc', body); },
      offlineOrder: function(body){ return request('POST', '/api/customer/offline/order', body); },
      ibigouGoods:  function(){ return request('GET', '/api/customer/ibigou/goods'); },
      ibigouOrder:  function(body){ return request('POST', '/api/customer/ibigou/order', body); },
      ibigouOrders: function(phone){ return request('GET', '/api/customer/ibigou/orders/' + encodeURIComponent(phone)); },
      ibigouAssets: function(phone){ return request('GET', '/api/customer/ibigou/assets/' + encodeURIComponent(phone)); },
      messages: function(phone){ return request('GET', '/api/customer/messages/' + encodeURIComponent(phone)); },
      messagesUnread: function(phone){ return request('GET', '/api/customer/messages/' + encodeURIComponent(phone) + '/unread-count'); },
      messagesRead: function(phone){ return request('GET', '/api/customer/messages/' + encodeURIComponent(phone) + '/read'); },
      messagesReadAll: function(phone){ return request('POST', '/api/customer/messages/read-all', { userPhone: phone }); },
      qrSvg: function(content){ return request('POST', '/api/customer/qr-svg', { content: content }); },
      qrDynamic: function(phone){ return request('GET', '/api/customer/qr/dynamic/' + encodeURIComponent(phone)); },
      identityQr: function(phone){ return request('GET', '/api/customer/identity-qr/' + encodeURIComponent(phone)); },
    },

    // 商家
    merchant: {
      login: function(body){ return request('POST', '/api/merchant/auth/login', typeof body === "string" ? { code: body } : body); },
      config: function(){ return request('GET', '/api/merchant/config'); },
      saveConfig: function(body){ return request('POST', '/api/merchant/config', body); },
      qrUpload: function(qrImageData, qrName){ return request('POST', '/api/merchant/qr/upload', { qrImageData: qrImageData, qrName: qrName||'' }); },
      deductConfigSave: function(p, c, m){ return request('POST', '/api/merchant/deduct-config/save', { percent: p, dailyCap: c, mode: m }); },
      prizePoolSave: function(prizes){ return request('POST', '/api/merchant/prize-pool/save', { prizes: prizes }); },
      orders: function(qs){ return request('GET', '/api/merchant/orders' + (qs||'')); },
      ordersExport: function(qs){ return request('POST', '/api/merchant/orders/export' + (qs||''), {}); },
      orderConfirm: function(orderNo){ return request('POST', '/api/merchant/order/' + orderNo + '/confirm', {}); },
      orderRefund: function(orderNo){ return request('POST', '/api/merchant/order/' + orderNo + '/refund', {}); },
      orderTradeNo: function(orderNo, tradeNo){ return request('POST', '/api/merchant/order/' + orderNo + '/trade-no', { tradeNo: tradeNo }); },
      orderVoucher: function(tk){ return request('GET', '/api/merchant/order/voucher?voucherToken=' + encodeURIComponent(tk)); },
      couponVerifyManual: function(couponId){ return request('POST', '/api/merchant/coupon/verify-manual', { couponId: couponId }); },
      balanceManualDeduct: function(phone, amt){ return request('POST', '/api/merchant/balance/manual-deduct', { userPhone: phone, orderAmount: amt }); },
      qrVerify: function(tk){ return request('GET', '/api/merchant/qr/verify?qrToken=' + encodeURIComponent(tk)); },
      identityVerify: function(content){ return request('GET', '/api/merchant/identity/verify?content=' + encodeURIComponent(content)); },
      announceList: function(){ return request('GET', '/api/merchant/announce/list'); },
      announcePublish: function(content){ return request('POST', '/api/merchant/announce/publish', { content: content }); },
      announcePoll: function(afterId){ return request('GET', '/api/merchant/announce/poll?afterId=' + (afterId||0)); },
      messages: function(){ return request('GET', '/api/merchant/messages'); },
      messagesUnread: function(){ return request('GET', '/api/merchant/messages/unread-count'); },
      messagesReadAll: function(){ return request('POST', '/api/merchant/messages/read-all', {}); },
      memberStatus: function(){ return request('GET', '/api/merchant/member/status'); },
      memberPlan: function(){ return request('GET', '/api/merchant/member/plan'); },
    },

    lsGet: lsGet, lsSet: lsSet, lsDel: lsDel,
  };

  window.IBIGOU_API = api;
  window.api = window.api || api;
})();
