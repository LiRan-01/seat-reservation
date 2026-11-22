#!/usr/bin/env bash
# 并发与性能实测：对应报告表 19。
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

# ---------- 1. 座位图查询：150 次请求 / 30 并发 ----------
: > "$OUT/seats.txt"
seq 1 150 | xargs -P 30 -I{} sh -c 'curl -s -o /dev/null -w "%{time_total}\n" "$API/api/rooms/1/seats?start=$NOW&end=$END"' >> "$OUT/seats.txt"
echo "座位图查询 150 次请求（30 并发）：P50=$(pct "$OUT/seats.txt" 0.5)ms，P95=$(pct "$OUT/seats.txt" 0.95)ms"

# ---------- 2. 200 用户抢 40 个座位（每座位 5 次请求） ----------
reset_db
rm -f "$OUT"/resp_*.txt
seq 1 200 | xargs -P 40 -I{} sh -c 'n={}; s=$(( (n % 40) + 1 )); curl -s -X POST "$API/api/reservations" -H "Content-Type: application/json" -d "{\"userId\":1001,\"seatId\":$s,\"start\":\"$NOW\",\"end\":\"$END\"}" > "$OUT/resp_$n.txt"'
OKC=$(grep -l '"code":"OK"' "$OUT"/resp_*.txt 2>/dev/null | wc -l)
CFC=$(grep -l 'TIME_CONFLICT' "$OUT"/resp_*.txt 2>/dev/null | wc -l)
SRV=$(grep -l 'SERVER_ERROR' "$OUT"/resp_*.txt 2>/dev/null | wc -l)
OTHER=$((200 - OKC - CFC - SRV))
OVER=$($MYSQL "USE seat; SELECT COUNT(*) FROM (SELECT seat_id,start_time,COUNT(*) c FROM reservation WHERE status IN ('RESERVED','USING','AWAY') GROUP BY seat_id,start_time HAVING c>1) x;" | tail -1)
echo "抢座 200 次请求（40 座位 × 5 次，40 并发）：成功 $OKC，冲突 $CFC，服务异常 $SRV，其他 $OTHER，超卖单数 $OVER"
echo "响应码分布："
cat "$OUT"/resp_*.txt 2>/dev/null | grep -o '"code":"[A-Z_]*"' | sort | uniq -c

# ---------- 3. 40 并发签到（每个座位一张预约） ----------
reset_db
: > "$OUT/tokens.txt"
CREATED=0
for i in $(seq 1 40); do
  r=$(curl -s -X POST "$API/api/reservations" -H "Content-Type: application/json" \
    -d "{\"userId\":1002,\"seatId\":$i,\"start\":\"$NOW\",\"end\":\"$END\"}")
  tok=$(echo "$r" | grep -o '"checkinCode":"[^"]*"' | cut -d'"' -f4)
  if [ -n "$tok" ]; then
    echo "$tok" >> "$OUT/tokens.txt"; CREATED=$((CREATED + 1))
  else
    echo "座位 $i 创建失败：$(echo "$r" | head -c 120)"
  fi
done
echo "预约创建成功 $CREATED / 40（签到码 $(wc -l < "$OUT/tokens.txt") 个）"
cat "$OUT/tokens.txt" | xargs -P 20 -I{} sh -c 'curl -s -o /dev/null -w "%{time_total}\n" -X POST "$API/api/checkin" -H "Content-Type: application/json" -d "{\"token\":\"{}\",\"deviceId\":\"load\"}"' > "$OUT/checkin.txt"
echo "签到请求 $(wc -l < "$OUT/checkin.txt") 条（20 并发）：P50=$(pct "$OUT/checkin.txt" 0.5)ms，P95=$(pct "$OUT/checkin.txt" 0.95)ms"

# ---------- 4. 定时任务单批 500 条 ----------
reset_db
$MYSQL "USE seat;
INSERT INTO reservation (user_id, seat_id, start_time, end_time, status)
SELECT 1001, (n % 40) + 1,
       DATE_SUB(NOW(), INTERVAL (1800 + n) SECOND),
       DATE_SUB(NOW(), INTERVAL (1800 + n) SECOND) + INTERVAL 60 MINUTE,
       'RESERVED'
FROM (SELECT ROW_NUMBER() OVER () AS n FROM information_schema.columns LIMIT 500) x;" 2>&1 | tail -3
INS=$($MYSQL "USE seat; SELECT COUNT(*) FROM reservation;" | tail -1)
echo "已插入过期未签到预约 $INS 条，等待定时任务释放（最多 150 秒）…"
T0=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
COST=""
for i in $(seq 1 30); do
  REMAIN=$($MYSQL "USE seat; SELECT COUNT(*) FROM reservation WHERE status='RESERVED';" | tail -1)
  if [ "$REMAIN" = "0" ]; then
    COST=$(docker logs seat-backend --since "$T0" 2>&1 | grep -o 'released=[0-9]*, cost=[0-9]*ms' | tail -1)
    break
  fi
  sleep 5
done
echo "定时任务单批：剩余未处理 $REMAIN 条，日志 $COST"
echo "== 实测结束 =="
