#!/usr/bin/env bash

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

need_cmd curl
need_cmd jq
need_cmd awk
need_cmd sort

BASE_URL="${BASE_URL:-http://localhost:8080}"
POOL_ID="${POOL_ID:-1}"
ORDER_TYPE="${ORDER_TYPE:-0}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
LABEL="${LABEL:-manual}"
RESULTS_DIR="${RESULTS_DIR:-Test/results}"
WRITE_PHONE="${WRITE_PHONE:-}"
WRITE_PASSWORD="${WRITE_PASSWORD:-player123}"
WRITE_TOKEN="${WRITE_TOKEN:-}"
WRITE_POOL_ID="${WRITE_POOL_ID:-}"
ORDER_NO="${ORDER_NO:-}"

mkdir -p "$RESULTS_DIR"

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
  local token="$3"
  curl -sS -X POST "$BASE_URL$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

auth_post_json_status() {
  local path="$1"
  local body="$2"
  local token="$3"
  curl -sS -o /dev/null -w "%{time_total}\t%{http_code}\n" -X POST "$BASE_URL$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

post_json_status() {
  local path="$1"
  local body="$2"
  curl -sS -o /dev/null -w "%{time_total}\t%{http_code}\n" -X POST "$BASE_URL$path" \
    -H "Content-Type: application/json" \
    -d "$body"
}

assert_code_200() {
  local name="$1"
  local resp="$2"
  local detail="${3:-}"
  local code
  code=$(echo "$resp" | jq -r '.code // empty')
  if [[ "$code" != "200" ]]; then
    echo "Scenario setup failed: $name" >&2
    if [[ -n "$detail" ]]; then
      echo "$detail" >&2
    fi
    echo "$resp" >&2
    exit 1
  fi
}

pct_from_file() {
  local file="$1"
  local ratio="$2"
  awk '{print $1}' "$file" | sort -n | awk -v ratio="$ratio" '
    {a[NR]=$1}
    END {
      if (NR == 0) {
        print "0"
      } else {
        idx = int(NR * ratio)
        if (idx < 1) idx = 1
        if (idx > NR) idx = NR
        printf "%.4f", a[idx]
      }
    }'
}

avg_from_file() {
  local file="$1"
  awk '{sum += $1; count++} END {if (count == 0) print "0"; else printf "%.4f", sum / count}' "$file"
}

write_summary() {
  local raw_file="$1"
  local summary_file="$2"
  local scenario="$3"
  local requests="$4"
  local concurrency="$5"
  local started_at="$6"
  local ended_at="$7"
  local success_expr="${8:-}"

  local elapsed total http_200 business_ok avg p50 p95 p99 throughput status_counts
  elapsed=$((ended_at - started_at))
  if (( elapsed < 1 )); then
    elapsed=1
  fi
  total=$(wc -l < "$raw_file" | tr -d ' ')
  http_200=$(awk '$2 == 200 {count++} END {print count + 0}' "$raw_file")
  if [[ -n "$success_expr" ]]; then
    business_ok=$(awk "$success_expr" "$raw_file")
  else
    business_ok="$http_200"
  fi
  avg=$(avg_from_file "$raw_file")
  p50=$(pct_from_file "$raw_file" 0.50)
  p95=$(pct_from_file "$raw_file" 0.95)
  p99=$(pct_from_file "$raw_file" 0.99)
  throughput=$(awk -v total="$total" -v elapsed="$elapsed" 'BEGIN {printf "%.2f", total / elapsed}')
  status_counts=$(awk '{count[$2]++} END {for (status in count) printf "%s:%s ", status, count[status]}' "$raw_file")

  {
    echo -e "run_id\tlabel\tscenario\tbase_url\tpool_id\twrite_pool_id\torder_no\trequests\tconcurrency\ttotal\thttp_200\tbusiness_ok\tavg_seconds\tp50_seconds\tp95_seconds\tp99_seconds\telapsed_seconds\tthroughput_req_s\tstatus_counts"
    echo -e "${RUN_ID}\t${LABEL}\t${scenario}\t${BASE_URL}\t${POOL_ID}\t${WRITE_POOL_ID:-}\t${ORDER_NO:-}\t${requests}\t${concurrency}\t${total}\t${http_200}\t${business_ok}\t${avg}\t${p50}\t${p95}\t${p99}\t${elapsed}\t${throughput}\t${status_counts}"
  } > "$summary_file"

  cat "$summary_file"
}

make_write_phone() {
  if [[ -n "$WRITE_PHONE" ]]; then
    return
  fi

  local digits suffix
  digits=$(printf "%s%s" "$RUN_ID" "$LABEL" | tr -cd '0-9')
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
}

create_write_pool() {
  if [[ -n "$WRITE_POOL_ID" ]]; then
    return
  fi

  local body resp pool_id
  body=$(jq -n \
    --arg scriptName "压测临时车局-${RUN_ID}-${LABEL}" \
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
  assert_code_200 "create write pool" "$resp" \
    "Precondition: temporary player must be allowed to create a type=0 player pool; seed scriptId=1 must exist and be online."

  pool_id=$(echo "$resp" | jq -r '.data.id // empty')
  if [[ -z "$pool_id" ]]; then
    echo "Create write pool failed: missing pool id." >&2
    echo "$resp" >&2
    exit 1
  fi
  WRITE_POOL_ID="$pool_id"
}

create_order_once() {
  local idem_key="${1:-setup-${RUN_ID}-${LABEL}}"
  local body resp order_no
  body=$(jq -n \
    --argjson poolId "$WRITE_POOL_ID" \
    --argjson type "$ORDER_TYPE" \
    --arg idempotentKey "$idem_key" \
    '{poolId:$poolId,type:$type,idempotentKey:$idempotentKey}')
  resp=$(auth_post_json "/api/player/order/create" "$body" "$WRITE_TOKEN")
  assert_code_200 "create order" "$resp"
  order_no=$(echo "$resp" | jq -r '.data.orderNo // empty')
  if [[ -z "$order_no" ]]; then
    echo "Create order failed: missing orderNo." >&2
    echo "$resp" >&2
    exit 1
  fi
  ORDER_NO="$order_no"
}
