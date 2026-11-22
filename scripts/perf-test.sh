#!/usr/bin/env bash
# 性能与并发验收脚本：对应报告表 19 的三行指标。
# 用法：bash scripts/perf-test.sh
set -u

API=${API:-http://localhost:8080}
OUT=${OUT:-/tmp/perf}
mkdir -p "$OUT"
MYSQL="docker exec seat-mysql mysql -uroot -pseat123 -N -e"

pct() { sort -n "$1" | awk -v p="$2" '{v[NR]=$1} END{i=int(NR*p); if(i<1)i=1; if(i>NR)i=NR; printf "%d", v[i]*1000}'; }
ts()  { date -d "$1" '+%Y-%m-%dT%H:%M:%S'; }

reset_db() {
  $MYSQL "USE seat; DELETE FROM reservation; DELETE FROM violation; DELETE FROM checkin_record; DELETE FROM wait_entry; UPDATE seat SET status='AVAILABLE';" >/dev/null 2>&1
}

NOW=$(ts now)
END=$(ts '+90 minutes')
export API NOW END OUT

echo "== 并发与性能实测 $(date '+%F %T') =="
reset_db

# ---------- 1. 座位图查询：150 并发 ----------
: > "$OUT/seats.txt"
seq 1 150 | xargs -P 50 -I{} sh -c 'curl -s -o /dev/null -w "%{time_total}\n" "$API/api/rooms/1/seats?start=$NOW&end=$END"' >> "$OUT/seats.txt"
echo "座位图查询 150 并发：样本 $(wc -l < "$OUT/seats.txt") 条，P50=$(pct "$OUT/seats.txt" 0.5)ms，P95=$(pct "$OUT/seats.txt" 0.95)ms"

# ---------- 2. 200 用户抢 40 个座位 ----------
: > "$OUT/resp.txt"
seq 1 200 | xargs -P 50 -I{} sh -c 'n={}; s=$(( (n % 40) + 1 )); curl -s -X POST "$API/api/reservations" -H "Content-Type: application/json" -d "{\"userId\":1001,\"seatId\":$s,\"start\":\"$NOW\",\"end\":\"$END\"}"' > "$OUT/resp.txt"
OKC=$(grep -c '"code":"OK"' "$OUT/resp.txt")
CFC=$(grep -c 'TIME_CONFLICT' "$OUT/resp.txt")
OTHER=$((200 - OKC - CFC))
OVER=$($MYSQL "USE seat; SELECT COUNT(*) FROM (SELECT seat_id,start_time,COUNT(*) c FROM reservation WHERE status IN ('RESERVED','USING','AWAY') GROUP BY seat_id,start_time HAVING c>1) x;" | tail -1)
echo "抢座 200 并发（40 座位 × 5 次）：成功 $OKC，冲突 $CFC，其他 $OTHER，超卖单数 $OVER"

# ---------- 3. 40 并发签到 ----------
reset_db
: > "$OUT/tokens.txt"
for i in $(seq 1 40); do
  curl -s -X POST "$API/api/reservations" -H "Content-Type: application/json" \
    -d "{\"userId\":1001,\"seatId\":$i,\"start\":\"$NOW\",\"end\":\"$END\"}" \
    | grep -o '"checkinCode":"[^"]*"' | cut -d'"' -f4 >> "$OUT/tokens.txt"
done
echo "已为 40 个座位各建一张预约，签到码 $(wc -l < "$OUT/tokens.txt") 个"
cat "$OUT/tokens.txt" | xargs -P 20 -I{} sh -c 'curl -s -o /dev/null -w "%{time_total}\n" -X POST "$API/api/checkin" -H "Content-Type: application/json" -d "{\"token\":\"{}\",\"deviceId\":\"load\"}"' > "$OUT/checkin.txt"
echo "签到 40 并发：样本 $(wc -l < "$OUT/checkin.txt") 条，P50=$(pct "$OUT/checkin.txt" 0.5)ms，P95=$(pct "$OUT/checkin.txt" 0.95)ms"

# ---------- 4. 定时任务单批 500 条 ----------
reset_db
$MYSQL "USE seat;
INSERT INTO reservation (user_id, seat_id, start_time, end_time, status)
SELECT 1001, (n % 40) + 1,
       DATE_SUB(NOW(), INTERVAL (1800 + n) SECOND),
       DATE_SUB(NOW(), INTERVAL (1800 + n) SECOND) + INTERVAL 60 MINUTE,
       'RESERVED'
FROM (SELECT @n := @n + 1 AS n FROM information_schema.columns, (SELECT @n := 0) t LIMIT 500) x;" >/dev/null 2>&1
echo "已插入 500 条过期未签到预约，等待定时任务（最多 150 秒）…"
COST=""
for i in $(seq 1 30); do
  COST=$(docker logs seat-backend --since 6m 2>&1 | grep -o 'released=[0-9]*, cost=[0-9]*ms' | tail -1)
  [ -n "$COST" ] && break
  sleep 5
done
echo "定时任务单批：$COST"
echo "== 实测结束，原始数据在 $OUT =="
