#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/ab_common.sh"

# ========== CONFIG ==========
REQUESTS="${REQUESTS:-50}"
CONCURRENCY="${CONCURRENCY:-10}"
SCENARIO="idempotent"

# ========== SINGLE-ROUND TEST ==========
run_idempotent_round() {
    local results="$RESULTS_DIR"
    mkdir -p "$results"

    local run_tail
    run_tail=$(printf "%s" "${RUN_ID}_${AB_ROUND_LABEL}" | cksum | awk '{printf "%06d", $1 % 1000000}')

    ensure_write_user
    local write_token="$WRITE_TOKEN"

    # --- Sub-scenario 1: Duplicate create ---
    echo "[idempotent-create] $REQUESTS concurrent requests, same idempotentKey..."
    create_write_pool
    local create_pool_id="$WRITE_POOL_ID"
    local idem_key="ab-idem-create-${run_tail}"
    local create_raw="$results/${RUN_ID}_${AB_ROUND_LABEL}_create_raw.tsv"
    : > "$create_raw"

    local body started_at ended_at
    body=$(jq -n --argjson poolId "$create_pool_id" --arg type "0" --arg idempotentKey "$idem_key" \
        '{poolId:$poolId,type:0,idempotentKey:$idempotentKey}')
    started_at=$(date +%s)
    export BASE_URL AB_CURL_MAX_TIME
    export IDEM_WRITE_TOKEN="$write_token"
    export IDEM_CREATE_BODY="$body"
    export IDEM_CREATE_RAW="$create_raw"
    seq 1 "$REQUESTS" | xargs -P "$CONCURRENCY" -n 1 bash -c '
        tmp=$(mktemp)
        meta=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code} %{time_total}" -X POST "$BASE_URL/api/player/order/create" \
            -H "Authorization: Bearer $IDEM_WRITE_TOKEN" \
            -H "Content-Type: application/json" \
            -d "$IDEM_CREATE_BODY" || echo "000 ${AB_CURL_MAX_TIME}")
        http_code="${meta%% *}"
        time_total="${meta#* }"
        biz_code=$(jq -r ".code // \"N/A\"" "$tmp" 2>/dev/null || echo "N/A")
        order_no=$(jq -r ".data.orderNo // empty" "$tmp" 2>/dev/null || echo "")
        message=$(jq -r ".msg // .message // empty" "$tmp" 2>/dev/null || echo "")
        printf "%s\t%s\t%s\t%s\t%s\n" "$time_total" "$http_code" "$biz_code" "$order_no" "$message" >> "$IDEM_CREATE_RAW"
        rm -f "$tmp"
    ' _
    ended_at=$(date +%s)

    local create_ok distinct_orders create_500
    create_ok=$(awk '$2 == 200 && $3 == 200 {count++} END {print count + 0}' "$create_raw")
    create_500=$(awk '$2 != 200 {count++} END {print count + 0}' "$create_raw")
    distinct_orders=$(awk '$4 != "" {seen[$4]=1} END {for(k in seen) c++; print c+0}' "$create_raw")
    write_summary "$create_raw" "$results/${RUN_ID}_${AB_ROUND_LABEL}_create_summary.tsv" \
        "${SCENARIO}_create" "$REQUESTS" "$CONCURRENCY" "$started_at" "$ended_at" \
        '$2 == 200 && $3 == 200 {count++} END {print count + 0}'
    echo "[idempotent-create] ok=$create_ok/$REQUESTS 500=$create_500 distinct_orders=$distinct_orders"

    # --- Sub-scenario 2: Duplicate callback ---
    echo "[idempotent-callback] $REQUESTS concurrent requests, same channelTxnId..."

    # Create a fresh pool + order for callback
    WRITE_POOL_ID=""
    create_write_pool
    local cb_pool_id="$WRITE_POOL_ID"
    create_order_once "ab-idem-cb-${run_tail}"
    local cb_order_no="$ORDER_NO"
    local channel_id="ab-cb-${run_tail}"

    local cb_raw="$results/${RUN_ID}_${AB_ROUND_LABEL}_callback_raw.tsv"
    : > "$cb_raw"

    local cb_start cb_end
    cb_start=$(date +%s)
    local cb_body
    cb_body=$(jq -n \
        --arg orderNo "$cb_order_no" \
        --arg payRequestNo "pay-${channel_id}" \
        --arg callbackRequestNo "cb-${channel_id}" \
        --arg channelTxnId "$channel_id" \
        '{orderNo:$orderNo,payRequestNo:$payRequestNo,callbackRequestNo:$callbackRequestNo,channelTxnId:$channelTxnId,payStatus:"SUCCESS"}')
    export IDEM_CALLBACK_BODY="$cb_body"
    export IDEM_CALLBACK_RAW="$cb_raw"
    seq 1 "$REQUESTS" | xargs -P "$CONCURRENCY" -n 1 bash -c '
        tmp=$(mktemp)
        meta=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code} %{time_total}" -X POST "$BASE_URL/api/player/order/mock-callback" \
            -H "Authorization: Bearer $IDEM_WRITE_TOKEN" \
            -H "Content-Type: application/json" \
            -d "$IDEM_CALLBACK_BODY" || echo "000 ${AB_CURL_MAX_TIME}")
        http_code="${meta%% *}"
        time_total="${meta#* }"
        biz_code=$(jq -r ".code // \"N/A\"" "$tmp" 2>/dev/null || echo "N/A")
        message=$(jq -r ".msg // .message // empty" "$tmp" 2>/dev/null || echo "")
        printf "%s\t%s\t%s\t%s\n" "$time_total" "$http_code" "$biz_code" "$message" >> "$IDEM_CALLBACK_RAW"
        rm -f "$tmp"
    ' _
    cb_end=$(date +%s)

    local cb_ok cb_500
    cb_ok=$(awk '$2 == 200 && $3 == 200 {count++} END {print count + 0}' "$cb_raw")
    cb_500=$(awk '$2 != 200 {count++} END {print count + 0}' "$cb_raw")
    write_summary "$cb_raw" "$results/${RUN_ID}_${AB_ROUND_LABEL}_callback_summary.tsv" \
        "${SCENARIO}_callback" "$REQUESTS" "$CONCURRENCY" "$cb_start" "$cb_end" \
        '$2 == 200 && $3 == 200 {count++} END {print count + 0}'
    echo "[idempotent-callback] ok=$cb_ok/$REQUESTS 500=$cb_500"

    # Summary line for easy parsing
    echo "idempotent_result: create_ok=$create_ok create_500=$create_500 distinct_orders=$distinct_orders cb_ok=$cb_ok cb_500=$cb_500"
}

# Function defined. Called by ab_suite.sh via run_full_ab_test.
