#!/usr/bin/env bash
# ============================================================================
# 宜必购盲盒系统 · 生产部署脚本（宝塔服务器 / Docker Compose）
# 用法：在目标生产服务器（宝塔）上执行：
#   bash deploy-prod.sh [jar路径] [cert目录]
# 示例：bash deploy-prod.sh /path/ibigou.jar /path/certs
# 前置：docker + docker compose；本脚本所在目录含 docker-compose.yml + nginx/ + .env
# ============================================================================
set -e

JAR_SRC="${1:-/volume1/Download/ybg/deploy/ibigou.jar}"
CERT_SRC="${2:-/volume1/Download/ybg/certs}"
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "== 1/6 检查环境 =="
command -v docker >/dev/null || { echo "缺少 docker"; exit 1; }
command -v docker >/dev/null && docker compose version >/dev/null 2>&1 || { echo "缺少 docker compose"; exit 1; }
[ -f "$HERE/.env" ] || { echo "缺少 .env（请先配置 IBIGOU_DB_PASSWORD/IBIGOU_ADMIN_INIT_PWD/IBIGOU_DOMAIN）"; exit 1; }

echo "== 2/6 商户证书 =="
mkdir -p "$HERE/certs"
cp "$CERT_SRC/apiclient_cert.pem" "$CERT_SRC/apiclient_key.pem" "$HERE/certs/" 2>/dev/null || { echo "证书复制失败（请提供证书目录）"; exit 1; }
chmod 600 "$HERE/certs/apiclient_key.pem"
echo "证书就绪: $(ls "$HERE/certs")"

echo "== 3/6 应用 jar =="
cp "$JAR_SRC" "$HERE/ibigou.jar"
ls -la "$HERE/ibigou.jar" | awk '{print "jar: "$5" bytes"}'

echo "== 4/6 首次建库（幂等） =="
# 首次启动时 Flyway 自动建表（V1+V2+V3）；此处仅确保库存在
docker compose up -d mysql 2>/dev/null || true

echo "== 5/6 启动全套 =="
docker compose up -d
sleep 20
echo "== 6/6 健康检查 =="
for i in 1 2 3 4 5; do
  S=$(docker compose exec -T app curl -s -m 3 http://127.0.0.1:8085/actuator/health 2>/dev/null || true)
  echo "$S" | grep -q '"UP"' && { echo "✅ 生产服务已就绪: $(echo "$S" | head -c 40)"; exit 0; }
  echo "  等待服务启动 ($i/5)..."; sleep 8
done
echo "⚠️ 服务未在预期时间内就绪，请查看 docker compose logs app"
