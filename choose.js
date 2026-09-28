/* choose.js - 选择参与方式 */
(function(){
  var tk = null;
  try { tk = localStorage.getItem("ibigou_user_token"); } catch(e){}
  if (!tk) { location.replace("login.html"); return; }
  var ph = null;
  try { ph = localStorage.getItem("ibigou_phone"); } catch(e){}
  if (ph) $("userPhone").textContent = ph.replace(/^(\d{3})\d{4}(\d{4})$/, "$1****$2");
  $("btnLogout").addEventListener("click", function(e){
    e.preventDefault();
    try { localStorage.removeItem("ibigou_user_token"); } catch(_){}
    location.href = "login.html";
  });
  var picked = null;
  var cards = document.querySelectorAll(".ch-card");
  cards.forEach(function(el){
    el.addEventListener("click", function(){
      cards.forEach(function(x){ x.classList.remove("selected"); });
      el.classList.add("selected");
      picked = el.getAttribute("data-ch");
      $("btnDraw").disabled = false;
    });
  });
  $("btnDraw").addEventListener("click", function(){
    if (!picked) { toast("请先选择参与方式", "warning"); return; }
    try { localStorage.setItem("ibigou_channel", picked); } catch(_){}
    location.href = "customer/draw.html?channel=" + encodeURIComponent(picked);
  });
})();
