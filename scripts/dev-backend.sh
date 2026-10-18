#!/bin/bash
# 启动后端（依赖 dev-mysql.sh 已就绪）
set -e
cd "$(dirname "$0")"
source ./env.sh
cd ../backend
exec mvn spring-boot:run
