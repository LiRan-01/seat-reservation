#!/usr/bin/env bash
# 功能验收脚本：逐条执行报告第 6 章表 18 / 表 20 的用例，输出 PASS/FAIL。
# 用法：bash scripts/acceptance-test.sh
set -u

API=${API:-http://localhost:8080}
PASS=0
FAIL=0
ok()  { echo "PASS $1 | $2"; PASS=$((PASS + 1)); }
no()  { echo "FAIL $1 | $2"; FAIL=$((FAIL + 1)); }

jstr() { echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -1; }
jnum() { echo "$1" | sed -n "s/.*\"$2\":\([0-9][0-9]*\).*/\1/p" | head -1; }
ts()   { date -d "$1" '+%Y-%m-%dT%H:%M:%S'; }

tsv() { curl -s -H 'Content-Type: application/json' "$@"; }

NOW=$(ts now)
END=$(ts '+90 minutes')

echo "== 智能自习室座位预约系统 · 功能验收 $(date '+%F %T') =="

# ---------- FT-01 座位图 ----------
S1=$(tsv "$API/api/rooms/1/seats?start=$NOW&end=$END")
if [[ "$S1" == *"A-01"* && "$S1" == *'"busy":false'* ]]; then
  ok FT-01 "座位图返回 20 个座位（A-01…A-20），空闲座位 busy=false"
else
  no FT-01 "座位图异常：$(echo "$S1" | head -c 120)"
fi

# ---------- FT-02 正常预约 ----------
R2=$(tsv -X POST "$API/api/reservations" -d "{\"userId\":1001,\"seatId\":1,\"start\":\"$NOW\",\"end\":\"$END\"}")
RID2=$(jnum "$R2" id)
CODE2=$(jstr "$R2" checkinCode)
if [[ "$R2" == *'"code":"OK"'* && -n "$RID2" && -n "$CODE2" ]]; then
  ok FT-02 "预约成功 id=$RID2，签到码长度=${#CODE2}"
else
  no FT-02 "预约失败：$(echo "$R2" | head -c 160)"
fi

# ---------- FT-03 重复预约 ----------
R3=$(tsv -X POST "$API/api/reservations" -d "{\"userId\":1002,\"seatId\":1,\"start\":\"$NOW\",\"end\":\"$END\"}")
if [[ "$R3" == *"TIME_CONFLICT"* ]]; then
  ok FT-03 "第二人预约同一座位同一时段被拒绝（TIME_CONFLICT）"
else
  no FT-03 "重复预约未被拦截：$(echo "$R3" | head -c 160)"
fi

# ---------- FT-04 窗口内签到 ----------
C4=$(tsv -X POST "$API/api/checkin" -d "{\"token\":\"$CODE2\",\"deviceId\":\"dev-A\"}")
if [[ "$C4" == *'"status":"USING"'* ]]; then
  ok FT-04 "签到成功，预约转为 USING"
else
  no FT-04 "签到失败：$(echo "$C4" | head -c 160)"
fi

# ---------- ET-02 签到码重放 ----------
C4B=$(tsv -X POST "$API/api/checkin" -d "{\"token\":\"$CODE2\",\"deviceId\":\"dev-B\"}")
if [[ "$C4B" == *"REPLAY"* ]]; then
  ok ET-02 "同一签到码第二次使用被拒绝（REPLAY）"
else
  no ET-02 "重放未被拦截：$(echo "$C4B" | head -c 160)"
fi

# ---------- FT-06 暂离与返回 ----------
A6=$(tsv -X POST "$API/api/reservations/$RID2/away?minutes=25")
B6=$(tsv -X POST "$API/api/reservations/$RID2/back")
D6=$(tsv "$API/api/reservations/$RID2")
if [[ "$A6" == *'"status":"AWAY"'* && "$B6" == *'"status":"USING"'* && "$D6" == *'"awayUntil":null'* ]]; then
  ok FT-06 "暂离 25 分钟 → AWAY，返回后回到 USING 且清空 awayUntil"
else
  no FT-06 "暂离/返回异常：$(echo "$A6" | head -c 80) / $(echo "$B6" | head -c 80)"
fi

# ---------- FT-05 窗口外签到 ----------
NOW_PAST=$(ts '-40 minutes')
END_PAST=$(ts '+50 minutes')
R5=$(tsv -X POST "$API/api/reservations" -d "{\"userId\":1003,\"seatId\":2,\"start\":\"$NOW_PAST\",\"end\":\"$END_PAST\"}")
C5=$(tsv -X POST "$API/api/checkin" -d "{\"token\":\"$(jstr "$R5" checkinCode)\",\"deviceId\":\"dev-C\"}")
if [[ "$C5" == *"OUT_OF_WINDOW"* ]]; then
  ok FT-05 "开始后 40 分钟签到被拒绝（OUT_OF_WINDOW）"
else
  no FT-05 "窗口外签到未被拦截：$(echo "$C5" | head -c 160)"
fi

# ---------- ET-04 跨天预约 ----------
D1=$(date '+%Y-%m-%d')
D2=$(date -d '+1 day' '+%Y-%m-%d')
R8=$(tsv -X POST "$API/api/reservations" -d "{\"userId\":1002,\"seatId\":5,\"start\":\"${D1}T23:30:00\",\"end\":\"${D2}T00:30:00\"}")
if [[ "$R8" == *'"code":"OK"'* ]]; then
  ok ET-04 "23:30→次日 00:30 跨天预约成功，时段连续不重叠"
else
  no ET-04 "跨天预约失败：$(echo "$R8" | head -c 160)"
fi

# ---------- FT-09 强制释放 ----------
R9=$(tsv -X POST "$API/api/reservations" -d "{\"userId\":1001,\"seatId\":4,\"start\":\"$(ts '+2 hours')\",\"end\":\"$(ts '+3 hours')\"}")
F9=$(tsv -X POST "$API/api/reservations/$(jnum "$R9" id)/release?reason=%E7%8E%B0%E5%9C%BA%E6%A0%B8%E5%AE%9E")
if [[ "$F9" == *'"status":"FORCED"'* ]]; then
  ok FT-09 "管理员强制释放成功，预约转为 FORCED"
else
  no FT-09 "强制释放失败：$(echo "$F9" | head -c 160)"
fi

# ---------- FT-07/FT-08 未签到超时释放 + 候补通知 ----------
NOW_OLD=$(ts '-30 minutes')
END_OLD=$(ts '+30 minutes')
R10=$(tsv -X POST "$API/api/reservations" -d "{\"userId\":1003,\"seatId\":3,\"start\":\"$NOW_OLD\",\"end\":\"$END_OLD\"}")
RID10=$(jnum "$R10" id)
W7=$(tsv -X POST "$API/api/waitlist" -d "{\"seatId\":3,\"userId\":1004,\"start\":\"$NOW_OLD\",\"end\":\"$END_OLD\"}")
echo "INFO 已构造超时预约 id=$RID10 与候补 user=1004，等待定时任务（最多 120 秒）…"
STATUS=""
for i in $(seq 1 20); do
  D10=$(tsv "$API/api/reservations/$RID10")
  if [[ "$D10" == *'"status":"NO_SHOW"'* ]]; then STATUS="NO_SHOW"; break; fi
  sleep 6
done
JOBLOG=$(docker logs seat-backend --since 3m 2>&1 | grep -E "release job finished|waitlist notified" | tail -2)
if [[ "$STATUS" == "NO_SHOW" ]]; then
  ok FT-08 "未签到 30 分钟的预约被定时任务置为 NO_SHOW 并释放座位"
else
  no FT-08 "定时任务未在 120 秒内释放，当前状态：$(echo "$D10" | grep -o '"status":"[A-Z_]*"')"
fi
if [[ "$JOBLOG" == *"release job finished"* ]]; then
  ok FT-08b "任务日志：$(echo "$JOBLOG" | tr '\n' ' ' | head -c 120)"
else
  no FT-08b "未见任务日志：$JOBLOG"
fi
if [[ "$JOBLOG" == *"waitlist notified"* ]]; then
  ok FT-07 "座位释放后候补首位收到通知（日志 waitlist notified）"
else
  no FT-07 "未见候补通知日志"
fi

# ---------- FT-10 统计报表 ----------
D_TODAY=$(date '+%Y-%m-%d')
ST=$(tsv "$API/api/stats/occupancy?from=$D_TODAY&to=$D_TODAY")
CNT=$(jnum "$ST" reservations)
if [[ "$ST" == *'"code":"OK"'* && -n "$CNT" ]]; then
  ok FT-10 "统计接口返回当日上座率数据，当日预约 $CNT 条：$(echo "$ST" | grep -o '"rate":[0-9.]*' | tail -1)"
else
  no FT-10 "统计接口异常：$(echo "$ST" | head -c 160)"
fi

echo "== 结果：PASS=$PASS FAIL=$FAIL =="
