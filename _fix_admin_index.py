# -*- coding: utf-8 -*-
# 整行替换 admin/index.html 三处语法错误行（JS 字符串双引号嵌套）
import io

p = r'D:\workspace-java\ybg-master\admin\index.html'
s = io.open(p, encoding='utf-8').read()

# ---- line 310 loadRecentAlerts（old 用 repr 原文，new 修复为单引号定界） ----
old310 = 'async function loadRecentAlerts(){try{const r=await get(A+"/alerts");const arr=(r.data||[]).slice(0,5);const el=$("kpiAlertsList");if(!arr.length){el.textContent="暂无告警";return;}el.className="";el.innerHTML=arr.map(function(a){return "<div style="padding:8px 0;border-bottom:1px solid var(--c-divider);display:flex;justify-content:space-between;font-size:13px;"><span>"+esc(a.title||a.content||"\u2014")+"</span><span class="pill "+(a.handled?"ok":"warn")+"">"+(a.handled?"\u5df2\u5904\u7406":"\u5f85\u5904\u7406")+"</span></div>";}).join("");}catch(e){}}'
new310 = 'async function loadRecentAlerts(){try{const r=await get(A+"/alerts");const arr=(r.data||[]).slice(0,5);const el=$("kpiAlertsList");if(!arr.length){el.textContent="\u6682\u65e0\u544a\u8b66";return;}el.className="";el.innerHTML=arr.map(function(a){return \'<div style="padding:8px 0;border-bottom:1px solid var(--c-divider);display:flex;justify-content:space-between;font-size:13px;"><span>\'+esc(a.title||a.content||"\u2014")+\'</span><span class="pill \'+(a.handled?"ok":"warn")+\'">\'+(a.handled?"\u5df2\u5904\u7406":"\u5f85\u5904\u7406")+\'</span></div>\';}).join("");}catch(e){}}'
assert old310 in s, 'line310 old not found'
s = s.replace(old310, new310)

# ---- line 314 filterMerchants ----
old314 = 'async function filterMerchants(){const q=($("mSearch")&&$("mSearch").value||"").toLowerCase();const st=$("mFilter")&&$("mFilter").value||"";const el=$("mTable");if(!_merchantsCache.length){el.innerHTML="<div class="empty">\u52a0\u8f7d\u4e2d...</div>";return;}let rows=_merchantsCache;if(q)rows=rows.filter(function(m){return(m.merchantNo||"").toLowerCase().indexOf(q)>=0||(m.merchantName||"").toLowerCase().indexOf(q)>=0;});if(st!=="")rows=rows.filter(function(m){return String(m.enabled)===st;});if(!rows.length){el.innerHTML="<div class="empty">\u65e0\u5339\u914d</div>";return;}el.innerHTML="<div class="table-wrap"><table><thead><tr><th>\u7f16\u53f7</th><th>\u540d\u79f0</th><th>\u8d26\u53f7</th><th>\u72b6\u6001</th><th>\u64cd\u4f5c</th></tr></thead><tbody>"+rows.map(function(m){return"<tr><td>"+esc(m.merchantNo)+"</td><td>"+esc(m.merchantName||"")+"</td><td>"+esc(m.loginAccount||"")+"</td><td><span class="pill "+(m.enabled?"ok":"no")+"">"+(m.enabled?"\u542f\u7528":"\u7981\u7528")+"</span></td><td><button class="btn ghost" onclick="toggleM(\\x27"+esc(m.merchantNo)+"\\x27,"+(m.enabled?0:1)+")">"+(m.enabled?"\u7981\u7528":"\u542f\u7528")+"</button> <button class="btn ghost" onclick="resetPwd(\\x27"+esc(m.merchantNo)+"\\x27)">\u91cd\u7f6e\u5bc6\u7801</button></td></tr>";}).join("")+"</tbody></table></div>";}'
new314 = 'async function filterMerchants(){const q=($("mSearch")&&$("mSearch").value||"").toLowerCase();const st=$("mFilter")&&$("mFilter").value||"";const el=$("mTable");if(!_merchantsCache.length){el.innerHTML=\'<div class="empty">\u52a0\u8f7d\u4e2d...</div>\';return;}let rows=_merchantsCache;if(q)rows=rows.filter(function(m){return(m.merchantNo||"").toLowerCase().indexOf(q)>=0||(m.merchantName||"").toLowerCase().indexOf(q)>=0;});if(st!=="")rows=rows.filter(function(m){return String(m.enabled)===st;});if(!rows.length){el.innerHTML=\'<div class="empty">\u65e0\u5339\u914d</div>\';return;}el.innerHTML=\'<div class="table-wrap"><table><thead><tr><th>\u7f16\u53f7</th><th>\u540d\u79f0</th><th>\u8d26\u53f7</th><th>\u72b6\u6001</th><th>\u64cd\u4f5c</th></tr></thead><tbody>\'+rows.map(function(m){return\'<tr><td>\'+esc(m.merchantNo)+\'</td><td>\'+esc(m.merchantName||"")+\'</td><td>\'+esc(m.loginAccount||"")+\'</td><td><span class="pill \'+(m.enabled?"ok":"no")+\'">\'+(m.enabled?"\u542f\u7528":"\u7981\u7528")+\'</span></td><td><button class="btn ghost" onclick="toggleM(\\x27\'+esc(m.merchantNo)+\'\\x27,\'+(m.enabled?0:1)+\')">\'+(m.enabled?"\u7981\u7528":"\u542f\u7528")+\'</button> <button class="btn ghost" onclick="resetPwd(\\x27\'+esc(m.merchantNo)+\'\\x27)">\u91cd\u7f6e\u5bc6\u7801</button></td></tr>\';}).join("")+\'</tbody></table></div>\';}'
assert old314 in s, 'line314 old not found'
s = s.replace(old314, new314)

# ---- line 316 loadReport ----
old316 = 'async function loadReport(){const active=document.querySelector(".chip.active");const key=active?active.dataset.rpt:"coupons";const el=$("reportTable");el.innerHTML="<div class="empty">\u52a0\u8f7d\u4e2d...</div>";try{const r=await get(A+"/report/"+key);const arr=r.data||[];if(!arr.length){el.innerHTML="<div class="empty">\u6682\u65e0\u6570\u636e</div>";return;}const sample=arr[0];const cols=Object.keys(sample).slice(0,6);el.innerHTML="<div class="table-wrap"><table><thead><tr>"+cols.map(function(c){return"<th>"+esc(c)+"</th>";}).join("")+"</tr></thead><tbody>"+arr.slice(0,100).map(function(row){return"<tr>"+cols.map(function(c){return"<td>"+esc(row[c])+"</td>";}).join("")+"</tr>";}).join("")+"</tbody></table></div>";}catch(e){el.innerHTML="<div class="empty">\u52a0\u8f7d\u5931\u8d25</div>";}}'
new316 = 'async function loadReport(){const active=document.querySelector(".chip.active");const key=active?active.dataset.rpt:"coupons";const el=$("reportTable");el.innerHTML=\'<div class="empty">\u52a0\u8f7d\u4e2d...</div>\';try{const r=await get(A+"/report/"+key);const arr=r.data||[];if(!arr.length){el.innerHTML=\'<div class="empty">\u6682\u65e0\u6570\u636e</div>\';return;}const sample=arr[0];const cols=Object.keys(sample).slice(0,6);el.innerHTML=\'<div class="table-wrap"><table><thead><tr>\'+cols.map(function(c){return\'<th>\'+esc(c)+\'</th>\';}).join("")+\'</tr></thead><tbody>\'+arr.slice(0,100).map(function(row){return\'<tr>\'+cols.map(function(c){return\'<td>\'+esc(row[c])+\'</td>\';}).join("")+\'</tr>\';}).join("")+\'</tbody></table></div>\';}catch(e){el.innerHTML=\'<div class="empty">\u52a0\u8f7d\u5931\u8d25</div>\';}}'
assert old316 in s, 'line316 old not found'
s = s.replace(old316, new316)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('FIX OK')

# ---- 校验：残留的双引号嵌套检查 ----
import re
scripts = re.findall(r'<script(?![^>]*src=)[^>]*>(.*?)</script>', s, re.S)
print('script blocks:', len(scripts))
for i, sc in enumerate(scripts):
    hits = re.findall(r'"[^"\\]*?"(?:\s*\+\s*)?"[^"\\]*?"', sc)
    # 简单统计每行是否有可疑的 = "< ... " 嵌套
    for ln in sc.splitlines():
        m = re.findall(r'= "(<div|<tr|<td|<th|<span|<button|<table)[^"]*"[^"]*"', ln)
        if m:
            print('SUSPECT script', i, ':', m)
print('done')
