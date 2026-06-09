#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/ab_common.sh"

# ========== CONFIG ==========
REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
POOL_ID="${POOL_ID:-1}"
SCENARIO="cache"
MIXED_NONEXIST_IDS=(999998001 999998002 999998003 999998004 999998005 999998006 999998007 999998008 999998009 999998010)
REPEAT_NONEXIST_IDS=(999999001 999999002 999999003 999999004 999999005 999999006 999999007 999999008 999999009 999999010)
CACHE_UNIQUE_BASE="${CACHE_UNIQUE_BASE:-900000000}"
REDIS_CONTAINER="${REDIS_CONTAINER:-jp-redis}"

# ========== HELPERS ==========
fetch_pool() {
    local target_id=$1
    local tmp meta http_code time_total biz_code
    tmp=$(mktemp)
    meta=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code} %{time_total}" "$BASE_URL/api/player/pool/$target_id" || echo "000 ${AB_CURL_MAX_TIME}")
    http_code="${meta%% *}"
    time_total="${meta#* }"
    biz_code=$(jq -r '.code // "N/A"' "$tmp" 2>/dev/null || echo "N/A")
    printf "%s\t%s\t%s\t%s\n" "$time_total" "$http_code" "$biz_code" "$target_id"
    rm -f "$tmp"
}

run_cache_sub() {
    local name="$1"      # happy / mixed / penetration_repeat_* / penetration_unique
    local generator="$2" # function name that prints target ID per request
    local expected="$3"  # happy | mixed | penetration
    local raw="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_${name}_raw.tsv"
    : > "$raw"

    local started_at ended_at targets_file
    started_at=$(date +%s)
    targets_file=$(mktemp)
    for i in $(seq 1 "$REQUESTS"); do
        local target_id
        target_id=$($generator "$i")
        echo "$target_id" >> "$targets_file"
    done
    export BASE_URL AB_CURL_MAX_TIME
    export CACHE_RAW_FILE="$raw"
    xargs -P "$CONCURRENCY" -n 1 bash -c '
        target_id="$1"
        tmp=$(mktemp)
        meta=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code} %{time_total}" "$BASE_URL/api/player/pool/$target_id" || echo "000 ${AB_CURL_MAX_TIME}")
        http_code="${meta%% *}"
        time_total="${meta#* }"
        biz_code=$(jq -r ".code // \"N/A\"" "$tmp" 2>/dev/null || echo "N/A")
        printf "%s\t%s\t%s\t%s\n" "$time_total" "$http_code" "$biz_code" "$target_id" >> "$CACHE_RAW_FILE"
        rm -f "$tmp"
    ' _ < "$targets_file"
    rm -f "$targets_file"
    ended_at=$(date +%s)

    local total ok_200 biz_200
    total=$(wc -l < "$raw" | tr -d ' ')
    ok_200=$(awk '$2 == "200" {count++} END {print count + 0}' "$raw")
    biz_200=$(awk '$3 == "200" {count++} END {print count + 0}' "$raw")

    echo "${name}_total=$total http_200=$ok_200 biz_200=$biz_200"

    write_summary "$raw" "$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_${name}_summary.tsv" \
        "${SCENARIO}_${name}" "$REQUESTS" "$CONCURRENCY" "$started_at" "$ended_at" \
        '$3 == "200" {count++} END {print count + 0}'

    if [[ "$ok_200" != "$REQUESTS" ]]; then
        echo "cache $name: http_200=$ok_200 != $REQUESTS" >&2
        return 1
    fi
    if [[ "$expected" == "happy" && "$biz_200" != "$REQUESTS" ]]; then
        echo "cache $name: biz_200=$biz_200 != $REQUESTS" >&2
        return 1
    fi
    if [[ "$expected" == "penetration" && "$biz_200" != "0" ]]; then
        echo "cache $name: non-existent IDs returned success: biz_200=$biz_200" >&2
        return 1
    fi
}

count_null_cache_keys() {
    local count=0
    local id val
    for id in "$@"; do
        val=$(docker exec "$REDIS_CONTAINER" redis-cli GET "pool:detail:${id}" 2>/dev/null | tr -d '\r' || true)
        if [[ "$val" == "__NULL__" ]]; then
            count=$((count + 1))
        fi
    done
    echo "$count"
}

# ========== ID GENERATORS ==========
gen_existing() { echo "$POOL_ID"; }

gen_mixed() {
    local i=$1
    if (( i % 2 == 0 )); then
        echo "$POOL_ID"
    else
        local idx=$(( (i / 2) % ${#MIXED_NONEXIST_IDS[@]} ))
        echo "${MIXED_NONEXIST_IDS[$idx]}"
    fi
}

gen_penetration_repeat() {
    local i=$1
    local idx=$(( (i - 1) % ${#REPEAT_NONEXIST_IDS[@]} ))
    echo "${REPEAT_NONEXIST_IDS[$idx]}"
}

gen_penetration_unique() {
    local i=$1
    echo $((CACHE_UNIQUE_BASE + i))
}

# ========== SINGLE-ROUND TEST ==========
run_cache_round() {
    local results="$RESULTS_DIR"
    mkdir -p "$results"

    echo "===== CACHE ROUND: $AB_ROUND_LABEL ====="
    echo "[cache] requests=$REQUESTS concurrency=$CONCURRENCY pool_id=$POOL_ID unique_base=$CACHE_UNIQUE_BASE"

    echo "--- [1/5] Happy path (100% existing ID) ---"
    run_cache_sub "happy" gen_existing happy

    echo "--- [2/5] Mixed (50% existing / 50% repeated non-existent) ---"
    run_cache_sub "mixed" gen_mixed mixed

    echo "--- [3/5] Penetration repeat cold (100% non-existent, 10 IDs cycled) ---"
    run_cache_sub "penetration_repeat_cold" gen_penetration_repeat penetration

    echo "--- [4/5] Penetration repeat warm (same 10 non-existent IDs, after null cache should exist) ---"
    run_cache_sub "penetration_repeat_warm" gen_penetration_repeat penetration

    echo "--- [5/5] Penetration unique (100% non-existent, each request different ID) ---"
    run_cache_sub "penetration_unique" gen_penetration_unique penetration

    # Penetration analysis: first request per ID vs subsequent
    local pen_raw="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_penetration_repeat_cold_raw.tsv"
    local warm_raw="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_penetration_repeat_warm_raw.tsv"
    local unique_raw="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_penetration_unique_raw.tsv"
    local analysis_file="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_penetration_analysis.tsv"
    echo ""
    echo "--- Penetration analysis ---"
    local total_slow=0 unique_count=${#REPEAT_NONEXIST_IDS[@]} null_cache_keys
    for nid in "${REPEAT_NONEXIST_IDS[@]}"; do
        local count first_req
        count=$(awk -v id="$nid" '$4 == id' "$pen_raw" | wc -l | tr -d ' ')
        first_req=$(awk -v id="$nid" '$4 == id {if (!seen) {print $1; seen=1}}' "$pen_raw")
        echo "  ID=$nid  requests=$count  first_req=${first_req:-N/A}s"
        if [[ -n "$first_req" ]]; then
            total_slow=$(awk -v x="$total_slow" -v y="$first_req" 'BEGIN {print x + y}')
        fi
    done
    null_cache_keys=$(count_null_cache_keys "${REPEAT_NONEXIST_IDS[@]}")
    local cold_p95 warm_p95 unique_p95
    cold_p95=$(pct_from_file "$pen_raw" 0.95)
    warm_p95=$(pct_from_file "$warm_raw" 0.95)
    unique_p95=$(pct_from_file "$unique_raw" 0.95)
    echo "  repeated_nonexistent_ids=$unique_count"
    echo "  redis_null_cache_keys=$null_cache_keys/$unique_count"
    echo "  warm_null_cache_eligible_requests=$REQUESTS"
    echo "  avg_first_hit_time=$(awk -v s="$total_slow" -v n="$unique_count" 'BEGIN {printf "%.4f", s / n}')s"
    echo "  cold_p95=${cold_p95}s warm_p95=${warm_p95}s unique_p95=${unique_p95}s"
    {
        echo -e "run_id\tlabel\tvariant\trepeated_ids\trequests\tconcurrency\tredis_null_cache_keys\tcold_p95_seconds\twarm_p95_seconds\tunique_p95_seconds\twarm_null_cache_eligible_requests"
        echo -e "${RUN_ID}\t${AB_ROUND_LABEL}\t${AB_VARIANT:-}\t${unique_count}\t${REQUESTS}\t${CONCURRENCY}\t${null_cache_keys}\t${cold_p95}\t${warm_p95}\t${unique_p95}\t${REQUESTS}"
    } > "$analysis_file"
    cat "$analysis_file"
}

# Function defined. Called by ab_suite.sh via run_full_ab_test.
