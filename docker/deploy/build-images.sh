#!/bin/bash
# 本地构建发布物：后端镜像 tar 包 + 三端前端静态产物
# 产物输出到 dist-release/，随后按 README 把它们传到云服务器
set -eu
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
OUT="$ROOT/docker/deploy/dist-release"
mkdir -p "$OUT"

# 0. 工具链
if [ -f "$HOME/crane-tools/env.sh" ]; then source "$HOME/crane-tools/env.sh"; fi
command -v docker >/dev/null || { echo "需要 docker"; exit 1; }

VERSION=$(date +%Y%m%d%H%M)
echo "== [1/4] 构建后端镜像 crane-backend:$VERSION =="
docker build -t crane-backend:$VERSION -t crane-backend:latest -f backend/Dockerfile backend

echo "== [2/4] 构建前端三端产物 =="
cd frontend
if [ ! -d node_modules ]; then corepack pnpm install; fi
ADMIN_BASE=/admin/ corepack pnpm -F admin build
corepack pnpm -F mobile build:h5
SCREEN_BASE=/screen/ corepack pnpm -F screen build
cd "$ROOT"

echo "== [3/4] 归集产物 =="
rm -rf "$OUT/www" && mkdir -p "$OUT/www/admin" "$OUT/www/h5" "$OUT/www/screen"
cp -r frontend/apps/admin/dist/*   "$OUT/www/admin/"
cp -r frontend/apps/mobile/dist/build/h5/* "$OUT/www/h5/"
cp -r frontend/apps/screen/dist/*  "$OUT/www/screen/"
docker save crane-backend:$VERSION | gzip > "$OUT/crane-backend-$VERSION.tar.gz"

echo "== [4/4] 校验 =="
du -sh "$OUT"/* | sed 's/^/  /'
echo "DONE。发布物目录：$OUT"
echo "下一：把 dist-release/ 与 docker/deploy/（不含 dist-release）整体上传云服务器 /opt/crane/deploy，按 README 完成部署"
