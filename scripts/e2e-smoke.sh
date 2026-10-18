#!/bin/bash
# 端到端 API 冒烟：管理员建档 → 客户提报 → 复核转工单 → 司机接单签到 → 异常上报批复 → 任务结束 → 客户评价
# 用法：bash scripts/e2e-smoke.sh（需后端 8080 已启动、MySQL 已初始化）
set -e
cd "$(dirname "$0")"
source ./env.sh
cd - >/dev/null
BASE="${E2E_BASE:-http://127.0.0.1:8080/api/v1}"
PASS=0; FAIL=0
TS=$(date +%s)
CUST_PHONE="139$((TS % 100000000))"
STAFF_PHONE="138$((TS % 100000000))"

req() { # req <desc> <method> <path> <token> <body-json>
  local desc="$1" method="$2" path="$3" token="$4" body="$5"
  local args=(-s -X "$method" "$BASE$path" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Idempotency-Key: e2e-$TS-$RANDOM$RANDOM")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  local out; out=$(curl "${args[@]}")
  local code; code=$(echo "$out" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code",9999))' 2>/dev/null || echo 9999)
  if [ "$code" = "0" ]; then PASS=$((PASS+1)); echo "PASS  $desc"
  else FAIL=$((FAIL+1)); echo "FAIL  $desc  code=$code"; echo "      $out" | head -c 400; echo; fi
  echo "$out" > /tmp/e2e-last.json
}

jq_get() { python3 -c '
import json,sys
d=json.load(open("/tmp/e2e-last.json"))
cur=d
for k in sys.argv[1:]:
    cur = cur[int(k)] if k.lstrip("-").isdigit() else cur[k]
print(cur)
' "$@"; }

echo "== 1. 管理员登录 =="
req "管理员密码登录" POST /auth/password-login "" '{"username":"13800000000","password":"Admin@123"}'
ADMIN=$(jq_get data accessToken)

echo "== 2. 建档 =="
req "创建客户(含账号)" POST /customer/customers "$ADMIN" "{\"name\":\"E2E测试客户$TS\",\"type\":\"COMPANY\",\"phone\":\"$CUST_PHONE\",\"creditCode\":\"91370100MA3E2E2E2E\",\"contacts\":[],\"createAccount\":true,\"contactName\":\"E2E联系人\",\"contactPhone\":\"$CUST_PHONE\"}"
CID=$(jq_get data id)
req "创建司机(含账号)" POST /staff/staffs "$ADMIN" "{\"name\":\"E2E司机$TS\",\"phone\":\""$STAFF_PHONE"\",\"idCard\":\"370100199001010$((TS%1000))X\",\"position\":\"DRIVER\",\"licenseType\":\"B2\",\"createAccount\":true}"
SID=$(jq_get data id)
STAFF_USER_ID=$(jq_get data userId)
req "创建车辆" POST /staff/vehicles "$ADMIN" "{\"plateNo\":\"鲁A$((TS%10000))E2\",\"model\":\"徐工XCT25\",\"tonnage\":25,\"defaultStaffId\":$SID}"

echo "== 3. 激活账号（模拟完成绑定）=="
CUST_UID=$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM sys_user WHERE phone='$CUST_PHONE' AND deleted=0;" 2>/dev/null | tr -d ' \n')
echo "DEBUG CUST_PHONE=$CUST_PHONE CUST_UID=[$CUST_UID]"
req "激活客户账号" PUT /iam/users/$CUST_UID/status "$ADMIN" '{"status":"ACTIVE"}'
req "激活司机账号" PUT /iam/users/$STAFF_USER_ID/status "$ADMIN" '{"status":"ACTIVE"}'

echo "== 4. 客户提报需求单 =="
req "发送客户验证码" POST /auth/sms-code "" "{\"phone\":\"$CUST_PHONE\",\"scene\":\"LOGIN\"}"
SMSG=$(jq_get data debugCode)
req "客户验证码登录" POST /auth/sms-login "" "{\"phone\":\"$CUST_PHONE\",\"smsCode\":\"$SMSG\"}"
CUST=$(jq_get data accessToken)
req "客户提报需求单" POST /demand/orders "$CUST" "{\"source\":\"CUSTOMER_MP\",\"contactName\":\"E2E联系人\",\"contactPhone\":\"$CUST_PHONE\",\"address\":\"济南市历下区经十路E2E测试点位$TS\",\"longitude\":117.12,\"latitude\":36.71,\"description\":\"吊装钢结构一批\",\"craneTonnage\":25,\"cargoWeight\":8,\"expectedStartAt\":\"2026-12-01T09:00:00+08:00\"}"
DID=$(jq_get data id)

echo "== 5. 复核与转工单 =="
req "管理员复核通过" POST /demand/orders/$DID/review "$ADMIN" '{"approve":true,"reviewRemark":"复核通过"}'
req "需求单转工单" POST /demand/orders/$DID/convert "$ADMIN" "{\"staffId\":$SID,\"vehicleId\":$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_vehicle ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n'),\"planStartAt\":\"2026-12-01T09:00:00+08:00\",\"craneTonnage\":25,\"cargoWeightTon\":8,\"remark\":\"E2E转单\"}"
OID=$(jq_get data convertedOrderId)

echo "== 6. 司机接单与签到 =="
req "发送司机验证码" POST /auth/sms-code "" "{\"phone\":\""$STAFF_PHONE"\",\"scene\":\"LOGIN\"}"
DSMG=$(jq_get data debugCode)
req "司机验证码登录" POST /auth/sms-login "" "{\"phone\":\""$STAFF_PHONE"\",\"smsCode\":\"$DSMG\"}"
DRIVER=$(jq_get data accessToken)
req "司机接单" POST /dispatch/orders/$OID/accept "$DRIVER" '{}'
req "司机签到(定位+照片)" POST /attendance/check-in "$DRIVER" "{\"orderId\":$OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/checkin-$TS.jpg\"]}"

echo "== 7. 异常上报与批复 =="
req "司机异常上报" POST /exception/reports "$DRIVER" "{\"orderId\":$OID,\"type\":\"RAIN\",\"description\":\"突降大雨暂停作业\"}"
req "管理员批复继续施工" POST /exception/reports/$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_exception_record WHERE order_id=$OID AND deleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n')/reply "$ADMIN" '{"approve":true,"instruction":"CONTINUE","opinion":"雨停后恢复"}'

echo "== 8. 任务结束与评价 =="
req "司机任务结束" POST /attendance/task-end "$DRIVER" "{\"orderId\":$OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/end-$TS.jpg\"]}"
req "客户评价" POST /work-order/orders/$OID/review "$CUST" '{"score":5,"content":"服务到位"}'

echo "== 9. 校验终态 =="
req "查询工单详情" GET /work-order/orders/$OID "$ADMIN" ""
STATUS=$(jq_get data status)
if [ "$STATUS" = "COMPLETED" ]; then PASS=$((PASS+1)); echo "PASS  工单终态 COMPLETED"; else FAIL=$((FAIL+1)); echo "FAIL  工单终态=$STATUS"; fi

echo "== 10. 越权用例 =="
code=$(curl -s -X GET "$BASE/work-order/orders/$OID" -H 'X-Client: admin' | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
if [ "$code" = "10001" ]; then PASS=$((PASS+1)); echo "PASS  无令牌访问被拒 10001"; else FAIL=$((FAIL+1)); echo "FAIL  无令牌访问 code=$code"; fi

echo "=============================="
echo "E2E 结果：PASS=$PASS FAIL=$FAIL"
exit $FAIL
