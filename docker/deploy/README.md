# 生产部署（docker/deploy）

完整步骤见 **[DEPLOY.md](./DEPLOY.md)**，本文件仅保留快速索引。

## 方案

- Ubuntu 24.04 单机 Docker，与同机其他容器隔离（`crane-` 前缀 + `crane-net`）
- 反向代理：`crane-nginx` 直接接管宿主 80/443
- 域名：`jndys.top`（HTTPS + 微信小程序合法域名）
- 后端镜像：`linux/amd64`，容器内端口 7777
- 数据目录：`/crane`

## 打包

```bash
bash docker/deploy/build-images.sh
```

产物：

```text
docker/deploy/dist-release/crane-release-<版本>.tar.gz
```

包含后端 jar、后台/H5/大屏静态产物、微信小程序 `mp-weixin/`、建表 SQL、部署配置、`version.json` 与 `SHA256SUMS`。后端镜像在服务器上原生构建。

## 服务器

```bash
sudo mkdir -p /crane
sudo chown -R "$USER":"$USER" /crane
tar -xzf crane-release-<版本>.tar.gz -C /crane --strip-components=1
cd /crane/deploy
cp .env.example .env && vi .env
docker compose up -d --build
```

验证：

```bash
curl -fsS https://jndys.top/api/v1/system/health
```

## 文档

- [DEPLOY.md](./DEPLOY.md)：完整部署、SSL、小程序、发布回滚、备份排障
