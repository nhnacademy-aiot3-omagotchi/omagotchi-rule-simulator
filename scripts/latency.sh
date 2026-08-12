#!/usr/bin/env bash
# 사용법: ./latency.sh [반복횟수(기본 100)]
# 부하 시험(load/burst 프로파일)이 돌아가는 동안, 별도 터미널에서 같이 돌리세요.
# rule-service 코드는 안 건드리고, MQTT 발행 + InfluxDB 조회를 직접 수행해서
# "발행 → InfluxDB에서 조회 가능"까지 걸린 시간을 측정합니다.

set -euo pipefail

ROUNDS="${1:-100}"

MQTT_HOST="${MQTT_HOST:-localhost}"
MQTT_PORT="${MQTT_PORT:-1883}"
APP_ID="${APP_ID:-sim-application}"

INFLUX_URL="${INFLUX_URL:-http://localhost:8086}"
INFLUX_TOKEN="${INFLUX_TOKEN:-token}"
INFLUX_ORG="${INFLUX_ORG:-org}"
INFLUX_BUCKET="${INFLUX_BUCKET:-omagotchi-raw}"

POLL_INTERVAL_SEC="0.2"
TIMEOUT_SEC="30"
GAP_BETWEEN_ROUNDS_SEC="1"

now_epoch() { python3 -c "import time; print(time.time())"; }

results_file=$(mktemp)
trap 'rm -f "$results_file"' EXIT

max_polls=$(python3 -c "print(int(${TIMEOUT_SEC} / ${POLL_INTERVAL_SEC}))")

echo "지연 측정 시작 - ${ROUNDS}회, 라운드 간격 ${GAP_BETWEEN_ROUNDS_SEC}초, 타임아웃 ${TIMEOUT_SEC}초"

for ((i=1; i<=ROUNDS; i++)); do
    dev_eui="sim-probe-${i}"
    topic="application/${APP_ID}/device/${dev_eui}/event/up"
    now_iso=$(python3 -c "import datetime; print(datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z'))")

    payload=$(cat <<EOF
{"deduplicationId":"lat-${i}","time":"${now_iso}","deviceInfo":{"deviceProfileName":"sim-profile","deviceName":"${dev_eui}","devEui":"${dev_eui}","tags":{"location":"지연측정"}},"fCnt":${i},"fPort":85,"object":{"temperature":25.0},"rxInfo":[{"gatewayId":"sim-gateway","nsTime":"${now_iso}"}]}
EOF
)

    publish_at=$(now_epoch)
    mosquitto_pub -h "$MQTT_HOST" -p "$MQTT_PORT" -t "$topic" -q 1 -m "$payload"

    flux_query="from(bucket: \"${INFLUX_BUCKET}\") |> range(start: -2m) |> filter(fn: (r) => r._measurement == \"temperature\" and r.device_eui == \"${dev_eui}\" and r._field == \"value\") |> limit(n: 1)"

    found="no"
    poll_count=0
    while [ "$poll_count" -lt "$max_polls" ]; do
        response=$(curl -s -XPOST "${INFLUX_URL}/api/v2/query?org=${INFLUX_ORG}" \
            -H "Authorization: Token ${INFLUX_TOKEN}" \
            -H "Content-Type: application/vnd.flux" \
            -H "Accept: application/csv" \
            --data-raw "$flux_query")
        asdasdasdsnsdd
        data_lines=$(echo "$response" | grep -v '^#' | tail -n +2 | grep -c '.' || true)

        if [ "$data_lines" -gt 0 ]; then
            found_at=$(now_epoch)
            latency=$(python3 -c "print(round(${found_at} - ${publish_at}, 3))")
            echo "${i} ${latency}" >> "$results_file"
            found="yes"
            break
        fi
        sleep "$POLL_INTERVAL_SEC"
        poll_count=$((poll_count + 1))
    done

    if [ "$found" = "no" ]; then
        echo "${i} TIMEOUT" >> "$results_file"
        echo "  [${i}/${ROUNDS}] 타임아웃 (${TIMEOUT_SEC}초 안에 조회 안 됨)"
    else
        echo "  [${i}/${ROUNDS}] ${latency}초"
    fi

    sleep "$GAP_BETWEEN_ROUNDS_SEC"
done

echo ""
echo "================ 지연 측정 결과 ================"
python3 - "$results_file" <<'PYEOF'
import sys

path = sys.argv[1]
latencies = []
timeouts = 0
with open(path) as f:
    for line in f:
        parts = line.split()
        if len(parts) != 2:
            continue
        if parts[1] == "TIMEOUT":
            timeouts += 1
        else:
            latencies.append(float(parts[1]))

total = len(latencies) + timeouts
print(f"총 {total}건 중 성공 {len(latencies)}건, 타임아웃 {timeouts}건")

if latencies:
    latencies.sort()
    n = len(latencies)
    p50 = latencies[int(n * 0.50)]
    p99_idx = min(int(n * 0.99), n - 1)
    p99 = latencies[p99_idx]
    print(f"최소: {latencies[0]:.3f}초")
    print(f"p50 : {p50:.3f}초")
    print(f"p99 : {p99:.3f}초  (100건 중 99건이 이 시간 안에 조회 가능)")
    print(f"최대: {latencies[-1]:.3f}초")
else:
    print("성공한 측정이 없습니다 - InfluxDB 연결/설정을 확인하세요.")
PYEOF
echo "==================================================="