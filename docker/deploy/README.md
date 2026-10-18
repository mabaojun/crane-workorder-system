# 云服务器部署手册（docker/deploy）

单机 Docker 部署，四容器：`crane-nginx` / `crane-app` / `crane-mysql` / `crane-backup`。
全部资源 `crane-` 前缀 + 独立网络 `crane-net`，与同机其他应用隔离；数据在 `/opt/crane`。

## 0. 前提（云服务器）

- Linux x86_64/aarch64，已安装 Docker 24+ 与 docker compose 插件
- 已放行端口：宿主 `NGINX_HTTP_PORT`（默认 8088）；如需 MySQL 远程运维再放行 33061（默认仅本机）
- 建议先在服务器核对端口占用：`ss -lntp | grep -E '8088|33061'`

## 1. 本地打包（开发机执行）

```bash
bash docker/deploy/build-images.sh
# 产物：docker/deploy/dist-release/（后端镜像 tar.gz + www/ 三端静态产物）
```

## 2. 上传服务器

```bash
rsync -avz docker/deploy/dist-release/  user@SERVER:/opt/crane/dist-release/
rsync -avz --exclude dist-release docker/deploy/ user@SERVER:/opt/crane/deploy/
ssh user@SERVER "mkdir -p /opt/crane/www /opt/crane/data/files /opt/crane/backup \
  && cp -r /opt/crane/dist-release/www/* /opt/crane/www/"
```

## 3. 配置与启动（服务器上）

```bash
cd /opt/crane/deploy
cp .env.example .env && vi .env   # 必改：MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD / CRANE_JWT_SECRET / CRANE_ADMIN_PASSWORD

docker load < /opt/crane/dist-release/crane-backend-*.tar.gz
docker compose up -d
docker compose ps                 # 四个容器应为 running / healthy
curl -s http://127.0.0.1:${NGINX_HTTP_PORT}/api/v1/system/health
# 期望 {"code":0,...status:UP...}
```

访问入口（服务器 IP 或域名）：

| 通道 | 地址 |
|---|---|
| PC 后台管理 | `http://IP:8088/admin/`（初始账号 .env 中的 CRANE_ADMIN_PHONE / CRANE_ADMIN_PASSWORD） |
| 移动端 H5 | `http://IP:8088/h5/`（手机号 + 验证码登录；短信通道二期前为 MOCK 模式） |
| 数据大屏 | `http://IP:8088/screen/`（二期） |

首次启动自动建库（31 表 + 字典/配置种子）与初始管理员账号。

## 4. 系统名称更换（复制给其他公司）

登录 PC 后台 →「系统配置」→ 修改 `system.name` / `system.short_name` / `system.company_name` 等 →
保存后四端刷新即生效，无需改代码或重新构建。

## 5. 发布新版本（回滚）

```bash
# 发布
bash docker/deploy/build-images.sh               # 本地
rsync dist-release/ user@SERVER:/opt/crane/dist-release/   # 服务器
cd /opt/crane/deploy && docker load < /opt/crane/dist-release/crane-backend-<新版本>.tar.gz
APP_VERSION=<新版本> docker compose up -d crane-app

# 回滚：回到上一个版本号即可
APP_VERSION=<旧版本> docker compose up -d crane-app
# 极端情况回滚数据库：gunzip < /opt/crane/backup/db/crane_workorder_<日期>.sql.gz | \
#   docker exec -i crane-mysql mysql -ucrane -p crane_workorder
```

## 6. 数据与备份

- 数据库数据：命名卷 `crane-mysql-data`；上传文件：`/opt/crane/data/files`
- `crane-backup` 每日 02:30 逻辑备份到 `/opt/crane/backup`（默认保留 7 天，`.env` 可调）
- 手动立即备份：`docker exec crane-backup bash /scripts/backup-loop.sh`（或重启该容器触发首次备份）

## 7. 日常运维速查

```bash
docker compose ps                          # 容器状态
docker logs -f crane-app --tail 200        # 应用日志
docker exec crane-mysql mysql -ucrane -p crane_workorder -e "SHOW TABLES;"
docker compose restart crane-app           # 重启应用
docker compose down                        # 停止（数据卷保留）
```

## 8. 完全清理（同机移除本系统）

```bash
cd /opt/crane/deploy && docker compose down -v   # 删容器+网络+卷（数据库数据一并删除，先确认备份）
rm -rf /opt/crane                                 # 删数据/备份/部署文件
```
