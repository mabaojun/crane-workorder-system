# 济南诚重吊装业务管理系统

以工单为核心的吊装业务管理系统：PC 后台管理 + 数据大屏 + 移动端 H5/微信小程序（同源）。

## 技术栈

- 后端：Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8（分层单体，按业务域分包）
- 前端：pnpm monorepo —— `apps/admin`（Vue3 + Element Plus）、`apps/screen`（Vue3 + ECharts）、`apps/mobile`（uni-app，H5 + 微信小程序同源）+ `packages/shared`（类型/枚举/接口客户端/设计令牌）
- 部署：Docker Compose（见 `docker/deploy/`，面向云服务器单机部署）

## 本地开发（免污染方案）

所有工具安装于 `~/crane-tools`（JDK/Maven/colima/docker/pnpm shim），不写入系统目录与 shell 配置；清理时 `rm -rf ~/crane-tools` 即完全移除。详见 `~/crane-tools/setup.sh`。

```bash
# 1. 启动本地 MySQL（宿主机端口 33061，账号 crane/crane123，库名 crane_workorder）
bash scripts/dev-mysql.sh

# 2. 启动后端（8080）
bash scripts/dev-backend.sh

# 3. 启动后台管理前端（5173，代理 /api 到 8080）
cd frontend && corepack pnpm install && corepack pnpm dev:admin
```

初始管理员账号（首次启动自动创建，可用环境变量覆盖）：
`13800000000 / Admin@123`

## 系统名称后台可改

系统名称等品牌信息存于 `sys_config`（group=system），四端通过 `GET /api/v1/system/public-config` 动态获取；
后台「系统配置」修改后全端生效。整套系统复制给其他公司时仅需改配置，无需改代码。

## 文档

- 设计文档：`docs/前置设计/`（00 总纲 ~ 11 前端设计手册）
- MVP 计划：`docs/开发计划/2026-10-17-MVP开发实施计划.md`
- 契约基准：文档 01（枚举/命名）、02（数据库）、03（接口）为唯一基准
