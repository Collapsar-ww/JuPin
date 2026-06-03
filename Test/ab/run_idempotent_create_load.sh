#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
SCENARIO="idempotent_create"
RAW_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_raw.tsv"
SUMMARY_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_summary.tsv"

ensure_write_user
create_write_pool

idem_key="idem-load-${RUN_ID}-${LABEL}-${WRITE_POOL_ID}"
body=$(jq -n \
  --argjson poolId "$WRITE_POOL_ID" \
  --argjson type "$ORDER_TYPE" \
  --arg idempotentKey "$idem_key" \
  '{poolId:$poolId,type:$type,idempotentKey:$idempotentKey}')

: > "$RAW_FILE"
started_at=$(date +%s)
running=0
for i in $(seq 1 "$REQUESTS"); do
  (
    tmp=$(mktemp)
    time_status=$(curl -sS -o "$tmp" -w "%{time_total}\t%{http_code}" -X POST "$BASE_URL/api/player/order/create" \
      -H "Authorization: Bearer $WRITE_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$body")
    code=$(jq -r '.code // empty' "$tmp")
    order_no=$(jq -r '.data.orderNo // empty' "$tmp")
    rm -f "$tmp"
    printf "%s\t%s\t%s\n" "$time_status" "$code" "$order_no"
  ) >> "$RAW_FILE" &

  running=$((running + 1))
  if (( running >= CONCURRENCY )); then
    wait
    running=0
  fi
done
wait
ended_at=$(date +%s)

ORDER_NO=$(awk '$4 != "" {print $4; exit}' "$RAW_FILE")
write_summary "$RAW_FILE" "$SUMMARY_FILE" "$SCENARIO" "$REQUESTS" "$CONCURRENCY" "$started_at" "$ended_at" \
  '$2 == 200 && $3 == 200 {count++} END {print count + 0}'

distinct_order_count=$(awk '$4 != "" {seen[$4]=1} END {for (k in seen) count++; print count + 0}' "$RAW_FILE")
echo "idempotent_key=$idem_key"
echo "distinct_order_no_count=$distinct_order_count"
echo "order_no=${ORDER_NO:-}"

total=$(wc -l < "$RAW_FILE" | tr -d ' ')
business_ok=$(awk '$2 == 200 && $3 == 200 {count++} END {print count + 0}' "$RAW_FILE")
if [[ "$business_ok" != "$total" || "$distinct_order_count" != "1" ]]; then
  echo "Idempotent create load failed: expected all business success and exactly one order number." >&2
  exit 1
fi
