#!/bin/bash
# 清理本地开发库中的 E2E 测试数据：
# - 备份当前库到 /tmp
# - 清空全部业务表、AI 记录、文件/幂等/验证码等运行数据
# - 仅保留管理员账号 13800000000，以及角色、权限、字典、系统配置
# 用法：bash scripts/clean-local-e2e.sh --yes
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh
cd ..

CONTAINER="${E2E_MYSQL_CONTAINER:-crane-mysql-dev}"
DB="${E2E_MYSQL_DB:-crane_workorder}"
DB_USER="${E2E_MYSQL_USER:-crane}"
DB_PW="${E2E_MYSQL_PW:-crane123}"
ADMIN_PHONE="${E2E_KEEP_ADMIN_PHONE:-13800000000}"

if [ "${1:-}" != "--yes" ]; then
  echo "将清空本地库 $DB 的全部业务数据（保留管理员 $ADMIN_PHONE / 字典 / API 配置）。"
  read -r -p "确认执行请输入 yes：" ans
  [ "$ans" = "yes" ] || { echo "已取消"; exit 1; }
fi

BACKUP="/tmp/crane-local-backup-$(date +%Y%m%d_%H%M%S).sql.gz"
echo "== 备份到 $BACKUP =="
docker exec "$CONTAINER" mysqldump --default-character-set=utf8mb4 -u"$DB_USER" -p"$DB_PW" \
  --single-transaction --routines --triggers --no-tablespaces "$DB" | gzip > "$BACKUP"
echo "备份完成：$(du -h "$BACKUP" | cut -f1)"

echo "== 清理业务数据 =="
docker exec -i "$CONTAINER" mysql --default-character-set=utf8mb4 -u"$DB_USER" -p"$DB_PW" "$DB" <<SQL
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE ai_alarm_record;
TRUNCATE TABLE ai_briefing;
TRUNCATE TABLE ai_service_config;
TRUNCATE TABLE biz_approval_record;
TRUNCATE TABLE biz_attendance;
TRUNCATE TABLE biz_common_place;
TRUNCATE TABLE biz_conflict_alert;
TRUNCATE TABLE biz_customer;
TRUNCATE TABLE biz_customer_contact;
TRUNCATE TABLE biz_customer_review;
TRUNCATE TABLE biz_daily_checkout;
TRUNCATE TABLE biz_demand_order;
TRUNCATE TABLE biz_exception_record;
TRUNCATE TABLE biz_notification_record;
TRUNCATE TABLE biz_order_accept_log;
TRUNCATE TABLE biz_order_change_request;
TRUNCATE TABLE biz_overtime_record;
TRUNCATE TABLE biz_performance_stat;
TRUNCATE TABLE biz_staff;
TRUNCATE TABLE biz_vehicle;
TRUNCATE TABLE biz_vehicle_binding;
TRUNCATE TABLE biz_vehicle_status_log;
TRUNCATE TABLE biz_work_hour_stat;
TRUNCATE TABLE biz_work_order;
TRUNCATE TABLE biz_work_order_status_log;
TRUNCATE TABLE sys_file;
TRUNCATE TABLE sys_idempotency;
TRUNCATE TABLE sys_sms_code;
DELETE FROM sys_user WHERE phone <> '$ADMIN_PHONE';
SET FOREIGN_KEY_CHECKS = 1;
SQL

echo "== 清理后统计 =="
docker exec "$CONTAINER" mysql --default-character-set=utf8mb4 -u"$DB_USER" -p"$DB_PW" "$DB" -e "
SELECT 'sys_user' t, COUNT(*) c FROM sys_user
UNION ALL SELECT 'biz_staff', COUNT(*) FROM biz_staff
UNION ALL SELECT 'biz_vehicle', COUNT(*) FROM biz_vehicle
UNION ALL SELECT 'biz_customer', COUNT(*) FROM biz_customer
UNION ALL SELECT 'biz_demand_order', COUNT(*) FROM biz_demand_order
UNION ALL SELECT 'biz_work_order', COUNT(*) FROM biz_work_order
UNION ALL SELECT 'sys_config', COUNT(*) FROM sys_config
UNION ALL SELECT 'sys_dict', COUNT(*) FROM sys_dict;"
echo "DONE。数据库备份：$BACKUP"
