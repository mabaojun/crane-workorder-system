# SSL 证书与 Nginx 反向代理配置

> 域名：`jndys.top`  
> 证书来源：腾讯云 SSL 证书  
> 反向代理：`crane-nginx`（Nginx 1.27）直接接管宿主 80/443  
> 腾讯云参考：[SSL 证书 Nginx 服务器 SSL 证书安装部署](https://cloud.tencent.com/document/product/400/35244)

---

## 1. 方案说明

系统使用独立的 `crane-nginx` 容器作为统一入口：

```text
Internet
  ├─ 80  -> 301 -> https://jndys.top
  └─ 443 -> crane-nginx
              ├─ /admin/   后台管理
              ├─ /h5/      移动端 H5
              ├─ /screen/  数据大屏
              └─ /api/     反向代理到 crane-app:7777
```

选择该方案的原因：

- 微信小程序要求 HTTPS，服务器域名必须使用 443；
- 本系统不依赖服务器上其他 Nginx；
- 证书、静态资源、API 代理统一由一个容器管理；
- 与同机其他 Docker 应用通过 `crane-net` 隔离。

前提：

- `jndys.top` A 记录已指向服务器公网 IP；
- 云安全组与 `ufw` 已放行 TCP 80、443；
- 宿主 80/443 空闲（`ss -lntp | grep -E ':(80|443)\b'`）。

---

## 2. 下载腾讯云证书

腾讯云控制台 → SSL 证书 → 我的证书 → 下载：

- 服务器类型选择 **Nginx**；
- 解压后通常得到：

```text
jndys.top_bundle.crt
jndys.top.key
```

> `bundle.crt` 已包含服务器证书与中间证书链，不要只上传域名证书。

---

## 3. 上传并重命名

上传到服务器：

```bash
scp jndys.top_bundle.crt jndys.top.key root@服务器IP:/tmp/
```

放置到固定目录（Nginx 配置只认这两个文件名）：

```bash
mkdir -p /crane/certs
mv /tmp/jndys.top_bundle.crt /crane/certs/fullchain.crt
mv /tmp/jndys.top.key        /crane/certs/privkey.key

chmod 644 /crane/certs/fullchain.crt
chmod 600 /crane/certs/privkey.key
ls -l /crane/certs
```

期望：

```text
fullchain.crt   644
privkey.key     600
```

---

## 4. Docker Compose 端口与证书挂载

`/crane/deploy/docker-compose.yml` 中 `crane-nginx` 关键配置：

```yaml
services:
  crane-nginx:
    image: nginx:1.27-alpine
    container_name: crane-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - /crane/certs:/etc/nginx/certs:ro
      - /crane/www/admin:/usr/share/nginx/html/admin:ro
      - /crane/www/h5:/usr/share/nginx/html/h5:ro
      - /crane/www/screen:/usr/share/nginx/html/screen:ro
    networks:
      - crane-net
```

要点：

- `80/443` 必须映射到宿主；
- 证书目录以只读方式挂载到容器 `/etc/nginx/certs`；
- `nginx.conf` 以只读方式挂载到 `/etc/nginx/conf.d/default.conf`。

---

## 5. Nginx SSL 配置

完整配置见发布包 `deploy/nginx.conf`，核心片段如下：

```nginx
# HTTP：只保留健康检查，其余跳转 HTTPS
server {
    listen 80 default_server;
    server_name jndys.top;

    location = /healthz {
        access_log off;
        return 200 "ok";
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl default_server;
    http2 on;
    server_name jndys.top;

    ssl_certificate     /etc/nginx/certs/fullchain.crt;
    ssl_certificate_key /etc/nginx/certs/privkey.key;
    ssl_session_timeout 5m;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers on;

    root /usr/share/nginx/html;
    index index.html;

    client_max_body_size 60m;

    location /admin/ {
        try_files $uri $uri/ /admin/index.html;
    }

    location /h5/ {
        try_files $uri $uri/ /h5/index.html;
    }

    location /screen/ {
        try_files $uri $uri/ /screen/index.html;
    }

    # SSE 实时推送：必须关闭缓冲并延长读超时
    location = /api/v1/notify/stream {
        proxy_pass http://crane-app:7777;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
    }

    location /api/ {
        proxy_pass http://crane-app:7777;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 10s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }
}
```

> 后端容器监听 7777，`proxy_pass` 必须写 `http://crane-app:7777`，不要写 8080。

---

## 6. 启动与校验

首次部署或证书变更后：

```bash
cd /crane/deploy
docker compose up -d --build
docker compose ps
```

单独校验 Nginx 配置：

```bash
docker exec crane-nginx nginx -t
```

期望：

```text
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

仅重启 Nginx：

```bash
docker compose restart crane-nginx
```

---

## 7. HTTPS 验证

### 7.1 健康检查

```bash
curl -fsS https://jndys.top/api/v1/system/health
```

### 7.2 HTTP 跳转

```bash
curl -I http://jndys.top/admin/
```

期望：

```text
HTTP/1.1 301 Moved Permanently
Location: https://jndys.top/admin/
```

### 7.3 证书信息

```bash
openssl s_client -connect jndys.top:443 -servername jndys.top </dev/null 2>/dev/null \
  | openssl x509 -noout -subject -dates -issuer
```

确认：

- `subject` 包含 `jndys.top`；
- `notAfter` 未过期；
- 浏览器地址栏为安全锁。

---

## 8. 微信小程序 HTTPS 要求

微信公众平台 → 开发管理 → 开发设置 → 服务器域名：

| 类型 | 值 |
|---|---|
| request 合法域名 | `https://jndys.top` |
| uploadFile 合法域名 | `https://jndys.top` |
| downloadFile 合法域名 | `https://jndys.top` |

必须满足：

- HTTPS，不能使用 IP / localhost；
- 使用 443 端口；
- 域名已完成 ICP 备案；
- 证书链完整且未过期。

---

## 9. 证书续期 / 更换

腾讯云证书续期后重新下载 Nginx 版，覆盖原文件即可：

```bash
mv /tmp/jndys.top_bundle.crt /crane/certs/fullchain.crt
mv /tmp/jndys.top.key        /crane/certs/privkey.key
chmod 644 /crane/certs/fullchain.crt
chmod 600 /crane/certs/privkey.key

docker exec crane-nginx nginx -t
docker compose restart crane-nginx
```

> 不需要重建镜像，也不需要重启后端。

---

## 10. 443 被其他应用占用时的方案

如果服务器上已有 Nginx 占用 80/443，本系统不要直接抢占端口，改为：

1. 本系统 Nginx 只绑定宿主 `127.0.0.1:7771`：

```yaml
ports:
  - "127.0.0.1:7771:80"
```

2. 在现有 Nginx 增加一个 server 块，反向代理到本系统：

```nginx
server {
    listen 443 ssl;
    server_name jndys.top;

    ssl_certificate     /path/to/fullchain.crt;
    ssl_certificate_key /path/to/privkey.key;

    location / {
        proxy_pass http://127.0.0.1:7771;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

3. 微信小程序域名仍然配置 `https://jndys.top`，由现有 Nginx 统一对外。

---

## 11. 常见问题

| 现象 | 排查 |
|---|---|
| `cannot load certificate ... fullchain.crt` | 文件不存在或路径不对，检查 `/crane/certs` 与 compose 挂载 |
| `BIO_new_file() failed` | 证书文件名必须是 `fullchain.crt` / `privkey.key`，注意大小写 |
| 浏览器提示证书不受信任 | 确认上传的是 `bundle.crt` 完整链，不是单张域名证书 |
| 443 无法访问 | 云安全组、`ufw`、`ss -lntp` 检查端口 |
| 502 Bad Gateway | 后端未启动或端口不是 7777，`docker logs crane-app` |
| 小程序提示域名不合法 | 三类域名是否都配置、是否为 HTTPS 443、证书是否有效 |
| Nginx 启动失败 | `docker exec crane-nginx nginx -t` 查看具体语法错误 |
| 管理端实时推送断开 | 检查 `/api/v1/notify/stream` 是否关闭 `proxy_buffering` |
