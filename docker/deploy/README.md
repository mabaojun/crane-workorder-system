# 生产部署（docker/deploy）

完整步骤见 **[DEPLOY.md](./DEPLOY.md)**，本文件仅保留快速索引。

## 本次服务器

- IP：`82.157.64.189`
- 用户：`ubuntu`（SSH 密钥登录，无口令）
- 系统：Ubuntu 24.04
- 域名：`jndys.top`

## 方案

- 单机 Docker，与同机其他容器隔离（`crane-` 前缀 + `crane-net`）
- 反向代理：`crane-nginx` 直接接管宿主 80/443
- 后端镜像：服务器本地构建 `linux/amd64`，容器内端口 7777
- 数据目录：`/crane`
- 腾讯软件源 + 腾讯 Docker CE 源 + 镜像加速

## 打包

```bash
bash docker/deploy/build-images.sh
```

产物：

```text
docker/deploy/dist-release/crane-release-<版本>.tar.gz
```

包含后端 jar、后台/H5/大屏静态产物、微信小程序 `mp-weixin/`、建表 SQL、部署配置、`UPGRADE.md`、`version.json` 与 `SHA256SUMS`。

## 服务器

```bash
# 本地上传
scp -i ~/.ssh/<私钥文件> \
  docker/deploy/dist-release/crane-release-<版本>.tar.gz \
  ubuntu@82.157.64.189:/tmp/

# 服务器执行
sudo mkdir -p /crane
sudo chown -R "$USER":"$USER" /crane
tar -xzf /tmp/crane-release-<版本>.tar.gz -C /crane --strip-components=1

cd /crane/deploy
sudo bash server-init-ubuntu24-tencent.sh   # 腾讯源 + Docker + 镜像加速
cp .env.example .env && vi .env
docker compose up -d --build
```

验证：

```bash
docker compose ps
curl -fsS https://jndys.top/api/v1/system/health
```

## 文档

- [DEPLOY.md](./DEPLOY.md)：完整部署、SSL、小程序、发布回滚、备份排障
