#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PLAYER_PHONE="${PLAYER_PHONE:-13800000001}"
PLAYER_PASSWORD="${PLAYER_PASSWORD:-player123}"
PLAYER_ROLE="${PLAYER_ROLE:-player}"
PLAYER_TOKEN="${PLAYER_TOKEN:-}"
POOL_ID="${POOL_ID:-}"
WRITE_PHONE="${WRITE_PHONE:-}"
WRITE_PASSWORD="${WRITE_PASSWORD:-player123}"
WRITE_TOKEN="${WRITE_TOKEN:-}"
WRITE_POOL_ID="${WRITE_POOL_ID:-}"
ORDER_TYPE="${ORDER_TYPE:-0}"
REQUESTS="${REQUESTS:-50}"
CONCURRENCY="${CONCURRENCY:-10}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

need_cmd curl
need_cmd jq

if [[ -z "$POOL_ID" ]]; then
  echo "POOL_ID is required. Example: POOL_ID=1 $0" >&2
  exit 1
fi

post_json() {
  local path="$1"
  local body="$2"
  if [[ -n "$PLAYER_TOKEN" ]]; then
    curl -sS -X POST "$BASE_URL$path" \
      -H "Authorization: Bearer $PLAYER_TOKEN" \
      -H "Content-Type: application/json" \
      -d "$body"
  else
    curl -sS -X POST "$BASE_URL$path" \
      -H "Content-Type: application/json" \
      -d "$body"
  fi
}

plain_post_json() {
  local path="$1"
  local body="$2"
  curl -sS -X POST "$BASE_URL$path" \
    -H "Content-Type: application/json" \
    -d "$body"
}

auth_post_json() {
  local path="$1"
  local body="$2"
  local token="${3:-$PLAYER_TOKEN}"
  curl -sS -X POST "$BASE_URL$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

ensure_token() {
  if [[ -n "$PLAYER_TOKEN" ]]; then
    return
  fi

  local body
  body=$(jq -n \
    --arg phone "$PLAYER_PHONE" \
    --arg password "$PLAYER_PASSWORD" \
    --arg role "$PLAYER_ROLE" \
    '{phone:$phone,password:$password,role:$role}')

  local resp
  resp=$(plain_post_json "/api/auth/login" "$body")
  PLAYER_TOKEN=$(echo "$resp" | jq -r '.data.accessToken // empty')

  if [[ -z "$PLAYER_TOKEN" ]]; then
    echo "Login failed. Response:" >&2
    echo "$resp" >&2
    exit 1
  fi
}

make_write_phone() {
  if [[ -n "$WRITE_PHONE" ]]; then
    return
  fi

  local digits suffix
  digits=$(printf "%s" "$RUN_ID" | tr -cd '0-9')
  suffix="${digits: -9}"
  while (( ${#suffix} < 9 )); do
    suffix="0$suffix"
  done
  WRITE_PHONE="13$suffix"
}

login_for_write() {
  local body resp
  body=$(jq -n \
    --arg phone "$WRITE_PHONE" \
    --arg password "$WRITE_PASSWORD" \
    '{phone:$phone,password:$password,role:"player"}')
  resp=$(plain_post_json "/api/auth/login" "$body")
  WRITE_TOKEN=$(echo "$resp" | jq -r '.data.accessToken // empty')
  if [[ -z "$WRITE_TOKEN" ]]; then
    echo "Write user login failed. Response:" >&2
    echo "$resp" >&2
    exit 1
  fi
}

ensure_write_user() {
  if [[ -n "$WRITE_TOKEN" ]]; then
    return
  fi

  make_write_phone

  local body resp token
  body=$(jq -n \
    --arg phone "$WRITE_PHONE" \
    --arg password "$WRITE_PASSWORD" \
    --arg nickname "压测用户${RUN_ID}" \
    '{phone:$phone,password:$password,nickname:$nickname,gender:0,role:"player",city:"上海"}')
  resp=$(plain_post_json "/api/auth/register" "$body")
  token=$(echo "$resp" | jq -r '.data.accessToken // empty')

  if [[ -n "$token" ]]; then
    WRITE_TOKEN="$token"
  else
    login_for_write
  fi

  echo "write_user_phone=$WRITE_PHONE"
}

create_write_pool() {
  if [[ -n "$WRITE_POOL_ID" ]]; then
    return
  fi

  local body resp pool_id
  body=$(jq -n \
    --arg scriptName "压测临时车局-${RUN_ID}" \
    --arg roles '[{"name":"角色1","desc":"压测角色"},{"name":"角色2","desc":"压测角色"}]' \
    '{
      type:0,
      scriptId:1,
      scriptName:$scriptName,
      scriptType:"硬核",
      roles:$roles,
      city:"上海",
      address:"压测测试地址",
      startTime:"2026-07-20 14:00:00",
      endTime:"2026-07-20 17:00:00",
      maxMembers:6,
      price:88,
      deposit:10,
      joinType:1
    }')

  resp=$(auth_post_json "/api/player/pool/create" "$body" "$WRITE_TOKEN")
  assert_code_200 "create write pool" "$resp"

  pool_id=$(echo "$resp" | jq -r '.data.id // empty')
  if [[ -z "$pool_id" ]]; then
    echo "Create write pool failed: missing pool id." >&2
    echo "$resp" >&2
    exit 1
  fi

  WRITE_POOL_ID="$pool_id"
  echo "write_pool_id=$WRITE_POOL_ID"
}

assert_code_200() {
  local name="$1"
  local resp="$2"
  local code
  code=$(echo "$resp" | jq -r '.code // empty')
  if [[ "$code" != "200" ]]; then
    echo "Scenario failed: $name" >&2
    echo "$resp" >&2
    exit 1
  fi
}

p95_from_file() {
  local file="$1"
  awk '{print $1}' "$file" | sort -n | awk '
    {a[NR]=$1}
    END {
      if (NR == 0) {
        print "0"
      } else {
        idx = int(NR * 0.95)
        if (idx < 1) idx = 1
        if (idx > NR) idx = NR
        printf "%.4f", a[idx]
      }
    }'
}

run_pool_detail_load() {
  local tmp_file
  tmp_file="/tmp/jupin_pool_detail_${RUN_ID}.txt"
  : > "$tmp_file"

  echo "[1/5] pool detail read load: requests=$REQUESTS concurrency=$CONCURRENCY"

  local running=0
  for i in $(seq 1 "$REQUESTS"); do
    (
      curl -sS -o /dev/null -w "%{time_total} %{http_code}\n" \
        "$BASE_URL/api/player/pool/$POOL_ID"
    ) >> "$tmp_file" &

    running=$((running + 1))
    if (( running >= CONCURRENCY )); then
      wait
      running=0
    fi
  done
  wait

  local total ok p95
  total=$(wc -l < "$tmp_file" | tr -d ' ')
  ok=$(awk '$2 == 200 {count++} END {print count + 0}' "$tmp_file")
  p95=$(p95_from_file "$tmp_file")

  echo "pool_detail_total=$total"
  echo "pool_detail_http_200=$ok"
  echo "pool_detail_p95_seconds=$p95"

  if [[ "$ok" != "$total" ]]; then
    echo "pool_detail_status_counts=$(awk '{count[$2]++} END {for (status in count) printf "%s:%s ", status, count[status]}' "$tmp_file")"
    echo "Pool detail load failed: HTTP 200 count does not match total requests." >&2
    exit 1
  fi
}

run_unauthorized_direct_create() {
  echo "[2/5] unauthorized direct order create should be rejected"

  local body status
  body=$(jq -n --argjson poolId "$POOL_ID" --argjson type "$ORDER_TYPE" \
    '{poolId:$poolId,type:$type,idempotentKey:"unauthorized-direct"}')

  status=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/player/order/create" \
    -H "Content-Type: application/json" \
    -d "$body")

  echo "unauthorized_direct_create_http_status=$status"
  if [[ "$status" == "200" ]]; then
    echo "Unauthorized direct order creation returned HTTP 200." >&2
    exit 1
  fi
}

run_idempotent_create() {
  echo "[3/5] idempotent order create"

  local idem_key body resp1 resp2 order1 order2
  idem_key="test-${RUN_ID}-pool-${WRITE_POOL_ID}-type-${ORDER_TYPE}"
  body=$(jq -n \
    --argjson poolId "$WRITE_POOL_ID" \
    --argjson type "$ORDER_TYPE" \
    --arg idempotentKey "$idem_key" \
    '{poolId:$poolId,type:$type,idempotentKey:$idempotentKey}')

  resp1=$(auth_post_json "/api/player/order/create" "$body" "$WRITE_TOKEN")
  resp2=$(auth_post_json "/api/player/order/create" "$body" "$WRITE_TOKEN")

  assert_code_200 "idempotent create first request" "$resp1"
  assert_code_200 "idempotent create second request" "$resp2"

  order1=$(echo "$resp1" | jq -r '.data.orderNo // empty')
  order2=$(echo "$resp2" | jq -r '.data.orderNo // empty')

  if [[ -z "$order1" || "$order1" != "$order2" ]]; then
    echo "Idempotent create failed: order numbers differ." >&2
    echo "first=$order1 second=$order2" >&2
    exit 1
  fi

  ORDER_NO="$order1"
  echo "created_order_no=$ORDER_NO"
  echo "idempotent_key=$idem_key"
}

run_duplicate_pay() {
  echo "[4/5] duplicate pay"

  local resp1 resp2
  resp1=$(auth_post_json "/api/player/order/pay/$ORDER_NO" "{}" "$WRITE_TOKEN")
  resp2=$(auth_post_json "/api/player/order/pay/$ORDER_NO" "{}" "$WRITE_TOKEN")

  assert_code_200 "pay first request" "$resp1"
  assert_code_200 "pay duplicate request" "$resp2"

  echo "duplicate_pay_order_no=$ORDER_NO"
}

run_duplicate_mock_callback() {
  echo "[5/5] duplicate Mock pay callback"

  local callback_order body resp1 resp2 channel_id
  callback_order="${CALLBACK_ORDER_NO:-$ORDER_NO}"
  channel_id="mock-channel-${callback_order}-${RUN_ID}"

  body=$(jq -n \
    --arg orderNo "$callback_order" \
    --arg payRequestNo "pay-${callback_order}-${RUN_ID}" \
    --arg callbackRequestNo "callback-${callback_order}-${RUN_ID}" \
    --arg channelTxnId "$channel_id" \
    '{orderNo:$orderNo,payRequestNo:$payRequestNo,callbackRequestNo:$callbackRequestNo,channelTxnId:$channelTxnId,payStatus:"SUCCESS"}')

  resp1=$(auth_post_json "/api/player/order/mock-callback" "$body" "$WRITE_TOKEN")
  resp2=$(auth_post_json "/api/player/order/mock-callback" "$body" "$WRITE_TOKEN")

  assert_code_200 "mock callback first request" "$resp1"
  assert_code_200 "mock callback duplicate request" "$resp2"

  echo "mock_callback_order_no=$callback_order"
  echo "mock_callback_channel_txn_id=$channel_id"
}

ensure_token
run_pool_detail_load
run_unauthorized_direct_create
ensure_write_user
create_write_pool
run_idempotent_create
run_duplicate_pay
run_duplicate_mock_callback

echo "All HTTP scenarios completed."
