/* login.js — 走真后端 */
(function(){
  var api = window.IBIGOU_API;
  if (!api) { document.body.innerHTML = '<p style="padding:20px;color:#dc2626">api.js 未加载，请检查</p>'; return; }
  if (localStorage.getItem('ibigou_user_token')) { location.replace('choose.html'); return; }
  var phone = $('phone'), codeInp = $('code'), btnSend = $('btnSendCode'), btnLogin = $('btnLogin'), codeArea = $('codeArea');
  var sending = false, logging = false, counting = 0;
  function validPhone(v){ return /^1[3-9]\d{9}$/.test(v); }
  btnSend.addEventListener('click', async function(){
    var v = (phone.value || '').trim();
    if (!validPhone(v)) { toast('请输入正确的 11 位手机号', 'warning'); return; }
    if (sending) return; sending = true;
    btnSend.disabled = true; btnSend.textContent = '发送中...';
    var r = await api.customer.sendCode(v);
    sending = false;
    if (r && r.code === 0) {
      codeArea.style.display = 'block'; btnLogin.style.display = 'block';
      toast('验证码已发送（测试：123456）', 'success');
      counting = 60;
      btnSend.textContent = '重新发送 (' + counting + 's)';
      var t = setInterval(function(){
        counting--;
        if (counting <= 0) { clearInterval(t); btnSend.disabled = false; btnSend.textContent = '重新获取验证码'; }
        else { btnSend.textContent = '重新发送 (' + counting + 's)'; }
      }, 1000);
      codeInp.focus();
    } else {
      btnSend.disabled = false; btnSend.textContent = '重新获取验证码';
      toast((r && r.msg) || '发送失败', 'danger');
    }
  });
  btnLogin.addEventListener('click', async function(){
    var p2 = (phone.value || '').trim();
    var c = (codeInp.value || '').trim();
    if (!validPhone(p2)) { toast('请输入正确的手机号', 'warning'); phone.focus(); return; }
    if (!c) { toast('请输入验证码', 'warning'); codeInp.focus(); return; }
    if (logging) return; logging = true;
    btnLogin.disabled = true; btnLogin.textContent = '登录中...';
    var r = await api.customer.login(p2, c);
    if (r && r.code === 0 && r.data && r.data.token) {
      try { localStorage.setItem('ibigou_user_token', r.data.token); localStorage.setItem('ibigou_phone', p2); } catch(e){}
      toast('登录成功', 'success');
      setTimeout(function(){ location.href = 'choose.html'; }, 400);
    } else {
      logging = false; btnLogin.disabled = false; btnLogin.textContent = '登录';
      toast((r && r.msg) || '登录失败', 'danger');
    }
  });
  codeInp && codeInp.addEventListener('keyup', function(e){ if (e.key === 'Enter') btnLogin.click(); });
})();
