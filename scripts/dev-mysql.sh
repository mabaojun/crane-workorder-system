#!/bin/bash
# 启动本地 MySQL（colima 未运行时自动拉起）
set -e
cd "$(dirname "$0")"
source ./env.sh

if ! docker info >/dev/null 2>&1; then
  echo "Docker 引擎未运行，启动 colima..."
  colima start --vm-type vz --vz-rosetta --cpu 2 --memory 4 --disk 30
fi
cd ../docker/mysql
docker compose up -d
echo "等待 MySQL 就绪..."
for i in $(seq 1 30); do
  if docker exec crane-mysql-dev mysqladmin ping -h localhost -uroot -proot123 >/dev/null 2>&1; then
    echo "MySQL 已就绪（宿主机端口 7772）"
    exit 0
  fi
  sleep 2
done
echo "MySQL 启动超时，请查看容器日志：docker logs crane-mysql-dev"
exit 1
