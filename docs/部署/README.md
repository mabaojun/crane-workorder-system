# 吊装业务管理系统 — 部署文档包

本目录是独立部署文档包，覆盖服务器部署、SSL 证书、Nginx 反向代理与常见故障排查。

| 文档 | 内容 |
|---|---|
| [01-生产服务器部署手册.md](./01-生产服务器部署手册.md) | 从上传发布包到启动、验证、发布回滚、备份 |
| [02-SSL证书与反向代理配置.md](./02-SSL证书与反向代理配置.md) | 腾讯云 SSL 证书、Nginx 443、HTTP 跳转、443 被占用方案 |
| [03-镜像构建与故障排查.md](./03-镜像构建与故障排查.md) | `failed to resolve reference ... crane-backend` 等启动问题 |

## 本次新服务器

- IP：`82.157.64.189`
- 用户：`ubuntu`（SSH 密钥登录，无口令）
- 系统：Ubuntu 24.04
- 初始化脚本：发布包内 `deploy/server-init-ubuntu24-tencent.sh`
  - 腾讯 Ubuntu 软件源
  - 腾讯 Docker CE 源
  - Docker Compose 插件
  - Docker Hub 镜像加速

## 当前约定

- 部署目录：`/crane`
- 域名：`jndys.top`
- 反向代理：`crane-nginx` 直接接管 80/443
- 后端镜像：服务器本地构建，`pull_policy: never`
- 证书目录：`/crane/certs/`
  - `fullchain.crt`
  - `privkey.key`
- 微信小程序合法域名：`https://jndys.top`（443）

## 最短部署路径

```bash
sudo mkdir -p /crane
sudo chown -R "$USER":"$USER" /crane

tar -xzf crane-release-<版本>.tar.gz -C /crane --strip-components=1
# 上传证书到 /crane/certs/fullchain.crt 与 /crane/certs/privkey.key

cd /crane/deploy
sudo bash server-init-ubuntu24-tencent.sh   # 腾讯源 + Docker + 镜像加速
cp .env.example .env
vi .env
docker compose up -d --build
docker compose ps
curl -fsS https://jndys.top/api/v1/system/health
```

> 更完整步骤以 `01-生产服务器部署手册.md` 为准。
