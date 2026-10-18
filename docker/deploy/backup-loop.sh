#!/bin/bash
# 数据库逻辑备份循环：每日 02:30 备份 + 保留策略 + 上传文件打包
set -eu
BACKUP_DIR=/backup
MYSQL_PWD="$MYSQL_PASSWORD"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"

run_backup() {
  local ts; ts=$(date +%Y%m%d_%H%M%S)
  mkdir -p "$BACKUP_DIR/db" "$BACKUP_DIR/files"
  echo "[$(date '+%F %T')] 开始数据库备份 crane_workorder_$ts.sql.gz"
  mysqldump -h crane-mysql -u"$MYSQL_USERNAME" -p"$MYSQL_PWD" \
    --single-transaction --routines --triggers crane_workorder \
    | gzip > "$BACKUP_DIR/db/crane_workorder_$ts.sql.gz"
  echo "[$(date '+%F %T')] 数据库备份完成：$(du -h "$BACKUP_DIR/db/crane_workorder_$ts.sql.gz" | cut -f1)"
  # 上传文件打包（照片/附件卷）
  if [ -d /data/files ] && [ -n "$(ls -A /data/files 2>/dev/null)" ]; then
    tar -czf "$BACKUP_DIR/files/files_$ts.tar.gz" -C /data/files . 2>/dev/null || true
  fi
}

prune_old() {
  find "$BACKUP_DIR/db" -name "*.sql.gz" -mtime +"$KEEP_DAYS" -delete 2>/dev/null || true
  find "$BACKUP_DIR/files" -name "*.tar.gz" -mtime +"$KEEP_DAYS" -delete 2>/dev/null || true
}

# 启动即备份一次（容器首次拉起兜底），随后每日 02:30 执行
sleep 20 && run_backup && prune_old
while true; do
  now=$(date +%H%M)
  if [ "$now" = "0230" ]; then
    run_backup && prune_old
    sleep 60
  fi
  sleep 30
done
