#!/usr/bin/env bash
# ============================================================================
# 宜必购生产环境验证脚本（部署 jar 更新后一键验收）
# 用法：bash verify-prod.sh [域名]  默认 https://ybgtc.com
# 预期：全 PASS = 生产恢复正常；有 FAIL = 未修复
# ============================================================================
D="${1:-https://ybgtc.com}"
PASS=0; FAIL=0

chk() { # 名称 期望 实际
  if [ "$2" = "$3" ]; then echo "✅ $1"; PASS=$((PASS+1));
  else echo "❌ $1（期望 $2 实际 $3）"; FAIL=$((FAIL+1)); fi
}

echo "== 1. 健康检查 =="
H=$(curl -s -m 8 "$D/actuator/health" | grep -o '"UP"')
chk "health UP" '"UP"' "$H"

echo "== 2. 三端页面 =="
for p in customer/index merchant/login admin/login; do
  C=$(curl -s -m 8 -o /dev/null -w "%{http_code}" "$D/h5/$p.html")
  chk "$p 200" "200" "$C"
done

echo "== 3. 登录接口（关键：应返回业务错误而非 500） =="
B=$(curl -s -m 8 -X POST "$D/api/merchant/auth/login" --data-urlencode "account=probe" --data-urlencode "password=x" | grep -o '账号或密码错误')
chk "merchant 登录优雅错误" "账号或密码错误" "$B"
B=$(curl -s -m 8 -X POST "$D/api/admin/auth/login" --data-urlencode "account=probe" --data-urlencode "password=x" | grep -o '账号或密码错误')
chk "admin 登录优雅错误" "账号或密码错误" "$B"

echo "== 4. 只读 API（商家配置/钱包应非 500） =="
C=$(curl -s -m 8 -o /dev/null -w "%{http_code}" "$D/api/customer/wallet/13800000000")
chk "钱包接口非500" "200" "$C"

echo ""
echo "======== 结果: PASS=$PASS FAIL=$FAIL ========"
[ "$FAIL" -eq 0 ] && echo "✅ 生产验证全部通过（jar 更新成功）" || echo "❌ 存在失败项，请检查"
