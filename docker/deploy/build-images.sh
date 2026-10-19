#!/bin/bash
# 版本化发布包构建：后端 jar + 三端前端 + 小程序产物 + 部署文件
# 后端镜像在服务器上构建（服务器原生 amd64），避免 Apple Silicon 交叉构建拉取 amd64 基础镜像
# 产物：
#   dist-release/crane-release-<版本>/        解压即用的发布目录
#   dist-release/crane-release-<版本>.tar.gz  上传服务器的压缩包
#   dist-release/release-history.log          历史版本记录
# 用法：
#   bash docker/deploy/build-images.sh
#   RELEASE_VERSION=v1.0.0 SKIP_TESTS=1 bash docker/deploy/build-images.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
OUT="$ROOT/docker/deploy/dist-release"
STAGE_NAME_BASE="crane-release"

# 0. 工具链
if [ -f "$HOME/crane-tools/env.sh" ]; then source "$HOME/crane-tools/env.sh"; fi
command -v mvn >/dev/null || { echo "需要 Maven 17"; exit 1; }
command -v node >/dev/null || { echo "需要 Node.js"; exit 1; }

# 1. 版本号：优先 RELEASE_VERSION；否则 日期-时间 + git 短 SHA + dirty 标记
GIT_SHA=$(git rev-parse --short=7 HEAD 2>/dev/null || echo nogit)
DIRTY_SUFFIX=""
if [ -n "$(git status --porcelain 2>/dev/null | head -n 1)" ]; then DIRTY_SUFFIX="-dirty"; fi
VERSION="${RELEASE_VERSION:-v$(date +%Y.%m.%d-%H%M%S)-${GIT_SHA}${DIRTY_SUFFIX}}"
VERSION=$(printf '%s' "$VERSION" | tr -c 'A-Za-z0-9_.-' '-')
BUILD_TIME=$(date '+%Y-%m-%d %H:%M:%S %Z')
STAGE="$OUT/$STAGE_NAME_BASE-$VERSION"
TARBALL="$OUT/$STAGE_NAME_BASE-$VERSION.tar.gz"

echo "== 版本：$VERSION  目标平台：linux/amd64 =="
mkdir -p "$OUT"

# 2. 后端：测试（默认执行）+ 可执行 jar
cd "$ROOT/backend"
if [ "${SKIP_TESTS:-0}" = "1" ]; then
  echo "== [1/5] Maven 打包（跳过测试） =="
  mvn -q -DskipTests package
else
  echo "== [1/5] Maven 测试并打包 =="
  mvn -q package
fi
test -f target/crane-backend.jar || { echo "缺少 target/crane-backend.jar"; exit 1; }
cd "$ROOT"

# 3. 前端：后台 / H5 / 大屏 / 微信小程序
echo "== [2/5] 构建前端四端产物 =="
cd "$ROOT/frontend"
if [ ! -d node_modules ]; then corepack pnpm install; fi
ADMIN_BASE=/admin/ corepack pnpm -F admin build
H5_BASE=/h5/ corepack pnpm -F mobile build:h5
corepack pnpm -F mobile build:mp-weixin
SCREEN_BASE=/screen/ corepack pnpm -F screen build
cd "$ROOT"

# 4. 组装发布目录（与服务器 /crane 目录结构一致）
echo "== [3/5] 组装发布包 =="
rm -rf "$STAGE"
mkdir -p "$STAGE"/{deploy,mysql/init,backend,www/admin,www/h5,www/screen,mp-weixin,certs,data/files,backup}

cp docker/deploy/docker-compose.yml "$STAGE/deploy/"
cp docker/deploy/nginx.conf          "$STAGE/deploy/"
cp docker/deploy/backup-loop.sh      "$STAGE/deploy/"
# .env.example 预填本次版本号，避免部署时 APP_VERSION 为空回退到 latest
sed "s|^APP_VERSION=.*|APP_VERSION=$VERSION|" docker/deploy/.env.example > "$STAGE/deploy/.env.example"
cp docker/deploy/DEPLOY.md           "$STAGE/DEPLOY.md"
cp docker/mysql/init/*.sql           "$STAGE/mysql/init/"
cp backend/target/crane-backend.jar  "$STAGE/backend/"
cp backend/Dockerfile.runtime        "$STAGE/backend/"
cp -R frontend/apps/admin/dist/.            "$STAGE/www/admin/"
cp -R frontend/apps/mobile/dist/build/h5/.  "$STAGE/www/h5/"
cp -R frontend/apps/screen/dist/.           "$STAGE/www/screen/"
cp -R frontend/apps/mobile/dist/build/mp-weixin/. "$STAGE/mp-weixin/"
# 小程序开发者工具的本地私有配置不随发布包上传
rm -f "$STAGE/mp-weixin/project.private.config.json"

# 服务器端镜像构建脚本（原生 amd64，无需交叉编译）
cat > "$STAGE/deploy/build-server-image.sh" <<EOF
#!/bin/sh
# 在服务器上构建后端镜像（原生 amd64）
set -eu
cd /crane/backend
docker build -f Dockerfile.runtime -t crane-backend:$VERSION -t crane-backend:latest .
echo "镜像构建完成：crane-backend:$VERSION"
EOF
chmod +x "$STAGE/deploy/build-server-image.sh"

cat > "$STAGE/certs/README.txt" <<'EOF'
将腾讯云下载的 Nginx 版证书放到本目录，并统一重命名：
  jndys.top_bundle.crt -> fullchain.crt
  jndys.top.key        -> privkey.key
然后执行：docker compose restart crane-nginx
EOF

cat > "$STAGE/version.json" <<EOF
{
  "version": "$VERSION",
  "buildTime": "$BUILD_TIME",
  "gitCommit": "$GIT_SHA",
  "gitDirty": $([ -n "$DIRTY_SUFFIX" ] && echo true || echo false),
  "platform": "linux/amd64",
  "backendJar": "backend/crane-backend.jar",
  "backendDockerfile": "backend/Dockerfile.runtime",
  "backendImage": "crane-backend:$VERSION",
  "components": {
    "admin": "www/admin",
    "h5": "www/h5",
    "screen": "www/screen",
    "miniprogram": "mp-weixin"
  }
}
EOF

# 5. 校验与压缩
echo "== [4/5] 生成 SHA256 校验清单 =="
cd "$STAGE"
if command -v shasum >/dev/null; then
  find . -type f ! -name SHA256SUMS -print0 | xargs -0 shasum -a 256 > SHA256SUMS
else
  find . -type f ! -name SHA256SUMS -print0 | xargs -0 sha256sum > SHA256SUMS
fi
cd "$ROOT"

echo "== [5/5] 生成压缩包 =="
tar -czf "$TARBALL" -C "$OUT" "$STAGE_NAME_BASE-$VERSION"
echo "$(date '+%F %T')  $VERSION  linux/amd64  $(du -h "$TARBALL" | cut -f1)  $TARBALL" >> "$OUT/release-history.log"

du -sh "$STAGE"/* | sed 's/^/  /'
echo "DONE"
echo "发布目录：$STAGE"
echo "上传压缩包：$TARBALL"
