#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/ab_common.sh"

# ========== CONFIG ==========
PLAYER_COUNT="${PLAYER_COUNT:-20}"
CONCURRENCY="${CONCURRENCY:-10}"
POOL_MAX_MEMBERS="${POOL_MAX_MEMBERS:-3}"
SCENARIO="oversell"
OVERSELL_PASSWORD="${OVERSELL_PASSWORD:-player123}"
OVERSELL_PASSWORD_HASH="${OVERSELL_PASSWORD_HASH:-\$2b\$10\$Fuf39z2Evtx59qO6yBXYiu7b.3lVm57Q6oQ2RnMXahwWHksVuSb.S}"
OVERSELL_TOKENS=()

seed_oversell_users() {
    local run_tail="$1"
    local count="$2"
    local sql_file
    sql_file=$(mktemp)

    {
        echo "USE \`${MYSQL_DATABASE:-script_murder_carpool}\`;"
        echo "INSERT INTO \`user\` (\`phone\`, \`password\`, \`nickname\`, \`gender\`, \`role\`, \`city\`, \`preference\`, \`credit_score\`, \`status\`) VALUES"
        for i in $(seq 0 "$count"); do
            local phone comma
            phone="13${run_tail}$(printf '%04d' "$i")"
            comma=","
            if [[ "$i" == "$count" ]]; then
                comma=";"
            fi
            printf "('%s','%s','AB压测-%s-%s',0,0,'上海','硬核,机制',100,1)%s\n" \
                "$phone" "$OVERSELL_PASSWORD_HASH" "$run_tail" "$i" "$comma"
        done
    } > "$sql_file"

    if [[ "${AB_BACKEND_MODE:-local}" == "remote" ]]; then
        remote_required
        ssh -o BatchMode=yes -o ConnectTimeout=10 "$REMOTE_SSH" \
            "$REMOTE_DOCKER_CMD exec -i '$REMOTE_MYSQL_CONTAINER' mysql -u'$REMOTE_MYSQL_USER' -p'$REMOTE_MYSQL_PASSWORD' --default-character-set=utf8mb4" < "$sql_file"
    else
        docker exec -i "${MYSQL_CONTAINER:-jp-mysql}" mysql \
            -u"${MYSQL_USER:-root}" -p"${MYSQL_PASSWORD:-root}" \
            --default-character-set=utf8mb4 < "$sql_file"
    fi

    rm -f "$sql_file"
}

login_oversell_users() {
    local run_tail="$1"
    local count="$2"

    OVERSELL_TOKENS=()
    for i in $(seq 0 "$count"); do
        local phone body resp token
        phone="13${run_tail}$(printf '%04d' "$i")"
        body=$(jq -n --arg phone "$phone" --arg password "$OVERSELL_PASSWORD" \
            '{phone:$phone,password:$password,role:"player"}')
        resp=$(plain_post_json "/api/auth/login" "$body")
        token=$(echo "$resp" | jq -r '.data.accessToken // empty')
        if [[ -z "$token" ]]; then
            echo "[oversell] login failed for preseeded phone=$phone response=$resp" >&2
            exit 1
        fi
        OVERSELL_TOKENS+=("$token")
    done
}

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

    # 1. Pre-seed owner + players. Only the player set participates in the
    # high-concurrency join pressure section; setup time is not included.
    echo "[oversell] Pre-seeding 1 owner + $PLAYER_COUNT players..."
    local tokens=()
    seed_oversell_users "$run_tail" "$PLAYER_COUNT"
    login_oversell_users "$run_tail" "$PLAYER_COUNT"
    tokens=("${OVERSELL_TOKENS[@]}")

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
    for i in $(seq 1 "$PLAYER_COUNT"); do
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

    # 5. DB consistency check. Seat occupation is defined as PENDING_PAYMENT + JOINED.
    local container="${MYSQL_CONTAINER:-jp-mysql}"
    local db="${MYSQL_DATABASE:-script_murder_carpool}"
    local sql="
        SELECT 'pending_payment_count' AS metric, COUNT(*) AS val FROM pool_member WHERE pool_id = $pool_id AND status = 1;
        SELECT 'joined_count' AS metric, COUNT(*) AS val FROM pool_member WHERE pool_id = $pool_id AND status = 2;
        SELECT 'locked_count' AS metric, COUNT(*) AS val FROM pool_member WHERE pool_id = $pool_id AND status IN (1, 2);
        SELECT 'current_members' AS metric, current_members AS val FROM car_pool WHERE id = $pool_id;
        SELECT 'drift' AS metric, cp.current_members - COALESCE(actual.cnt, 0) AS val
        FROM car_pool cp
        LEFT JOIN (SELECT pool_id, COUNT(*) AS cnt FROM pool_member WHERE status IN (1, 2) GROUP BY pool_id) actual
        ON actual.pool_id = cp.id WHERE cp.id = $pool_id;
        SELECT 'duplicate_member_count' AS metric, COUNT(*) AS val FROM (
          SELECT pool_id, user_id FROM pool_member WHERE pool_id = $pool_id GROUP BY pool_id, user_id HAVING COUNT(*) > 1
        ) t;
        SELECT 'oversell' AS metric, CASE WHEN COUNT(*) > $POOL_MAX_MEMBERS THEN COUNT(*) - $POOL_MAX_MEMBERS ELSE 0 END AS val
        FROM pool_member WHERE pool_id = $pool_id AND status IN (1, 2);
    "
    if [[ "${AB_BACKEND_MODE:-local}" == "remote" ]]; then
        remote_exec "$REMOTE_DOCKER_CMD exec -i '$REMOTE_MYSQL_CONTAINER' mysql -u'$REMOTE_MYSQL_USER' -p'$REMOTE_MYSQL_PASSWORD' --default-character-set=utf8mb4 '$REMOTE_MYSQL_DATABASE' -e \"$sql\"" > "$consistency_file"
    else
        docker exec -i "$container" mysql \
            -u"${MYSQL_USER:-root}" -p"${MYSQL_PASSWORD:-root}" \
            --default-character-set=utf8mb4 "$db" \
            -e "$sql" > "$consistency_file"
    fi

    local locked_count oversell_count
    locked_count=$(awk '$1 == "locked_count" {print $2}' "$consistency_file")
    oversell_count=$(awk '$1 == "oversell" {print $2}' "$consistency_file")
    echo "[oversell] join_ok=$joined_count locked=$locked_count max=$POOL_MAX_MEMBERS oversell=$oversell_count"
    cat "$consistency_file"

    # Return non-zero if oversold (for A variants this is expected; the comparison report handles it)
    if [[ "$oversell_count" -gt 0 ]]; then
        echo "[oversell] OVERSOLD: locked=$locked_count > $POOL_MAX_MEMBERS"
    fi
}

# Function defined. Called by ab_suite.sh via run_full_ab_test.
