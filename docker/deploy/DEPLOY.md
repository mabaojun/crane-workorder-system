# 济南诚重吊装业务管理系统 — 生产服务器部署手册

> 适用环境：Ubuntu 24.04、单机 Docker、服务器已有其他容器在运行  
> 域名：`jndys.top`（与微信小程序合法域名一致）  
> 反向代理：**Nginx 1.27（容器 `crane-nginx`）**，直接接管宿主 80/443  
> 后端镜像：服务器端基于 jar 原生构建 `linux/amd64`  
> 证书参考：[腾讯云 SSL 证书 Nginx 服务器安装部署](https://cloud.tencent.com/document/product/400/35244)

---

## 0. 方案结论与端口

| 项目 | 取值 |
|---|---|
| 对外入口 | `https://jndys.top`（443） |
| HTTP | `http://jndys.top`（80，仅跳转 HTTPS） |
| 反向代理 | 本系统 `crane-nginx`，不依赖服务器上其他 Nginx |
| 后端 | 容器 `crane-app`，仅内网 `7777`，不对外暴露 |
| 数据库 | 容器 `crane-mysql`，宿主仅 `127.0.0.1:7772` |
| 隔离 | 独立网络 `crane-net`、独立卷 `crane-mysql-data`、容器名统一 `crane-` 前缀 |
| 数据目录 | `/crane` |
| 微信小程序 | 合法域名统一配置 `https://jndys.top`（必须是 443） |

> 本系统只占用 `80/443/7772`，不会改动或停止服务器上已有的其他容器。  
> 部署目录固定为根目录下 `/crane`（不使用 `/opt/crane`，服务器 `/opt` 已有其他内容）。

---

## 1. 本地打包（开发机执行）

```bash
cd /Users/goofy/项目/胡浩业务系统/crane-workorder-system
bash docker/deploy/build-images.sh
```

脚本会完成：

1. Maven 测试 + 打包后端可执行 jar；
2. 构建后台 `admin`、H5、数据大屏、微信小程序四端产物；
3. 生成版本化发布目录与压缩包（后端镜像在服务器上原生构建，避免 Apple Silicon 交叉架构问题）。

产物：

```text
docker/deploy/dist-release/
├── crane-release-<版本>/                 # 解压即用的目录
│   ├── deploy/                           # docker-compose / nginx / 备份脚本
│   ├── mysql/init/                       # 建表 SQL + 本地 API 默认配置
│   ├── backend/                          # crane-backend.jar + Dockerfile.runtime
│   ├── www/{admin,h5,screen}/            # 三端静态产物
│   ├── mp-weixin/                        # 微信小程序产物（导入开发者工具）
│   ├── certs/                            # 证书放置目录
│   ├── version.json                      # 版本/提交/架构/构建时间
│   ├── SHA256SUMS                        # 完整性校验
│   └── DEPLOY.md                         # 本手册
├── crane-release-<版本>.tar.gz            # 上传服务器的压缩包
└── release-history.log                   # 历史版本记录
```

指定版本号或跳过测试：

```bash
RELEASE_VERSION=v1.0.0 bash docker/deploy/build-images.sh
SKIP_TESTS=1 bash docker/deploy/build-images.sh
```

---

## 2. 上传到服务器

在本地执行（把 `<版本>` 替换为实际版本，`<私钥文件>` 替换为 SSH 私钥）：

```bash
scp -i ~/.ssh/<私钥文件> \
  docker/deploy/dist-release/crane-release-<版本>.tar.gz \
  ubuntu@82.157.64.189:/tmp/
```

同时在本地准备腾讯云证书文件（Nginx 版）：

```text
jndys.top_bundle.crt
jndys.top.key
```

---

## 3. 新服务器初始化（Ubuntu 24.04）

> 本次服务器：`82.157.64.189`，登录用户 `ubuntu`，SSH 密钥登录，无口令。  
> 域名 `jndys.top` 已解析到该服务器。  
> 以下命令在服务器执行，需要管理员权限的已标注 `sudo`。

### 3.1 登录服务器

本地执行（把 `<私钥文件>` 替换为你的 SSH 私钥）：

```bash
ssh -i ~/.ssh/<私钥文件> ubuntu@82.157.64.189
```

### 3.2 切换腾讯软件源（Ubuntu 24.04）

Ubuntu 24.04 使用 `/etc/apt/sources.list.d/ubuntu.sources`：

```bash
sudo cp /etc/apt/sources.list.d/ubuntu.sources \
  "/etc/apt/sources.list.d/ubuntu.sources.bak.$(date +%F_%H%M)"

sudo sed -i -E \
  's#https?://(archive|security)\.ubuntu\.com/ubuntu/?#https://mirrors.cloud.tencent.com/ubuntu/#g; s#https?://ports\.ubuntu\.com/ubuntu-ports/?#https://mirrors.cloud.tencent.com/ubuntu-ports/#g' \
  /etc/apt/sources.list.d/ubuntu.sources

sudo apt-get update
```

如果系统仍存在 `/etc/apt/sources.list`，同样替换：

```bash
sudo sed -i -E \
  's#https?://(archive|security)\.ubuntu\.com/ubuntu/?#https://mirrors.cloud.tencent.com/ubuntu/#g' \
  /etc/apt/sources.list
```

### 3.3 安装 Docker CE + Compose（腾讯源）

```bash
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://mirrors.tencent.com/docker-ce/linux/ubuntu/gpg \
  | sudo gpg --batch --yes --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://mirrors.tencent.com/docker-ce/linux/ubuntu \
$(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt-get update
sudo apt-get install -y \
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu
```

重新登录 SSH（或执行 `newgrp docker`）后，`ubuntu` 用户可免 sudo 使用 Docker。

配置镜像加速：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<'EOF'
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.1ms.run"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
```

验证：

```bash
docker version
docker compose version
```

> 如果 `/etc/docker/daemon.json` 已存在，不要直接覆盖；可在第 4 步解压后运行
> `sudo bash /crane/deploy/server-init-ubuntu24-tencent.sh`，脚本会合并 `registry-mirrors`。

### 3.4 检查端口与防火墙

```bash
ss -lntp | grep -E ':(80|443|7772)\b' || echo "80/443/7772 均空闲"
```

- `80/443`：本系统 Nginx 使用；
- `7772`：仅绑定 `127.0.0.1` 供数据库排查。

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
```

腾讯云控制台「安全组」放行 TCP 22、80、443。**不要放行 7772。**

### 3.5 域名解析确认

```bash
ping -c 1 jndys.top
```

期望解析到 `82.157.64.189`。

---

## 4. 解压发布包

根目录下创建 `/crane` 需要 sudo 权限，创建后把目录归属改为当前登录用户：

```bash
sudo mkdir -p /crane
sudo chown -R "$USER":"$USER" /crane
tar -xzf /tmp/crane-release-<版本>.tar.gz -C /crane --strip-components=1
ls -la /crane
```

如果第 3 步没有手工安装 Docker，可在这个阶段运行一键初始化脚本（腾讯源 + Docker + 镜像加速）：

```bash
sudo bash /crane/deploy/server-init-ubuntu24-tencent.sh
```

期望目录：

```text
/crane/
├── deploy/
├── mysql/init/
├── backend/
├── www/
├── mp-weixin/
├── certs/
├── version.json
└── SHA256SUMS
```

可选完整性校验：

```bash
cd /crane && sha256sum -c SHA256SUMS
```

---

## 5. 配置 SSL 证书

参考腾讯云文档下载 **Nginx 版**证书，上传到服务器：

```bash
scp -i ~/.ssh/<私钥文件> jndys.top_bundle.crt jndys.top.key ubuntu@82.157.64.189:/tmp/
```

统一重命名并放到证书目录：

```bash
mkdir -p /crane/certs
mv /tmp/jndys.top_bundle.crt /crane/certs/fullchain.crt
mv /tmp/jndys.top.key        /crane/certs/privkey.key
chmod 644 /crane/certs/fullchain.crt
chmod 600 /crane/certs/privkey.key
ls -l /crane/certs
```

本系统 Nginx 的 SSL 关键配置（已写入 `deploy/nginx.conf`）：

```nginx
listen 443 ssl default_server;
http2 on;
server_name jndys.top;
ssl_certificate     /etc/nginx/certs/fullchain.crt;
ssl_certificate_key /etc/nginx/certs/privkey.key;
ssl_protocols TLSv1.2 TLSv1.3;
```

80 端口仅保留 `/healthz`，其余请求 301 跳转 HTTPS。

---

## 6. 配置环境变量

```bash
cd /crane/deploy
cp .env.example .env
chmod 600 .env
vi .env
```

必须修改的项：

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | 数据库 root 密码，强随机 |
| `MYSQL_PASSWORD` | 业务库密码，强随机 |
| `CRANE_JWT_SECRET` | JWT 密钥，执行 `openssl rand -hex 32` 生成 |
| `CRANE_ADMIN_PASSWORD` | 初始管理员密码 |
| `WECHAT_APPID` | `wxcd19e4508b62d824`（已预填） |
| `WECHAT_SECRET` | 小程序 AppSecret（必填，否则微信登录不可用） |
| `APP_VERSION` | 发布包已按当前版本预填，通常无需修改；手工修改时须与 `version.json` 一致 |

示例：

```bash
openssl rand -hex 32
```

把输出填入 `CRANE_JWT_SECRET`。

查看本次版本号：

```bash
cat /crane/version.json
```

> 初始管理员账号为 `.env` 中的 `CRANE_ADMIN_PHONE` / `CRANE_ADMIN_PASSWORD`，首次启动自动创建。
> 本地联调验证过的高德 Key、AI DeepSeek 配置已随 `mysql/init/02-local-defaults.sql` 作为初始化默认值写入数据库，首次启动即可用。

---

## 7. 启动系统

```bash
cd /crane/deploy
docker compose config >/dev/null && echo "compose 配置校验通过"

# --build 会使用 /crane/backend/Dockerfile.runtime 构建后端镜像
# 首次需要拉取 eclipse-temurin:17-jre
docker compose up -d --build
docker compose ps
```

> `docker-compose.yml` 已为 `crane-app` 设置：
> - `pull_policy: never`：后端镜像只使用服务器本地构建结果，不会去 Docker Hub 拉取；
> - `build.context: ../backend`：执行 `--build` 时自动构建并打上 `crane-backend:<APP_VERSION>` 标签。
>
> 也可以手动构建后再启动：`bash /crane/deploy/build-server-image.sh && docker compose up -d`。

首次启动流程：

1. `crane-mysql` 初始化 `crane_workorder` 库（含 32+ 表、字典、系统配置、API 默认值）；
2. `crane-app` 等待 MySQL 健康后启动，自动创建管理员账号；
3. `crane-nginx` 加载证书并监听 80/443；
4. `crane-backup` 启动后自动做一次数据库备份。

查看日志：

```bash
docker logs -f crane-app --tail 100
docker logs -f crane-nginx --tail 100
```

---

## 8. 部署验证

### 8.1 容器与接口

```bash
docker compose ps
curl -fsS http://127.0.0.1/healthz && echo
curl -fsS https://jndys.top/api/v1/system/health
```

健康接口期望返回 `code:0` 且 `status:UP`。

### 8.2 三端页面

| 端 | 地址 | 账号 |
|---|---|---|
| PC 后台 | `https://jndys.top/admin/` | `.env` 中管理员手机号/密码 |
| 移动端 H5 | `https://jndys.top/h5/` | 管理员/司机/客户账号 |
| 数据大屏 | `https://jndys.top/screen/` | 管理员账号密码 |

### 8.3 SSL 检查

```bash
curl -I https://jndys.top/admin/
openssl s_client -connect jndys.top:443 -servername jndys.top </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -dates
```

浏览器地址栏应为安全锁，证书域名与 `jndys.top` 一致。

### 8.4 文件上传/下载

后台打开工单详情上传一张图片，确认：

```bash
ls -l /crane/data/files
```

并有文件落盘。

---

## 9. 微信小程序对接

### 9.1 小程序后台配置合法域名

微信公众平台 →「开发管理」→「开发设置」→「服务器域名」：

| 类型 | 值 |
|---|---|
| request 合法域名 | `https://jndys.top` |
| uploadFile 合法域名 | `https://jndys.top` |
| downloadFile 合法域名 | `https://jndys.top` |

注意：

- 必须是 HTTPS + 443 端口，不能用 IP 或 localhost；
- 域名需完成 ICP 备案；
- 修改后建议在开发者工具「详情 → 域名信息」刷新确认。

### 9.2 服务端微信登录配置

`.env` 中确认：

```ini
WECHAT_APPID=wxcd19e4508b62d824
WECHAT_SECRET=<小程序后台获取的 AppSecret>
```

修改后重启后端：

```bash
cd /crane/deploy
docker compose up -d crane-app
```

后端登录链路：`POST /api/v1/auth/wechat-login` → 微信 `jscode2session` → 绑定手机号 → 签发系统令牌。
`WECHAT_SECRET` 只存在服务器 `.env`，不会下发到前端。

### 9.3 导入小程序产物

本地发布包中的 `mp-weixin/` 即 uni-app 微信小程序构建产物：

1. 打开微信开发者工具 → 导入项目；
2. 目录选择 `mp-weixin/`；
3. AppID 填 `wxcd19e4508b62d824`；
4. 联调阶段可勾选「不校验合法域名」，**上传体验版/正式版前必须取消勾选**；
5. 在小程序后台补充「用户隐私保护指引」，声明位置信息用途（司机签到、地图选点、导航）。

小程序位置能力已在 `manifest.json` 声明：

```json
"requiredPrivateInfos": ["getLocation"],
"permission": { "scope.userLocation": { "desc": "用于司机定位打卡、地图选点与导航" } }
```

### 9.4 当前登录能力边界（重要）

| 能力 | 状态 | 说明 |
|---|---|---|
| 账号密码登录 | 可用 | 管理员在后台预建司机/客户账号并设置初始密码 |
| 微信授权登录（已绑定账号） | 可用 | 依赖 `WECHAT_APPID` / `WECHAT_SECRET` |
| 微信首次登录绑定手机号 | 暂不可用 | 绑定需要短信验证码；真实短信通道尚未接入 |
| 短信验证码登录 | 暂不可用 | 生产 `CRANE_SMS_MOCK=false` 后不再回显验证码 |

> 首次上线请使用管理员预设账号 + 密码登录。真实短信通道接入后，把 `CRANE_SMS_MOCK` 调整为符合实际的配置即可恢复短信相关能力。

### 9.5 域名变更时重新构建

小程序 API 地址在构建时写入，默认 `https://jndys.top`。若更换域名：

```bash
cd frontend
VITE_API_ORIGIN=https://新域名 corepack pnpm -F mobile build:mp-weixin
```

---

## 10. 版本发布与回滚

### 10.1 发布新版本

本地：

```bash
RELEASE_VERSION=v1.0.1 bash docker/deploy/build-images.sh
scp docker/deploy/dist-release/crane-release-v1.0.1.tar.gz root@服务器IP:/tmp/
```

服务器：

```bash
tar -xzf /tmp/crane-release-v1.0.1.tar.gz -C /crane --strip-components=1
cd /crane/deploy
sed -i 's/^APP_VERSION=.*/APP_VERSION=v1.0.1/' .env
docker compose up -d --build
docker compose ps
```

> `--strip-components=1` 会更新 `deploy/`、`www/`、`backend/`、`mysql/init/` 等文件，但不会覆盖 `deploy/.env`（发布包只含 `.env.example`）与 `data/`、`backup/`、`certs/`。

### 10.2 回滚应用版本

旧镜像仍在服务器本地时：

```bash
cd /crane/deploy
sed -i 's/^APP_VERSION=.*/APP_VERSION=<旧版本>/' .env
docker compose up -d crane-app
```

查看本地镜像：

```bash
docker images | grep crane-backend
```

### 10.3 数据库变更说明

当前版本使用 `mysql/init/` 做首次初始化，**不会在已有数据卷上重复执行**。
后续版本如包含表结构变更，发布前必须先备份，并按版本说明手工执行迁移 SQL；禁止直接删除 `crane-mysql-data` 卷。

---

## 11. 备份与恢复

### 11.1 自动备份

`crane-backup` 每日 02:30 备份数据库和上传文件到 `/crane/backup`，默认保留 7 天（`.env` 的 `BACKUP_KEEP_DAYS`）。

### 11.2 手动备份

```bash
cd /crane/deploy
source .env
mkdir -p /crane/backup/manual
docker exec crane-mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers --no-tablespaces crane_workorder \
  | gzip > "/crane/backup/manual/crane_$(date +%F_%H%M).sql.gz"
```

### 11.3 恢复

```bash
cd /crane/deploy
source .env
docker compose stop crane-app
gunzip < /crane/backup/manual/crane_YYYY-MM-DD_HHMM.sql.gz \
  | docker exec -i crane-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" crane_workorder
docker compose start crane-app
```

---

## 12. 日常运维与故障排查

```bash
cd /crane/deploy
docker compose ps
docker compose logs -f crane-app --tail 200
docker compose restart crane-app
docker exec crane-nginx nginx -t
docker exec crane-mysql mysql -ucrane -p"$MYSQL_PASSWORD" crane_workorder -e "SHOW TABLES;"
```

| 现象 | 排查 |
|---|---|
| 启动报 `failed to resolve reference ... crane-backend` | 本地后端镜像未构建成功。在 `/crane/deploy` 执行 `docker compose up -d --build`，或先执行 `bash build-server-image.sh`；再用 `docker images` 确认存在 crane-backend 镜像 |
| 502 Bad Gateway | `docker logs crane-app`；确认 MySQL healthy；确认后端监听 7777 |
| Nginx 起不来 | `docker exec crane-nginx nginx -t`；检查证书文件名/权限 |
| 证书告警 | 确认 `fullchain.crt` 为完整链、域名 `jndys.top`、系统时间正确 |
| 端口被占用 | `ss -lntp \| grep -E ':(80\|443\|7772)\b'` |
| 小程序提示域名不合法 | 微信后台三类域名是否都配 `https://jndys.top`，证书是否有效 |
| 小程序登录失败 | `.env` 的 `WECHAT_SECRET` 是否正确；`docker logs crane-app` 查看微信返回 |
| 管理端实时推送断开 | 确认 Nginx 的 `/api/v1/notify/stream` 已关闭 `proxy_buffering` |
| 数据库初始化失败 | `docker logs crane-mysql`；仅首次部署可删除卷重来（会丢数据） |

停止与清理（**会删除数据库数据，先备份**）：

```bash
cd /crane/deploy
docker compose down          # 停容器，保留数据
docker compose down -v       # 停容器并删除数据库卷
```

---

## 13. 安全清单

- [ ] `.env` 权限 600，且未提交到 Git
- [ ] `CRANE_JWT_SECRET`、数据库密码为随机强密码
- [ ] 修改默认管理员密码
- [ ] `WECHAT_SECRET` 仅服务器保存
- [ ] 首次部署验证后，在后台「系统配置」轮换高德/AI API Key
- [ ] 云安全组仅放行 80/443/SSH，禁止放行 7772
- [ ] 定期检查 `/crane/backup` 与磁盘空间
- [ ] 发布包保留 `version.json` 与 `release-history.log`，便于追溯

---

## 附：与同机其他 Docker 应用的隔离说明

- Compose 项目名固定为 `crane`；
- 网络固定 `crane-net`，卷固定 `crane-mysql-data`；
- 仅绑定宿主 `80/443/127.0.0.1:7772`；
- `docker compose down` 只影响 `crane-` 前缀容器，不影响其他项目；
- 若后续 80/443 被其他应用占用，可改为：
  - 本系统 Nginx 仅绑定 `127.0.0.1:7771`；
  - 在现有 Nginx 增加 `server` 反向代理到 `http://127.0.0.1:7771`；
  - 小程序仍通过现有 Nginx 的 443 对外。
