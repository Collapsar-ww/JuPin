#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
SCENARIO="mock_callback"
RAW_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_raw.tsv"
SUMMARY_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_summary.tsv"

ensure_write_user
create_write_pool
create_order_once "callback-load-${RUN_ID}-${LABEL}-${WRITE_POOL_ID}"

channel_id="mock-callback-load-${ORDER_NO}-${RUN_ID}-${LABEL}"
body=$(jq -n \
  --arg orderNo "$ORDER_NO" \
  --arg payRequestNo "pay-${ORDER_NO}-${RUN_ID}-${LABEL}" \
  --arg callbackRequestNo "callback-${ORDER_NO}-${RUN_ID}-${LABEL}" \
  --arg channelTxnId "$channel_id" \
  '{orderNo:$orderNo,payRequestNo:$payRequestNo,callbackRequestNo:$callbackRequestNo,channelTxnId:$channelTxnId,payStatus:"SUCCESS"}')

: > "$RAW_FILE"
started_at=$(date +%s)
running=0
for i in $(seq 1 "$REQUESTS"); do
  (
    tmp=$(mktemp)
    time_status=$(curl -sS -o "$tmp" -w "%{time_total}\t%{http_code}" -X POST "$BASE_URL/api/player/order/mock-callback" \
      -H "Authorization: Bearer $WRITE_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$body")
    code=$(jq -r '.code // empty' "$tmp" 2>/dev/null || true)
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
echo "channel_txn_id=$channel_id"

total=$(wc -l < "$RAW_FILE" | tr -d ' ')
business_ok=$(awk '$2 == 200 && $3 == 200 {count++} END {print count + 0}' "$RAW_FILE")
if [[ "$business_ok" != "$total" ]]; then
  echo "Mock callback load did not fully pass. This may mean the tested version does not support /mock-callback or has a callback idempotency issue." >&2
  exit 1
fi
