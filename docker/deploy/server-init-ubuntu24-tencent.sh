#!/bin/bash
# Ubuntu 24.04 新服务器初始化：腾讯软件源 + Docker CE + Compose 插件 + 镜像加速
# 用法：sudo bash server-init-ubuntu24-tencent.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 sudo 运行：sudo bash $0"
  exit 1
fi

TARGET_USER="${SUDO_USER:-ubuntu}"
TS=$(date +%Y%m%d_%H%M%S)

echo "== [1/5] 备份并切换 Ubuntu 24.04 腾讯软件源 =="
if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
  cp -a /etc/apt/sources.list.d/ubuntu.sources "/etc/apt/sources.list.d/ubuntu.sources.bak.$TS"
  sed -i -E 's#https?://(archive|security)\.ubuntu\.com/ubuntu/?#https://mirrors.cloud.tencent.com/ubuntu/#g' \
    /etc/apt/sources.list.d/ubuntu.sources
  sed -i -E 's#https?://ports\.ubuntu\.com/ubuntu-ports/?#https://mirrors.cloud.tencent.com/ubuntu-ports/#g' \
    /etc/apt/sources.list.d/ubuntu.sources
  echo "已更新 /etc/apt/sources.list.d/ubuntu.sources"
fi

if [ -f /etc/apt/sources.list ]; then
  cp -a /etc/apt/sources.list "/etc/apt/sources.list.bak.$TS"
  sed -i -E 's#https?://(archive|security)\.ubuntu\.com/ubuntu/?#https://mirrors.cloud.tencent.com/ubuntu/#g' \
    /etc/apt/sources.list
  sed -i -E 's#https?://ports\.ubuntu\.com/ubuntu-ports/?#https://mirrors.cloud.tencent.com/ubuntu-ports/#g' \
    /etc/apt/sources.list
  echo "已更新 /etc/apt/sources.list"
fi

echo "== [2/5] 更新软件包索引并安装基础依赖 =="
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl gnupg python3

echo "== [3/5] 添加腾讯云 Docker CE 源并安装 Docker =="
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.tencent.com/docker-ce/linux/ubuntu/gpg \
  | gpg --batch --yes --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

ARCH=$(dpkg --print-architecture)
CODENAME=$(. /etc/os-release && echo "$VERSION_CODENAME")
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.tencent.com/docker-ce/linux/ubuntu ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "== [4/5] 配置 Docker 镜像加速 =="
mkdir -p /etc/docker
if [ -f /etc/docker/daemon.json ]; then
  cp -a /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$TS"
fi
python3 - <<'PY'
import json
import os

path = "/etc/docker/daemon.json"
data = {}
if os.path.exists(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        data = {}

mirrors = data.get("registry-mirrors", [])
for mirror in ["https://mirror.ccs.tencentyun.com", "https://docker.1ms.run"]:
    if mirror not in mirrors:
        mirrors.append(mirror)
data["registry-mirrors"] = mirrors

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

systemctl daemon-reload
systemctl enable --now docker
systemctl restart docker

echo "== [5/5] 用户组与验证 =="
usermod -aG docker "$TARGET_USER" || true
docker --version
docker compose version

cat <<EOF

初始化完成。

当前用户：$TARGET_USER
Docker 组已加入，重新登录 SSH 后即可免 sudo 使用 docker。
临时生效可执行：newgrp docker

下一步：
  sudo mkdir -p /crane
  sudo chown -R ${TARGET_USER}:${TARGET_USER} /crane
  tar -xzf /tmp/crane-release-<版本>.tar.gz -C /crane --strip-components=1
  cd /crane/deploy
  cp .env.example .env
  vi .env
  docker compose up -d --build
EOF
