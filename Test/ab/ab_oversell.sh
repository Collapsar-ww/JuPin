#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/ab_common.sh"

# ========== CONFIG ==========
PLAYER_COUNT="${PLAYER_COUNT:-20}"
CONCURRENCY="${CONCURRENCY:-10}"
POOL_MAX_MEMBERS="${POOL_MAX_MEMBERS:-3}"
SCENARIO="oversell"

# ========== SINGLE-ROUND TEST ==========
run_oversell_round() {
    local results="$RESULTS_DIR"
    mkdir -p "$results"

    local raw="$results/${RUN_ID}_${AB_ROUND_LABEL}_raw.tsv"
    local pay_raw="$results/${RUN_ID}_${AB_ROUND_LABEL}_pay_raw.tsv"
    local consistency_file="$results/${RUN_ID}_${AB_ROUND_LABEL}_consistency.tsv"
    : > "$raw"
    : > "$pay_raw"

    # Generate a 6-digit suffix from the full label so A/B rounds never reuse users or idempotent keys.
    local run_tail
    run_tail=$(printf "%s" "${RUN_ID}_${AB_ROUND_LABEL}" | cksum | awk '{printf "%06d", $1 % 1000000}')

    # 1. Register players
    echo "[oversell] Registering $PLAYER_COUNT players..."
    local tokens=()
    for i in $(seq 1 "$PLAYER_COUNT"); do
        local seq_num phone
        seq_num=$(printf '%03d' "$i")
        phone="13${run_tail}${seq_num}"

        local body resp token
        body=$(jq -n --arg phone "$phone" --arg password "player123" \
            --arg nickname "AB压测-${run_tail}-${i}" \
            '{phone:$phone,password:$password,nickname:$nickname,gender:0,role:"player",city:"上海"}')
        resp=$(plain_post_json "/api/auth/register" "$body")
        token=$(echo "$resp" | jq -r '.data.accessToken // empty')
        if [[ -z "$token" ]]; then
            body=$(jq -n --arg phone "$phone" --arg password "player123" '{phone:$phone,password:$password,role:"player"}')
            resp=$(plain_post_json "/api/auth/login" "$body")
            token=$(echo "$resp" | jq -r '.data.accessToken // empty')
        fi
        tokens+=("$token")
    done

    # 2. Create a small-capacity pool (owner = first player)
    local write_token="${tokens[0]}"
    local body resp pool_id
    body=$(jq -n \
        --arg scriptName "AB超员压测-${run_tail}" \
        --arg roles '[{"name":"角色1","desc":"压测"},{"name":"角色2","desc":"压测"}]' \
        --argjson maxMembers "$POOL_MAX_MEMBERS" \
        '{type:0,scriptId:1,scriptName:$scriptName,scriptType:"硬核",
          roles:$roles,city:"上海",address:"压测地址",
          startTime:"2026-07-20 14:00:00",endTime:"2026-07-20 17:00:00",
          maxMembers:$maxMembers,price:88,deposit:10,joinType:1}')
    resp=$(auth_post_json "/api/player/pool/create" "$body" "$write_token")
    assert_code_200 "create oversell pool" "$resp"
    pool_id=$(echo "$resp" | jq -r '.data.id // empty')
    echo "[oversell] pool_id=$pool_id max_members=$POOL_MAX_MEMBERS"

    # 3. All players concurrently join
    echo "[oversell] $PLAYER_COUNT players joining concurrently (concurrency=$CONCURRENCY)..."
    local started_at ended_at
    started_at=$(date +%s)
    local token_file
    token_file=$(mktemp)
    for i in $(seq 0 $((PLAYER_COUNT - 1))); do
        printf "%s\t%s\n" "$i" "${tokens[$i]}" >> "$token_file"
    done
    export BASE_URL AB_CURL_MAX_TIME
    export RAW_FILE="$raw"
    export POOL_ID_ARG="$pool_id"
    xargs -P "$CONCURRENCY" -n 2 bash -c '
        idx="$1"
        token="$2"
        tmp=$(mktemp)
        start_ns=$(date +%s%N)
        http_code=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code}" -X POST "$BASE_URL/api/player/pool/$POOL_ID_ARG/join" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" || echo "000")
        end_ns=$(date +%s%N)
        time_total=$(awk "BEGIN {printf \"%.6f\", ($end_ns - $start_ns) / 1000000000}")
        biz_code=$(jq -r ".code // \"N/A\"" "$tmp" 2>/dev/null || echo "N/A")
        printf "%s\t%s\t%s\t%s\n" "$time_total" "$http_code" "$biz_code" "$idx" >> "$RAW_FILE"
        rm -f "$tmp"
    ' _ < "$token_file"
    rm -f "$token_file"
    ended_at=$(date +%s)

    # 4. Collect join results
    local joined_count=0
    local joined_indices=()
    local joined_tokens=()
    while IFS=$'\t' read -r time http code idx _; do
        if [[ "$http" == "200" && "$code" == "200" ]]; then
            joined_count=$((joined_count + 1))
            joined_indices+=("$idx")
            joined_tokens+=("${tokens[$idx]}")
        fi
    done < "$raw"

    write_summary "$raw" "$results/${RUN_ID}_${AB_ROUND_LABEL}_summary.tsv" "${SCENARIO}_join" \
        "$PLAYER_COUNT" "$CONCURRENCY" "$started_at" "$ended_at" \
        '$2 == 200 && $3 == 200 {count++} END {print count + 0}'
    echo "[oversell] join_ok=$joined_count / $PLAYER_COUNT"

    # 5. Each joined player creates order + pays
    local pay_ok=0
    if [[ $joined_count -gt 0 ]]; then
        echo "[oversell] $joined_count joined players creating orders + paying..."
        local pay_start pay_end
        pay_start=$(date +%s)

        local joined_file
        joined_file=$(mktemp)
        for i in "${!joined_indices[@]}"; do
            local idx="${joined_indices[$i]}"
            local token="${joined_tokens[$i]}"
            printf "%s\t%s\n" "$idx" "$token" >> "$joined_file"
        done
        export PAY_RAW_FILE="$pay_raw"
        export RUN_TAIL_ARG="$run_tail"
        export POOL_ID_ARG="$pool_id"
        xargs -P "$CONCURRENCY" -n 2 bash -c '
            idx="$1"
            token="$2"
            tmp=$(mktemp)
            order_body=$(jq -n --argjson poolId "$POOL_ID_ARG" --arg type "0" \
                --arg idempotentKey "oversell-${RUN_TAIL_ARG}-${idx}" \
                "{poolId:\$poolId,type:0,idempotentKey:\$idempotentKey}")
            http_code=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code}" -X POST "$BASE_URL/api/player/order/create" \
                -H "Authorization: Bearer $token" \
                -H "Content-Type: application/json" \
                -d "$order_body" || echo "000")
            biz_code=$(jq -r ".code // \"N/A\"" "$tmp" 2>/dev/null || echo "N/A")
            order_no=$(jq -r ".data.orderNo // empty" "$tmp" 2>/dev/null || echo "")

            if [[ "$http_code" != "200" || "$biz_code" != "200" || -z "$order_no" ]]; then
                printf "ORDER_FAIL\t%s\t%s\t%s\n" "$http_code" "$biz_code" "$idx" >> "$PAY_RAW_FILE"
                rm -f "$tmp"
                exit 0
            fi

            tmp=$(mktemp)
            http_code=$(curl -sS --max-time "$AB_CURL_MAX_TIME" -o "$tmp" -w "%{http_code}" -X POST "$BASE_URL/api/player/order/pay/$order_no" \
                -H "Authorization: Bearer $token" \
                -H "Content-Type: application/json" \
                -d "{}" || echo "000")
            biz_code=$(jq -r ".code // \"N/A\"" "$tmp" 2>/dev/null || echo "N/A")
            printf "PAY\t%s\t%s\t%s\t%s\n" "$http_code" "$biz_code" "$order_no" "$idx" >> "$PAY_RAW_FILE"
            rm -f "$tmp"
        ' _ < "$joined_file"
        rm -f "$joined_file"
        pay_end=$(date +%s)

        # Count successful payments
        while IFS=$'\t' read -r typ http code _ __; do
            if [[ "$typ" == "PAY" && "$http" == "200" && "$code" == "200" ]]; then
                pay_ok=$((pay_ok + 1))
            fi
        done < "$pay_raw"
    fi

    # 6. DB consistency check
    local container="${MYSQL_CONTAINER:-jp-mysql}"
    local db="${MYSQL_DATABASE:-script_murder_carpool}"
    docker exec -i "$container" mysql \
        -u"${MYSQL_USER:-root}" -p"${MYSQL_PASSWORD:-root}" \
        --default-character-set=utf8mb4 "$db" \
        -e "
        SELECT 'joined_count' AS metric, COUNT(*) AS val FROM pool_member WHERE pool_id = $pool_id AND status = 2;
        SELECT 'current_members' AS metric, current_members AS val FROM car_pool WHERE id = $pool_id;
        SELECT 'drift' AS metric, cp.current_members - COALESCE(actual.cnt, 0) AS val
        FROM car_pool cp
        LEFT JOIN (SELECT pool_id, COUNT(*) AS cnt FROM pool_member WHERE status = 2 GROUP BY pool_id) actual
        ON actual.pool_id = cp.id WHERE cp.id = $pool_id;
        SELECT 'duplicate_member_count' AS metric, COUNT(*) AS val FROM (
          SELECT pool_id, user_id FROM pool_member WHERE pool_id = $pool_id GROUP BY pool_id, user_id HAVING COUNT(*) > 1
        ) t;
        SELECT 'oversell' AS metric, CASE WHEN COUNT(*) > $POOL_MAX_MEMBERS THEN COUNT(*) - $POOL_MAX_MEMBERS ELSE 0 END AS val
        FROM pool_member WHERE pool_id = $pool_id AND status = 2;
    " > "$consistency_file"

    echo "[oversell] pay_ok=$pay_ok max=$POOL_MAX_MEMBERS oversell=$(( pay_ok > POOL_MAX_MEMBERS ? pay_ok - POOL_MAX_MEMBERS : 0 ))"
    cat "$consistency_file"

    # Return non-zero if oversold (for A variants this is expected; the comparison report handles it)
    if [[ "$pay_ok" -gt "$POOL_MAX_MEMBERS" ]]; then
        echo "[oversell] OVERSOLD: $pay_ok > $POOL_MAX_MEMBERS"
    fi
}

# Function defined. Called by ab_suite.sh via run_full_ab_test.
