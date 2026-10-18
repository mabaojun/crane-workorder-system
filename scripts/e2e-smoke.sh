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
req "客户提报需求单" POST /demand/orders "$CUST" "{\"source\":\"CUSTOMER_MP\",\"contactName\":\"E2E联系人\",\"contactPhone\":\"$CUST_PHONE\",\"address\":\"济南市历下区经十路E2E测试点位$TS\",\"longitude\":117.12,\"latitude\":36.71,\"description\":\"吊装钢结构一批\",\"craneTonnage\":25,\"cargoWeight\":8,\"expectedStartAt\":\"2026-12-01T09:00:00+08:00\"}"
DID=$(jq_get data id)

echo "== 5. 复核与转工单 =="
req "管理员复核通过" POST /demand/orders/$DID/review "$ADMIN" '{"approve":true,"reviewRemark":"复核通过"}'
req "需求单转工单" POST /demand/orders/$DID/convert "$ADMIN" "{\"staffId\":$SID,\"vehicleId\":$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_vehicle ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n'),\"planStartAt\":\"2026-12-01T09:00:00+08:00\",\"craneTonnage\":25,\"cargoWeightTon\":8,\"remark\":\"E2E转单\"}"
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
req "创建退单验证单" POST /work-order/orders "$ADMIN" "{\"taskName\":\"退单验证单\",\"source\":\"DIRECT\",\"customerId\":$CID,\"staffId\":$SID2,\"vehicleId\":$(docker exec ${E2E_MYSQL_CONTAINER:-crane-mysql-dev} mysql --default-character-set=utf8mb4 -u${E2E_MYSQL_USER:-crane} -p${E2E_MYSQL_PW:-crane123} crane_workorder -N -e "SELECT id FROM biz_vehicle WHERE status='IDLE' AND deleted=0 ORDER BY id DESC LIMIT 1;" 2>/dev/null | tr -d ' \n'),\"planStartAt\":\"2026-12-09T09:00:00+08:00\",\"address\":\"退单测试点位\",\"longitude\":117.12,\"latitude\":36.71,\"cargoName\":\"测试\",\"cargoQty\":1,\"cargoWeightTon\":1,\"craneTonnage\":25}"
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

echo "=============================="
echo "E2E 结果：PASS=$PASS FAIL=$FAIL"
exit $FAIL
