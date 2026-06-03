#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
SCENARIO="unauthorized_create"
RAW_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_raw.tsv"
SUMMARY_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_${SCENARIO}_summary.tsv"

body=$(jq -n --argjson poolId "$POOL_ID" --argjson type "$ORDER_TYPE" \
  '{poolId:$poolId,type:$type,idempotentKey:"unauthorized-direct-load"}')

: > "$RAW_FILE"
started_at=$(date +%s)
running=0
for i in $(seq 1 "$REQUESTS"); do
  (
    post_json_status "/api/player/order/create" "$body"
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
  '$2 != 200 {count++} END {print count + 0}'

total=$(wc -l < "$RAW_FILE" | tr -d ' ')
blocked=$(awk '$2 != 200 {count++} END {print count + 0}' "$RAW_FILE")
if [[ "$blocked" != "$total" ]]; then
  echo "Unauthorized create load failed: some requests returned HTTP 200." >&2
  exit 1
fi
