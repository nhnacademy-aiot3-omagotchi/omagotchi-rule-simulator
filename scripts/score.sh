#!/usr/bin/env bash
# 사용법: ./score.sh <엔진_로그파일>
# application.yaml의 현재 fault 시나리오를 기준으로,
# 엔진이 실제로 감지·로깅한 건수를 세어 기대값과 비교합니다.
# 시나리오(from-tick/count)가 바뀌면 아래 EXPECTED_* 값도 같이 고쳐주세요.

set -euo pipefail
LOG="${1:?사용법: $0 <엔진_로그파일>}"

# --- application.yaml 현재 시나리오 기준 기대값 ---
EXPECTED_ANOMALY=3      # sim-0001, from-tick 5,  count 3
EXPECTED_DUPLICATE=2    # sim-0001, from-tick 10, count 2
EXPECTED_DELAYED=2      # sim-0001, from-tick 14, count 2
EXPECTED_MISSING=2      # sim-0001, from-tick 18, count 2
EXPECTED_DISCONNECT=1   # sim-0002, from-tick 5,  count 5 (구간 1개 = 시작+종료 각 1건)

count() { grep -Ec "$1" "$LOG" || true; }

anomaly=$(count '\[범위초과\] sim-0001:')
duplicate=$(count '\[중복\] sim-0001:')
delayed=$(count '\[지연\] sim-0001:')

missing_single=$(count '\[결측\] sim-0001 fCnt [0-9]+ 누락$')
missing_batch=$(sed -n -E 's/.*\(([0-9]+)건 일괄\).*/\1/p' "$LOG" | awk '{s+=$1} END{print s+0}')
missing=$((missing_single + missing_batch))

disc_start=$(count '\[끊김 시작\] sim-0002:')
disc_end=$(count '\[끊김 종료\] sim-0002:')
stuck=$(count '\[무변동\]')

row() {
    local name="$1" expected="$2" actual="$3"
    local verdict="FAIL"
    [[ "$expected" == "$actual" ]] && verdict="PASS"
    printf "%-10s %6s %6s %s\n" "$name" "$expected" "$actual" "$verdict"
}

echo "================ 채점 결과 ================"
printf "%-10s %6s %6s %s\n" "TYPE" "기대" "실제" "판정"
echo "---------------------------------------------"
row "ANOMALY"    "$EXPECTED_ANOMALY"    "$anomaly"
row "DUPLICATE"  "$EXPECTED_DUPLICATE"  "$duplicate"
row "DELAYED"    "$EXPECTED_DELAYED"    "$delayed"
row "MISSING"    "$EXPECTED_MISSING"    "$missing"
row "DISCONNECT" "$EXPECTED_DISCONNECT" "$disc_start"
echo "==============================================="
echo "  참고: 끊김 종료 로그 ${disc_end}건 (구간 복구까지 관측됐는지 참고용, 채점 대상 아님)"
echo "  참고: 무변동 로그 ${stuck}건 (30분+ 지속돼야 감지 — 단기 실행에선 0건이 정상)"