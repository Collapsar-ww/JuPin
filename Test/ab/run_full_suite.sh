#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
POOL_ID="${POOL_ID:-1}"
TIERS="${TIERS:-100:10,300:30,500:50}"
LABEL_PREFIX="${LABEL_PREFIX:-ab}"
SCENARIOS="${SCENARIOS:-read,unauthorized_create,idempotent_create,duplicate_pay,mock_callback}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
RESULTS_DIR="${RESULTS_DIR:-Test/results}"
RESET_EACH="${RESET_EACH:-true}"
FAIL_FAST="${FAIL_FAST:-false}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-jp-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-script_murder_carpool}"

mkdir -p "$RESULTS_DIR"
echo -e "run_id\tlabel\tscenario\trequests\tconcurrency\texit_status" > "$RESULTS_DIR/${RUN_ID}_${LABEL_PREFIX}_suite_status.tsv"

run_reset() {
  if [[ "$RESET_EACH" == "true" ]]; then
    bash "$SCRIPT_DIR/reset_state.sh"
  fi
}

run_validation() {
  local label="$1"
  local consistency_file="$RESULTS_DIR/${RUN_ID}_${label}_consistency.tsv"
  local reliability_file="$RESULTS_DIR/${RUN_ID}_${label}_reliability.tsv"

  docker exec -i "$MYSQL_CONTAINER" mysql \
    -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    --default-character-set=utf8mb4 "$MYSQL_DATABASE" \
    < "$ROOT_DIR/Test/sql/validate_consistency.sql" > "$consistency_file"

  docker exec -i "$MYSQL_CONTAINER" mysql \
    -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    --default-character-set=utf8mb4 "$MYSQL_DATABASE" \
    < "$ROOT_DIR/Test/mq/validate_reliability.sql" > "$reliability_file"

  echo "consistency_result=$consistency_file"
  echo "reliability_result=$reliability_file"
}

run_scenario() {
  local scenario="$1"
  local requests="$2"
  local concurrency="$3"
  local label="${LABEL_PREFIX}_${scenario}_${requests}_${concurrency}"

  echo "===== scenario=$scenario requests=$requests concurrency=$concurrency label=$label ====="
  run_reset

  status=0
  case "$scenario" in
    read)
      RUN_ID="$RUN_ID" LABEL="$label" BASE_URL="$BASE_URL" POOL_ID="$POOL_ID" REQUESTS="$requests" CONCURRENCY="$concurrency" \
        bash "$SCRIPT_DIR/run_read_load.sh" || status=$?
      ;;
    unauthorized_create)
      RUN_ID="$RUN_ID" LABEL="$label" BASE_URL="$BASE_URL" POOL_ID="$POOL_ID" REQUESTS="$requests" CONCURRENCY="$concurrency" \
        bash "$SCRIPT_DIR/run_unauthorized_create_load.sh" || status=$?
      ;;
    idempotent_create)
      RUN_ID="$RUN_ID" LABEL="$label" BASE_URL="$BASE_URL" POOL_ID="$POOL_ID" REQUESTS="$requests" CONCURRENCY="$concurrency" \
        bash "$SCRIPT_DIR/run_idempotent_create_load.sh" || status=$?
      ;;
    duplicate_pay)
      RUN_ID="$RUN_ID" LABEL="$label" BASE_URL="$BASE_URL" POOL_ID="$POOL_ID" REQUESTS="$requests" CONCURRENCY="$concurrency" \
        bash "$SCRIPT_DIR/run_duplicate_pay_load.sh" || status=$?
      ;;
    mock_callback)
      RUN_ID="$RUN_ID" LABEL="$label" BASE_URL="$BASE_URL" POOL_ID="$POOL_ID" REQUESTS="$requests" CONCURRENCY="$concurrency" \
        bash "$SCRIPT_DIR/run_mock_callback_load.sh" || status=$?
      ;;
    *)
      echo "Unknown scenario: $scenario" >&2
      exit 1
      ;;
  esac

  run_validation "$label" || status=$?
  echo -e "${RUN_ID}\t${label}\t${scenario}\t${requests}\t${concurrency}\t${status}" >> "$RESULTS_DIR/${RUN_ID}_${LABEL_PREFIX}_suite_status.tsv"
  if [[ "$status" != "0" && "$FAIL_FAST" == "true" ]]; then
    exit "$status"
  fi
}

IFS=',' read -r -a tier_array <<< "$TIERS"
IFS=',' read -r -a scenario_array <<< "$SCENARIOS"

for tier in "${tier_array[@]}"; do
  requests="${tier%%:*}"
  concurrency="${tier##*:}"
  if [[ -z "$requests" || -z "$concurrency" || "$tier" != "$requests:$concurrency" ]]; then
    echo "Invalid tier: $tier. Expected format like 100:10." >&2
    exit 1
  fi
  for scenario in "${scenario_array[@]}"; do
    run_scenario "$scenario" "$requests" "$concurrency"
  done
done

echo "full_suite_done run_id=$RUN_ID label_prefix=$LABEL_PREFIX"
