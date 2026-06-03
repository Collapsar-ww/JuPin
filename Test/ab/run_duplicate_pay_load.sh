#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
SCENARIO="duplicate_pay"
RAW_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_raw.tsv"
SUMMARY_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_summary.tsv"

ensure_write_user
create_write_pool
create_order_once "pay-load-${RUN_ID}-${LABEL}-${WRITE_POOL_ID}"

: > "$RAW_FILE"
started_at=$(date +%s)
running=0
for i in $(seq 1 "$REQUESTS"); do
  (
    tmp=$(mktemp)
    time_status=$(curl -sS -o "$tmp" -w "%{time_total}\t%{http_code}" -X POST "$BASE_URL/api/player/order/pay/$ORDER_NO" \
      -H "Authorization: Bearer $WRITE_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{}")
    code=$(jq -r '.code // empty' "$tmp")
    rm -f "$tmp"
    printf "%s\t%s\n" "$time_status" "$code"
  ) >> "$RAW_FILE" &

  running=$((running + 1))
  if (( running >= CONCURRENCY )); then
    wait
    running=0
  fi
done
wait
ended_at=$(date +%s)

write_summary "$RAW_FILE" "$SUMMARY_FILE" "$SCENARIO" "$REQUESTS" "$CONCURRENCY" "$started_at" "$ended_at" \
  '$2 == 200 && $3 == 200 {count++} END {print count + 0}'

echo "order_no=$ORDER_NO"
echo "write_pool_id=$WRITE_POOL_ID"

total=$(wc -l < "$RAW_FILE" | tr -d ' ')
business_ok=$(awk '$2 == 200 && $3 == 200 {count++} END {print count + 0}' "$RAW_FILE")
if [[ "$business_ok" != "$total" ]]; then
  echo "Duplicate pay load failed: expected all requests to return business success." >&2
  exit 1
fi
