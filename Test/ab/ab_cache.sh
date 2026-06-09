#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/ab_common.sh"

# ========== CONFIG ==========
REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
POOL_ID="${POOL_ID:-1}"
SCENARIO="cache"
NONEXIST_IDS=(999999001 999999002 999999003 999999004 999999005 999999006 999999007 999999008 999999009 999999010)

# ========== HELPERS ==========
fetch_pool() {
    local target_id=$1
    local tmp meta http_code time_total biz_code
    tmp=$(mktemp)
    meta=$(curl -sS -o "$tmp" -w "%{http_code} %{time_total}" "$BASE_URL/api/player/pool/$target_id")
    http_code="${meta%% *}"
    time_total="${meta#* }"
    biz_code=$(jq -r '.code // "N/A"' "$tmp" 2>/dev/null || echo "N/A")
    printf "%s\t%s\t%s\t%s\n" "$time_total" "$http_code" "$biz_code" "$target_id"
    rm -f "$tmp"
}

run_cache_sub() {
    local name="$1"      # happy / mixed / penetration
    local generator="$2" # function name that prints target ID per request
    local expected="$3"  # happy | mixed | penetration
    local raw="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_${name}_raw.tsv"
    : > "$raw"

    local started_at ended_at running
    started_at=$(date +%s)
    running=0
    for i in $(seq 1 "$REQUESTS"); do
        local target_id
        target_id=$($generator "$i")
        (
            fetch_pool "$target_id" >> "$raw"
        ) &
        running=$((running + 1))
        if (( running >= CONCURRENCY )); then wait; running=0; fi
    done
    wait
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

# ========== ID GENERATORS ==========
gen_existing() { echo "$POOL_ID"; }

gen_mixed() {
    local i=$1
    if (( i % 2 == 0 )); then
        echo "$POOL_ID"
    else
        local idx=$(( (i / 2) % ${#NONEXIST_IDS[@]} ))
        echo "${NONEXIST_IDS[$idx]}"
    fi
}

gen_penetration() {
    local i=$1
    local idx=$(( (i - 1) % ${#NONEXIST_IDS[@]} ))
    echo "${NONEXIST_IDS[$idx]}"
}

# ========== SINGLE-ROUND TEST ==========
run_cache_round() {
    local results="$RESULTS_DIR"
    mkdir -p "$results"

    echo "===== CACHE ROUND: $AB_ROUND_LABEL ====="
    echo "[cache] requests=$REQUESTS concurrency=$CONCURRENCY pool_id=$POOL_ID"

    echo "--- [1/3] Happy path (100% existing ID) ---"
    run_cache_sub "happy" gen_existing happy

    echo "--- [2/3] Mixed (50% existing / 50% non-existent) ---"
    run_cache_sub "mixed" gen_mixed mixed

    echo "--- [3/3] Penetration (100% non-existent, cycled) ---"
    run_cache_sub "penetration" gen_penetration penetration

    # Penetration analysis: first request per ID vs subsequent
    local pen_raw="$RESULTS_DIR/${RUN_ID}_${AB_ROUND_LABEL}_penetration_raw.tsv"
    echo ""
    echo "--- Penetration analysis ---"
    local total_slow=0 unique_count=${#NONEXIST_IDS[@]}
    for nid in "${NONEXIST_IDS[@]}"; do
        local count first_req
        count=$(awk -v id="$nid" '$4 == id' "$pen_raw" | wc -l | tr -d ' ')
        first_req=$(awk -v id="$nid" '$4 == id {if (!seen) {print $1; seen=1}}' "$pen_raw")
        echo "  ID=$nid  requests=$count  first_req=${first_req:-N/A}s"
        if [[ -n "$first_req" ]]; then
            total_slow=$(awk -v x="$total_slow" -v y="$first_req" 'BEGIN {print x + y}')
        fi
    done
    echo "  estimated_db_hits=$unique_count (first per ID)"
    echo "  null_cache_hits=$(( REQUESTS - unique_count )) (repeats)"
    echo "  avg_first_hit_time=$(awk -v s="$total_slow" -v n="$unique_count" 'BEGIN {printf "%.4f", s / n}')s"
}

# Function defined. Called by ab_suite.sh via run_full_ab_test.
