#!/bin/bash
# 端到端 API 冒烟：管理员建档 → 客户提报 → 复核转工单 → 司机接单签到 → 异常上报批复 → 任务结束 → 客户评价 → 司机退单 → 常用施工地点
# 用法：bash scripts/e2e-smoke.sh（需后端 7777 已启动、MySQL 已初始化）
set -e
cd "$(dirname "$0")"
source ./env.sh
cd - >/dev/null
BASE="${E2E_BASE:-http://127.0.0.1:7777/api/v1}"
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
req "创建司机(含账号)" POST /staff/staffs "$ADMIN" "{\"name\":\"E2E司机$TS\",\"phone\":\""$STAFF_PHONE"\",\"idCard\":\"370100199001010$((TS%1000))X\",\"position\":\"DRIVER\",\"licenseType\":\"B2\",\"createAccount\":true,\"initialPassword\":\"E2e@12345\"}"
SID=$(jq_get data id)
STAFF_USER_ID=$(jq_get data userId)
req "创建车辆" POST /staff/vehicles "$ADMIN" "{\"plateNo\":\"鲁A$((TS%10000))E2\",\"model\":\"徐工XCT25\",\"tonnage\":25,\"defaultStaffId\":$SID}"

echo "== 3. 激活账号（模拟完成绑定）=="
CUST_UID=$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM sys_user WHERE phone='$CUST_PHONE' AND deleted=0;" 2>/dev/null | tr -d ' \n')
echo "DEBUG CUST_PHONE=$CUST_PHONE CUST_UID=[$CUST_UID]"
req "激活客户账号" PUT /iam/users/$CUST_UID/status "$ADMIN" '{"status":"ACTIVE"}'
req "激活司机账号" PUT /iam/users/$STAFF_USER_ID/status "$ADMIN" '{"status":"ACTIVE"}'

echo "== 4. 客户提报需求单 =="
req "发送客户验证码" POST /auth/sms-code "" "{\"phone\":\"$CUST_PHONE\",\"scene\":\"LOGIN\"}"
SMSG=$(jq_get data debugCode)
req "客户验证码登录" POST /auth/sms-login "" "{\"phone\":\"$CUST_PHONE\",\"smsCode\":\"$SMSG\"}"
CUST=$(jq_get data accessToken)
req "客户提报需求单" POST /demand/orders "$CUST" "{\"source\":\"CUSTOMER_MP\",\"contactName\":\"E2E联系人\",\"contactPhone\":\"$CUST_PHONE\",\"address\":\"济南市历下区经十路E2E测试点位$TS\",\"longitude\":117.12,\"latitude\":36.71,\"description\":\"吊装钢结构一批\",\"craneTonnage\":25,\"cargoWeight\":8,\"estimatedDays\":1.5,\"expectedStartAt\":\"2026-12-01T09:00:00+08:00\"}"
DID=$(jq_get data id)

echo "== 5. 复核与转工单 =="
req "管理员复核通过" POST /demand/orders/$DID/review "$ADMIN" '{"approve":true,"reviewRemark":"复核通过"}'
req "需求单转工单" POST /demand/orders/$DID/convert "$ADMIN" "{\"staffId\":$SID,\"vehicleId\":$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_vehicle ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n'),\"planStartAt\":\"2026-12-01T09:00:00+08:00\",\"craneTonnage\":25,\"cargoWeightTon\":8,\"estimatedDays\":2,\"remark\":\"E2E转单\"}"
OID=$(jq_get data convertedOrderId)
EST_DAYS=$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT estimated_days FROM biz_work_order WHERE id=$OID;" 2>/dev/null | tr -d ' \n')
if [ "$EST_DAYS" = "2.0" ]; then PASS=$((PASS+1)); echo "PASS  预计使用时长转单落库(2天)"; else FAIL=$((FAIL+1)); echo "FAIL  预计使用时长落库=$EST_DAYS 期望 2.0"; fi

echo "== 6. 司机接单与签到 =="
req "发送司机验证码" POST /auth/sms-code "" "{\"phone\":\""$STAFF_PHONE"\",\"scene\":\"LOGIN\"}"
DSMG=$(jq_get data debugCode)
req "司机验证码登录" POST /auth/sms-login "" "{\"phone\":\""$STAFF_PHONE"\",\"smsCode\":\"$DSMG\"}"
DRIVER=$(jq_get data accessToken)
req "司机接单" POST /dispatch/orders/$OID/accept "$DRIVER" '{}'
req "司机签到(定位+照片)" POST /attendance/check-in "$DRIVER" "{\"orderId\":$OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/checkin-$TS.jpg\"]}"

echo "== 7. 异常上报与批复 =="
req "司机异常上报" POST /exception/reports "$DRIVER" "{\"orderId\":$OID,\"type\":\"RAIN\",\"description\":\"突降大雨暂停作业\"}"
req "管理员批复继续施工" POST /exception/reports/$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_exception_record WHERE order_id=$OID AND deleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n')/reply "$ADMIN" '{"approve":true,"instruction":"CONTINUE","opinion":"雨停后恢复"}'

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

echo "== 11. 地图选点与密码登录 =="
# 无定位无照片签到（定位/照片强制开关默认关闭，测试阶段不限制）
req "创建开关验证单" POST /work-order/orders "$ADMIN" "{\"taskName\":\"开关验证单\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID,\"vehicleId\":$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_vehicle WHERE status='IDLE' AND deleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n'),\"planStartAt\":\"2026-12-08T09:00:00+08:00\",\"address\":\"开关测试点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}"
SOID=$(jq_get data orderId)
req "开关单司机接单" POST /dispatch/orders/$SOID/accept "$DRIVER" "{}"
req "无定位无照片签到(开关关闭)" POST /attendance/check-in "$DRIVER" "{\"orderId\":$SOID}"
# 逆地理/搜索走真实高德（map.mock=false 时）或模拟数据；外部服务不可用（50004）不视为系统缺陷，跳过不计数
AMAP_OUT=$(curl -s "$BASE/system/amap/regeo?lng=117.12&lat=36.71" -H "Authorization: Bearer $ADMIN" -H 'X-Client: admin')
AMAP_CODE=$(echo "$AMAP_OUT" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null || echo 9999)
if [ "$AMAP_CODE" = "0" ]; then
  addr=$(echo "$AMAP_OUT" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"].get("formattedAddress",""))')
  if [ -n "$addr" ]; then PASS=$((PASS+1)); echo "PASS  逆地理回填地址: $addr"; else FAIL=$((FAIL+1)); echo "FAIL  逆地理无地址"; fi
else
  echo "SKIP  逆地理暂不可用（高德 Key 配置/配额问题，不影响系统能力）"
fi
AMAP_CODE=$(curl -s "$BASE/system/amap/place/text?keywords=%E5%81%9C%E8%BD%A6%E5%9C%BA&lng=117.12&lat=36.71" -H "Authorization: Bearer $ADMIN" -H 'X-Client: admin' | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))' 2>/dev/null || echo 9999)
if [ "$AMAP_CODE" = "0" ]; then PASS=$((PASS+1)); echo "PASS  关键词搜索可用"; else echo "SKIP  关键词搜索暂不可用（同上）"; fi
PWPHONE="$STAFF_PHONE"
code=$(curl -s -X POST "$BASE/auth/password-login" -H 'Content-Type: application/json' -H 'X-Client: mp' -d "{\"username\":\"$PWPHONE\",\"password\":\"E2e@12345\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
if [ "$code" = "0" ]; then PASS=$((PASS+1)); echo "PASS  司机密码登录(mp)"; else FAIL=$((FAIL+1)); echo "FAIL  司机密码登录 code=$code"; fi
# 开关实时生效：关闭 → mp 被拒 30015 → 恢复
curl -s -X PUT "$BASE/system/configs/auth.password_login_enabled" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"configValue":"false"}' > /dev/null
code=$(curl -s -X POST "$BASE/auth/password-login" -H 'Content-Type: application/json' -H 'X-Client: mp' -d "{\"username\":\"$PWPHONE\",\"password\":\"E2e@12345\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
if [ "$code" = "30015" ]; then PASS=$((PASS+1)); echo "PASS  开关关闭后 mp 密码登录被拒 30015"; else FAIL=$((FAIL+1)); echo "FAIL  开关未生效 code=$code"; fi
curl -s -X PUT "$BASE/system/configs/auth.password_login_enabled" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"configValue":"true"}' > /dev/null
code=$(curl -s -X POST "$BASE/auth/password-login" -H 'Content-Type: application/json' -H 'X-Client: mp' -d "{\"username\":\"$PWPHONE\",\"password\":\"E2e@12345\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')
if [ "$code" = "0" ]; then PASS=$((PASS+1)); echo "PASS  开关开启后恢复（实时生效无重启）"; else FAIL=$((FAIL+1)); echo "FAIL  恢复失败 code=$code"; fi

echo "== 12. 司机退单（接单后状态回退并通知管理端）=="
# 独立退单司机：避免与前面链路的司机占用状态（30001）互相干扰
STAFF2_PHONE="137$((TS % 100000000))"
req "创建退单司机(含账号)" POST /staff/staffs "$ADMIN" "{\"name\":\"E2E退单司机$TS\",\"phone\":\"$STAFF2_PHONE\",\"idCard\":\"370100199001012$((TS%1000))X\",\"position\":\"DRIVER\",\"licenseType\":\"B2\",\"createAccount\":true,\"initialPassword\":\"E2e@12345\"}"
SID2=$(jq_get data id)
STAFF2_USER_ID=$(jq_get data userId)
req "激活退单司机账号" PUT /iam/users/$STAFF2_USER_ID/status "$ADMIN" '{"status":"ACTIVE"}'
req "发送退单司机验证码" POST /auth/sms-code "" "{\"phone\":\"$STAFF2_PHONE\",\"scene\":\"LOGIN\"}"
WMSG=$(jq_get data debugCode)
req "退单司机验证码登录" POST /auth/sms-login "" "{\"phone\":\"$STAFF2_PHONE\",\"smsCode\":\"$WMSG\"}"
DRIVER2=$(jq_get data accessToken)
# 退单专用车辆：历史轮次遗留车状态不可控（多为 BUSY），自建确保 IDLE 可派
req "创建退单专用车辆" POST /staff/vehicles "$ADMIN" "{\"plateNo\":\"鲁W$((TS%10000))E2\",\"model\":\"徐工XCT25\",\"type\":\"CRANE\",\"tonnage\":25}"
WVID=$(jq_get data id)
req "创建退单验证单" POST /work-order/orders "$ADMIN" "{\"taskName\":\"退单验证单\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID2,\"vehicleId\":$WVID,\"planStartAt\":\"2026-12-09T09:00:00+08:00\",\"address\":\"退单测试点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}"
WOID=$(jq_get data orderId)
req "退单验证单司机接单" POST /dispatch/orders/$WOID/accept "$DRIVER2" "{}"
req "司机接单后发起退单" POST /dispatch/orders/$WOID/withdraw "$DRIVER2" '{"reasonType":"ABILITY_MISMATCH"}'
req "管理员查询退单后工单" GET /work-order/orders/$WOID "$ADMIN" ""
WSTATUS=$(jq_get data status)
if [ "$WSTATUS" = "PENDING_ACCEPT" ]; then PASS=$((PASS+1)); echo "PASS  退单后工单回到 PENDING_ACCEPT"; else FAIL=$((FAIL+1)); echo "FAIL  退单后工单状态=$WSTATUS"; fi

echo "== 13. 常用施工地点（实时落库增删）=="
req "管理员新增常用地点" POST /place/common-places "$ADMIN" "{\"placeName\":\"E2E测试地点$TS\",\"longitude\":117.216,\"latitude\":36.721}"
PID=$(jq_get data id)
req "客户读取常用地点列表" GET /place/common-places "$CUST" ""
PRESENT=$(PLACENAME="E2E测试地点$TS" python3 -c '
import json,os
d=json.load(open("/tmp/e2e-last.json"))
name=os.environ.get("PLACENAME","")
print("YES" if any(p.get("placeName")==name for p in (d.get("data") or [])) else "NO")
')
if [ "$PRESENT" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  新增地点已实时落库可查询"; else FAIL=$((FAIL+1)); echo "FAIL  列表未包含新增地点"; fi
COORDS_OK=$(PLACENAME="E2E测试地点$TS" python3 -c '
import json,os
d=json.load(open("/tmp/e2e-last.json"))
name=os.environ.get("PLACENAME","")
ps=[p for p in (d.get("data") or []) if p.get("placeName")==name]
print("YES" if ps and ps[0].get("longitude") is not None and ps[0].get("latitude") is not None else "NO")
')
if [ "$COORDS_OK" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  地点经纬度随新增实时落库"; else FAIL=$((FAIL+1)); echo "FAIL  地点经纬度缺失"; fi
req "关键字模糊过滤查询" GET "/place/common-places?keyword=$TS" "$CUST" ""
PRESENT=$(PLACENAME="E2E测试地点$TS" python3 -c '
import json,os
d=json.load(open("/tmp/e2e-last.json"))
name=os.environ.get("PLACENAME","")
print("YES" if any(p.get("placeName")==name for p in (d.get("data") or [])) else "NO")
')
if [ "$PRESENT" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  关键字过滤命中新增地点"; else FAIL=$((FAIL+1)); echo "FAIL  关键字过滤未命中"; fi
req "管理员删除常用地点" DELETE /place/common-places/$PID "$ADMIN" ""
req "删除后再次读取列表" GET /place/common-places "$CUST" ""
PRESENT=$(PLACENAME="E2E测试地点$TS" python3 -c '
import json,os
d=json.load(open("/tmp/e2e-last.json"))
name=os.environ.get("PLACENAME","")
print("YES" if any(p.get("placeName")==name for p in (d.get("data") or [])) else "NO")
')
if [ "$PRESENT" = "NO" ]; then PASS=$((PASS+1)); echo "PASS  删除后列表已不含该地点"; else FAIL=$((FAIL+1)); echo "FAIL  删除后地点仍存在"; fi

echo "== 14. 暂停恢复链路（异常上报→申请继续→批准→暂停签退/终止）=="
req "司机异常上报(暂停)" POST /exception/reports "$DRIVER" "{\"orderId\":$SOID,\"type\":\"RAIN\",\"description\":\"突降大雨暂停作业\"}"
req "司机申请继续施工" POST /work-order/orders/$SOID/resume-request "$DRIVER" ""
req "管理员批准继续" POST /work-order/orders/$SOID/resume "$ADMIN" ""
req "查询恢复后工单" GET /work-order/orders/$SOID "$ADMIN" ""
RSTATUS=$(jq_get data status)
if [ "$RSTATUS" = "IN_PROGRESS" ]; then PASS=$((PASS+1)); echo "PASS  批准后工单恢复 IN_PROGRESS"; else FAIL=$((FAIL+1)); echo "FAIL  恢复后工单状态=$RSTATUS"; fi
req "司机再次异常上报(暂停)" POST /exception/reports "$DRIVER" "{\"orderId\":$SOID,\"type\":\"CUSTOM\",\"typeLabel\":\"设备故障\",\"description\":\"设备临时故障\"}"
req "暂停态签退" POST /attendance/daily-checkout "$DRIVER" "{\"orderId\":$SOID,\"longitude\":117.12,\"latitude\":36.71,\"isLeftSite\":false,\"isReturned\":false}"
req "暂停态终止工单" POST /attendance/task-end "$DRIVER" "{\"orderId\":$SOID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/suspend-end-$TS.jpg\"]}"
req "查询终止后工单" GET /work-order/orders/$SOID "$ADMIN" ""
FSTATUS=$(jq_get data status)
if [ "$FSTATUS" = "COMPLETED" ]; then PASS=$((PASS+1)); echo "PASS  暂停态终止后工单 COMPLETED"; else FAIL=$((FAIL+1)); echo "FAIL  终止后工单状态=$FSTATUS"; fi

echo "== 15. 任务名自动生成与货物重量选填 =="
req "创建自动命名单(无任务名/无货物重量)" POST /work-order/orders "$ADMIN" "{\"taskName\":\"\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID,\"vehicleId\":$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_vehicle WHERE status='IDLE' AND deleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n'),\"planStartAt\":\"2026-12-10T09:00:00+08:00\",\"address\":\"自动命名测试点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"craneTonnage\":25}"
AOID=$(jq_get data orderId)
req "查询自动命名工单" GET /work-order/orders/$AOID "$ADMIN" ""
TASK=$(jq_get data taskName)
if [[ "$TASK" == *"E2E测试客户"* && "$TASK" == *"吨"* && "$TASK" == *"E2E司机"* ]]; then PASS=$((PASS+1)); echo "PASS  任务名自动生成: $TASK"; else FAIL=$((FAIL+1)); echo "FAIL  自动任务名=$TASK"; fi

echo "== 16. 客户常用地点（本客户历史工单地点）=="
req "客户读取我的常用地点" GET /place/my-places "$CUST" ""
MYPLACE=$(PLACENAME="济南市历下区经十路E2E测试点位$TS" python3 -c '
import json,os
d=json.load(open("/tmp/e2e-last.json"))
name=os.environ.get("PLACENAME","")
ps=[p for p in (d.get("data") or []) if p.get("placeName")==name]
print("YES" if ps and ps[0].get("longitude") is not None else "NO")
')
if [ "$MYPLACE" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  我的常用地点含本客户工单地址（含经纬度）"; else FAIL=$((FAIL+1)); echo "FAIL  我的常用地点未含本客户工单地址"; fi
code=$(curl -s "$BASE/place/my-places" -H 'X-Client: admin' -H "Authorization: Bearer $ADMIN" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$code" = "0" ]; then PASS=$((PASS+1)); echo "PASS  管理员调用返回空列表不报错"; else FAIL=$((FAIL+1)); echo "FAIL  管理员调用 code=$code"; fi

echo "== 17. 举升车与状态手动维护（第十一轮）=="
# 建司机 + 举升车（举升高度 20 米），验证类型规格互斥与状态手动变更链路
STAFF2_PHONE="136$((TS % 100000000))"
req "创建举升车司机" POST /staff/staffs "$ADMIN" "{\"name\":\"E2E举升司机$TS\",\"phone\":\"$STAFF2_PHONE\",\"idCard\":\"370100199001020$((TS%1000))X\",\"position\":\"DRIVER\",\"createAccount\":true,\"initialPassword\":\"E2e@12345\"}"
SID2=$(jq_get data id)
SUID2=$(jq_get data userId)
docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -e "UPDATE sys_user SET status='ACTIVE' WHERE id=$SUID2;" >/dev/null 2>&1
req "创建举升车(高度20米)" POST /staff/vehicles "$ADMIN" "{\"plateNo\":\"鲁L$((TS%10000))E9\",\"model\":\"徐工XGS5060\",\"type\":\"LIFT\",\"liftHeight\":20,\"defaultStaffId\":$SID2}"
LVID=$(jq_get data id)
LIFT_OK=$(python3 -c '
import json
d=json.load(open("/tmp/e2e-last.json"))["data"]
print("YES" if d.get("type")=="LIFT" and d.get("liftHeight")==20 and d.get("tonnage") is None else "NO")
')
if [ "$LIFT_OK" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  举升车规格回填(高度20/吨位空)"; else FAIL=$((FAIL+1)); echo "FAIL  举升车规格回填"; fi
# req 只认 code=0 为 PASS，负向用例单独判定
NEG1=$(curl -s -X POST "$BASE/staff/vehicles" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: e2e-$TS-neg1" -d "{\"plateNo\":\"鲁L$((TS%10000))E8\",\"type\":\"LIFT\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$NEG1" = "20001" ]; then PASS=$((PASS+1)); echo "PASS  举升车缺高度返回20001"; else FAIL=$((FAIL+1)); echo "FAIL  举升车缺高度 code=$NEG1"; fi
NEG2=$(curl -s -X PATCH "$BASE/staff/vehicles/$LVID/status" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: e2e-$TS-neg2" -d '{"status":"BUSY"}' | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$NEG2" = "40004" ]; then PASS=$((PASS+1)); echo "PASS  管理员手动设作业中返回40004"; else FAIL=$((FAIL+1)); echo "FAIL  手动设作业中 code=$NEG2"; fi
req "管理员改维保" PATCH /staff/vehicles/$LVID/status "$ADMIN" '{"status":"MAINTENANCE"}'
# 司机常用设备与状态切换
req "发送举升车司机验证码" POST /auth/sms-code "" "{\"phone\":\"$STAFF2_PHONE\",\"scene\":\"LOGIN\"}"
DSMG2=$(jq_get data debugCode)
req "举升车司机登录" POST /auth/sms-login "" "{\"phone\":\"$STAFF2_PHONE\",\"smsCode\":\"$DSMG2\"}"
DRIVER2=$(jq_get data accessToken)
req "司机查常用设备" GET /staff/my-vehicles "$DRIVER2" ""
MYV=$(python3 -c '
import json
d=json.load(open("/tmp/e2e-last.json"))
ids=[v["id"] for v in d.get("data") or []]
print("YES" if int('"$LVID"') in ids else "NO")
')
if [ "$MYV" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  常用设备含举升车"; else FAIL=$((FAIL+1)); echo "FAIL  常用设备未含举升车"; fi
req "司机改第三方外出" POST /staff/vehicles/$LVID/driver-status "$DRIVER2" '{"status":"BUSY_THIRD"}'
NEG3=$(curl -s -X POST "$BASE/staff/vehicles/$LVID/driver-status" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Authorization: Bearer $DRIVER2" -H "Idempotency-Key: e2e-$TS-neg3" -d '{"status":"DISABLED"}' | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$NEG3" = "20008" ]; then PASS=$((PASS+1)); echo "PASS  司机改停用返回20008"; else FAIL=$((FAIL+1)); echo "FAIL  司机改停用 code=$NEG3"; fi
# 接单超时报警：时限临时改 0，把一条已完成工单置为超时 PENDING_ACCEPT，验证 NOT_ACCEPTED 后还原
req "接单底线临时改0分钟" PUT /system/configs/order.accept_lead_minutes "$ADMIN" '{"configValue":"0"}'
ALOID=$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_work_order WHERE status='COMPLETED' AND deleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n')
docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -e "UPDATE biz_work_order SET status='PENDING_ACCEPT', accepted_at=NULL, accept_deadline_at=NOW() - INTERVAL 10 MINUTE WHERE id=$ALOID;" >/dev/null 2>&1
req "查询报警中心" GET /stat/alarms "$ADMIN" ""
HASNA=$(python3 -c '
import json
d=json.load(open("/tmp/e2e-last.json"))
items=[a for a in d["data"]["list"] if a["type"]=="NOT_ACCEPTED" and a.get("orderId")==int('"$ALOID"')]
print("YES" if items else "NO")
')
if [ "$HASNA" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  报警含超时未接单(NOT_ACCEPTED)"; else FAIL=$((FAIL+1)); echo "FAIL  报警未含NOT_ACCEPTED"; fi
docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -e "UPDATE biz_work_order SET status='COMPLETED', accept_deadline_at=NULL WHERE id=$ALOID;" >/dev/null 2>&1
req "接单底线还原120分钟" PUT /system/configs/order.accept_lead_minutes "$ADMIN" '{"configValue":"120"}'

# BUSY 车时段校验：第 12 节退单后 WVID 车保持 BUSY（占用退单单，预计 2026-12-09T09:00 开工）
# 同一 6 小时段内建单应拒绝（40002 时间冲突），时段充裕应放行（当前忙不代表派单时刻忙）
CONFLICT_MSG=$(curl -s -X POST "$BASE/work-order/orders" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: e2e-$TS-vconf" -d "{\"taskName\":\"时段冲突验证\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID2,\"vehicleId\":$WVID,\"planStartAt\":\"2026-12-09T12:00:00+08:00\",\"address\":\"冲突验证点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}")
CONFLICT_CODE=$(echo "$CONFLICT_MSG" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$CONFLICT_CODE" = "40002" ] && echo "$CONFLICT_MSG" | grep -q "时间冲突"; then PASS=$((PASS+1)); echo "PASS  BUSY车6小时段内建单被拒(40002+时间冲突)"; else FAIL=$((FAIL+1)); echo "FAIL  BUSY车时段冲突 code=$CONFLICT_MSG"; fi
req "BUSY车时段充裕建单放行" POST /work-order/orders "$ADMIN" "{\"taskName\":\"时段充裕验证\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID2,\"vehicleId\":$WVID,\"planStartAt\":\"2026-12-09T20:00:00+08:00\",\"address\":\"充裕验证点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}"
BUSY_OK_OID=$(jq_get data orderId)
docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -e "UPDATE biz_work_order SET status='CANCELLED' WHERE id=$BUSY_OK_OID; UPDATE biz_vehicle SET status='IDLE', current_order_id=NULL WHERE id=$WVID;" >/dev/null 2>&1

docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -e "UPDATE biz_vehicle SET deleted=1 WHERE id=$LVID;" >/dev/null 2>&1

echo "== 18. 突发插单闭环（同班组借调：A 进行中 → 派 B → 暂停 A → 执行 B → 回签 A）=="
# 前置：WVID 车已被置回 IDLE，DRIVER2 为空闲司机
req "插单-建任务A" POST /work-order/orders "$ADMIN" "{\"taskName\":\"插单任务A\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID2,\"vehicleId\":$WVID,\"planStartAt\":\"2026-12-20T09:00:00+08:00\",\"address\":\"插单A点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}"
A_OID=$(jq_get data orderId)
req "插单-司机接A" POST /dispatch/orders/$A_OID/accept "$DRIVER2" '{}'
req "插单-司机签A(进行中)" POST /attendance/check-in "$DRIVER2" "{\"orderId\":$A_OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/ins-a-$TS.jpg\"]}"
# 同车同司机建 B（与 A 同一 6 小时段）：同班组插单豁免应放行
req "插单-同班组建任务B豁免放行" POST /work-order/orders "$ADMIN" "{\"taskName\":\"插单任务B\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID2,\"vehicleId\":$WVID,\"planStartAt\":\"2026-12-20T11:00:00+08:00\",\"address\":\"插单B点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}"
B_OID=$(jq_get data orderId)
req "插单-司机接B" POST /dispatch/orders/$B_OID/accept "$DRIVER2" '{}'
# 未暂停 A 直接签 B：司机互斥 30001（司机先取舍）
MUTEX_CODE=$(curl -s -X POST "$BASE/attendance/check-in" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Authorization: Bearer $DRIVER2" -H "Idempotency-Key: e2e-$TS-insm" -d "{\"orderId\":$B_OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/ins-b0-$TS.jpg\"]}" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$MUTEX_CODE" = "30001" ]; then PASS=$((PASS+1)); echo "PASS  未暂停A签B被互斥拦截(30001)"; else FAIL=$((FAIL+1)); echo "FAIL  未暂停A签B code=$MUTEX_CODE"; fi
# 司机选择执行 B：暂停（临时签退）A
req "插单-司机暂停A(临时签退)" POST /exception/reports "$DRIVER2" "{\"orderId\":$A_OID,\"type\":\"RAIN\",\"description\":\"突发任务插单，暂停当前作业\"}"
req "插单-司机签B(执行突发任务)" POST /attendance/check-in "$DRIVER2" "{\"orderId\":$B_OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/ins-b1-$TS.jpg\"]}"
req "插单-任务B结束(车释放)" POST /attendance/task-end "$DRIVER2" "{\"orderId\":$B_OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/ins-be-$TS.jpg\"]}"
# 管理员批准 A 恢复：车辆若已释放应重占
req "插单-管理员批准A恢复" POST /work-order/orders/$A_OID/resume "$ADMIN" ""
RESUME_CAR=$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT CONCAT(status,'-',IFNULL(current_order_id,0)) FROM biz_vehicle WHERE id=$WVID;" 2>/dev/null | tr -d ' \n')
if [ "$RESUME_CAR" = "BUSY-$A_OID" ]; then PASS=$((PASS+1)); echo "PASS  A恢复后车辆重占(BUSY+currentOrderId=A)"; else FAIL=$((FAIL+1)); echo "FAIL  A恢复车辆状态=$RESUME_CAR 期望 BUSY-$A_OID"; fi
req "插单-任务A继续完成" POST /attendance/task-end "$DRIVER2" "{\"orderId\":$A_OID,\"longitude\":117.12,\"latitude\":36.71,\"photoUrls\":[\"/files/e2e/ins-ae-$TS.jpg\"]}"
END_CAR=$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT status FROM biz_vehicle WHERE id=$WVID;" 2>/dev/null | tr -d ' \n')
if [ "$END_CAR" = "IDLE" ]; then PASS=$((PASS+1)); echo "PASS  A完成后车辆释放回IDLE"; else FAIL=$((FAIL+1)); echo "FAIL  A完成后车辆状态=$END_CAR"; fi

echo "== 19. AI 报告（连通性 / mock / 手动触发 / 真实生成）=="
req "AI开关临时关闭" PUT /system/configs/ai.enabled "$ADMIN" '{"configValue":"false"}'
AI_DIS=$(curl -s -X POST "$BASE/ai/reports/generate" -H 'Content-Type: application/json' -H 'X-Client: admin' -H "Authorization: Bearer $ADMIN" -H "Idempotency-Key: e2e-$TS-ai0" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("code"))')
if [ "$AI_DIS" = "50005" ]; then PASS=$((PASS+1)); echo "PASS  AI未开启时手动生成被拒(50005)"; else FAIL=$((FAIL+1)); echo "FAIL  AI未开启生成 code=$AI_DIS"; fi
req "AI开关恢复" PUT /system/configs/ai.enabled "$ADMIN" '{"configValue":"true"}'
req "开启mock演示模式" PUT /system/configs/ai.mock "$ADMIN" '{"configValue":"true"}'
req "mock模式手动生成报告" POST /ai/reports/generate "$ADMIN" ""
MOCK_OK=$(python3 -c '
import json
d=json.load(open("/tmp/e2e-last.json"))["data"]["report"]
print("YES" if d["callStatus"]=="SUCCESS" and "mock" in d["contentMarkdown"] else "NO")
')
if [ "$MOCK_OK" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  mock模式生成SUCCESS且含演示文本"; else FAIL=$((FAIL+1)); echo "FAIL  mock生成结果异常"; fi
req "查询最新报告" GET /ai/reports/latest "$ADMIN" ""
req "AI连通性诊断(mock)" GET /ai/diagnose "$ADMIN" ""
DG_OK=$(python3 -c 'import json;print("YES" if json.load(open("/tmp/e2e-last.json"))["data"]["ok"] else "NO")')
if [ "$DG_OK" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  诊断ok(mock模式)"; else FAIL=$((FAIL+1)); echo "FAIL  诊断未通过"; fi
req "关闭mock恢复真实调用" PUT /system/configs/ai.mock "$ADMIN" '{"configValue":"false"}'
req "真实大模型生成日报(DeepSeek)" POST /ai/reports/generate "$ADMIN" ""
REAL_OK=$(python3 -c '
import json
d=json.load(open("/tmp/e2e-last.json"))["data"]["report"]
print("YES" if d["callStatus"]=="SUCCESS" and len(d["contentMarkdown"])>50 else "NO")
')
if [ "$REAL_OK" = "YES" ]; then PASS=$((PASS+1)); echo "PASS  真实大模型生成SUCCESS(正文>50字)"; else FAIL=$((FAIL+1)); echo "FAIL  真实生成失败或内容过短"; fi

echo "=============================="
echo "E2E 结果：PASS=$PASS FAIL=$FAIL"
exit $FAIL
