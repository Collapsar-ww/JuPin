#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

BASE_RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
TESTS="${TESTS:-oversell,idempotent,cache}"
AB_ROUNDS="${AB_ROUNDS:-3}"
AB_WARMUP="${AB_WARMUP:-1}"
AB_RESET_EACH_ROUND="${AB_RESET_EACH_ROUND:-true}"
REMOTE_PROJECT_DIR="${REMOTE_PROJECT_DIR:-~/JuPin}"
REMOTE_COMPOSE_DIR="${REMOTE_COMPOSE_DIR:-$REMOTE_PROJECT_DIR/jupin}"

# Level format:
#   oversell:   userCount:concurrency:maxMembers
#   idempotent: requestCount:concurrency
#   cache:      requestCount:concurrency:poolId
#
# The first field is the business/user scale for this pressure level. High-pressure
# levels should be passed by command line instead of hard-coded here.
OVERSELL_LEVELS="${OVERSELL_LEVELS:-20:10:3,50:25:3,100:50:3,200:100:3}"
IDEMPOTENT_LEVELS="${IDEMPOTENT_LEVELS:-50:10,100:25,200:50,500:100}"
CACHE_LEVELS="${CACHE_LEVELS:-300:30:1,1000:100:1,3000:200:1,10000:300:1}"

normalize_remote_paths() {
    if [[ "${AB_BACKEND_MODE:-local}" != "remote" ]]; then
        return
    fi

    local local_home="${HOME%/}"
    if [[ "$REMOTE_PROJECT_DIR" == "$local_home/"* ]]; then
        REMOTE_PROJECT_DIR="~/${REMOTE_PROJECT_DIR#"$local_home"/}"
    fi
    if [[ "$REMOTE_COMPOSE_DIR" == "$local_home/"* ]]; then
        REMOTE_COMPOSE_DIR="~/${REMOTE_COMPOSE_DIR#"$local_home"/}"
    fi
}

normalize_remote_paths

contains_test() {
    local needle="$1"
    local item
    IFS=',' read -r -a items <<< "$TESTS"
    for item in "${items[@]}"; do
        if [[ "$item" == "$needle" ]]; then
            return 0
        fi
    done
    return 1
}

run_oversell_levels() {
    local level=1 spec players concurrency max_members
    IFS=',' read -r -a specs <<< "$OVERSELL_LEVELS"
    for spec in "${specs[@]}"; do
        IFS=':' read -r players concurrency max_members <<< "$spec"
        echo ""
        echo "============================================================"
        echo "PRESSURE LEVEL oversell L${level}: user_count=${players}, concurrency=${concurrency}, max_members=${max_members}"
        echo "============================================================"
        RUN_ID="${BASE_RUN_ID}_oversell_L${level}" \
        TESTS=oversell \
        AB_ROUNDS="$AB_ROUNDS" \
        AB_WARMUP="$AB_WARMUP" \
        AB_RESET_EACH_ROUND="$AB_RESET_EACH_ROUND" \
        AB_BACKEND_MODE="${AB_BACKEND_MODE:-local}" \
        AB_AUTO_BACKEND="${AB_AUTO_BACKEND:-false}" \
        AB_CURL_MAX_TIME="${AB_CURL_MAX_TIME:-15}" \
        REMOTE_SSH="${REMOTE_SSH:-}" \
        REMOTE_PROJECT_DIR="$REMOTE_PROJECT_DIR" \
        REMOTE_COMPOSE_DIR="$REMOTE_COMPOSE_DIR" \
        REMOTE_COMPOSE_CMD="${REMOTE_COMPOSE_CMD:-sudo docker-compose}" \
        REMOTE_DOCKER_CMD="${REMOTE_DOCKER_CMD:-sudo docker}" \
        REMOTE_MYSQL_CONTAINER="${REMOTE_MYSQL_CONTAINER:-jp-mysql}" \
        REMOTE_REDIS_CONTAINER="${REMOTE_REDIS_CONTAINER:-jp-redis}" \
        REMOTE_MYSQL_PASSWORD="${REMOTE_MYSQL_PASSWORD:-root}" \
        REMOTE_MYSQL_DATABASE="${REMOTE_MYSQL_DATABASE:-script_murder_carpool}" \
        BASE_URL="${BASE_URL:-http://localhost:8080}" \
        PLAYER_COUNT="$players" \
        CONCURRENCY="$concurrency" \
        POOL_MAX_MEMBERS="$max_members" \
        bash "$SCRIPT_DIR/ab_suite.sh"
        level=$((level + 1))
    done
}

run_idempotent_levels() {
    local level=1 spec requests concurrency
    IFS=',' read -r -a specs <<< "$IDEMPOTENT_LEVELS"
    for spec in "${specs[@]}"; do
        IFS=':' read -r requests concurrency <<< "$spec"
        echo ""
        echo "============================================================"
        echo "PRESSURE LEVEL idempotent L${level}: request_count=${requests}, concurrency=${concurrency}"
        echo "============================================================"
        RUN_ID="${BASE_RUN_ID}_idempotent_L${level}" \
        TESTS=idempotent \
        AB_ROUNDS="$AB_ROUNDS" \
        AB_WARMUP="$AB_WARMUP" \
        AB_RESET_EACH_ROUND="$AB_RESET_EACH_ROUND" \
        AB_BACKEND_MODE="${AB_BACKEND_MODE:-local}" \
        AB_AUTO_BACKEND="${AB_AUTO_BACKEND:-false}" \
        AB_CURL_MAX_TIME="${AB_CURL_MAX_TIME:-15}" \
        REMOTE_SSH="${REMOTE_SSH:-}" \
        REMOTE_PROJECT_DIR="$REMOTE_PROJECT_DIR" \
        REMOTE_COMPOSE_DIR="$REMOTE_COMPOSE_DIR" \
        REMOTE_COMPOSE_CMD="${REMOTE_COMPOSE_CMD:-sudo docker-compose}" \
        REMOTE_DOCKER_CMD="${REMOTE_DOCKER_CMD:-sudo docker}" \
        REMOTE_MYSQL_CONTAINER="${REMOTE_MYSQL_CONTAINER:-jp-mysql}" \
        REMOTE_REDIS_CONTAINER="${REMOTE_REDIS_CONTAINER:-jp-redis}" \
        REMOTE_MYSQL_PASSWORD="${REMOTE_MYSQL_PASSWORD:-root}" \
        REMOTE_MYSQL_DATABASE="${REMOTE_MYSQL_DATABASE:-script_murder_carpool}" \
        BASE_URL="${BASE_URL:-http://localhost:8080}" \
        IDEM_REQUESTS="$requests" \
        IDEM_CONCURRENCY="$concurrency" \
        bash "$SCRIPT_DIR/ab_suite.sh"
        level=$((level + 1))
    done
}

run_cache_levels() {
    local level=1 spec requests concurrency pool_id
    IFS=',' read -r -a specs <<< "$CACHE_LEVELS"
    for spec in "${specs[@]}"; do
        IFS=':' read -r requests concurrency pool_id <<< "$spec"
        echo ""
        echo "============================================================"
        echo "PRESSURE LEVEL cache L${level}: request_count=${requests}, concurrency=${concurrency}, pool_id=${pool_id}"
        echo "============================================================"
        RUN_ID="${BASE_RUN_ID}_cache_L${level}" \
        TESTS=cache \
        AB_ROUNDS="$AB_ROUNDS" \
        AB_WARMUP="$AB_WARMUP" \
        AB_RESET_EACH_ROUND="$AB_RESET_EACH_ROUND" \
        AB_BACKEND_MODE="${AB_BACKEND_MODE:-local}" \
        AB_AUTO_BACKEND="${AB_AUTO_BACKEND:-false}" \
        AB_CURL_MAX_TIME="${AB_CURL_MAX_TIME:-15}" \
        REMOTE_SSH="${REMOTE_SSH:-}" \
        REMOTE_PROJECT_DIR="$REMOTE_PROJECT_DIR" \
        REMOTE_COMPOSE_DIR="$REMOTE_COMPOSE_DIR" \
        REMOTE_COMPOSE_CMD="${REMOTE_COMPOSE_CMD:-sudo docker-compose}" \
        REMOTE_DOCKER_CMD="${REMOTE_DOCKER_CMD:-sudo docker}" \
        REMOTE_MYSQL_CONTAINER="${REMOTE_MYSQL_CONTAINER:-jp-mysql}" \
        REMOTE_REDIS_CONTAINER="${REMOTE_REDIS_CONTAINER:-jp-redis}" \
        REMOTE_MYSQL_PASSWORD="${REMOTE_MYSQL_PASSWORD:-root}" \
        REMOTE_MYSQL_DATABASE="${REMOTE_MYSQL_DATABASE:-script_murder_carpool}" \
        BASE_URL="${BASE_URL:-http://localhost:8080}" \
        CACHE_REQUESTS="$requests" \
        CACHE_CONCURRENCY="$concurrency" \
        POOL_ID="$pool_id" \
        bash "$SCRIPT_DIR/ab_suite.sh"
        level=$((level + 1))
    done
}

echo "=============================================="
echo "JuPin A/B Gradient Pressure Suite"
echo "=============================================="
echo "BASE_RUN_ID:       $BASE_RUN_ID"
echo "Tests:             $TESTS"
echo "Rounds per level:  $AB_ROUNDS (data) + $AB_WARMUP (warmup)"
echo "Reset each round:  $AB_RESET_EACH_ROUND"
echo "Oversell levels:   $OVERSELL_LEVELS"
echo "Idempotent levels: $IDEMPOTENT_LEVELS"
echo "Cache levels:      $CACHE_LEVELS"
echo "Results dir:       $ROOT_DIR/Test/ab_results"
echo "Backend mode:      ${AB_BACKEND_MODE:-local}"
if [[ "${AB_BACKEND_MODE:-local}" == "remote" ]]; then
    echo "Remote SSH:        ${REMOTE_SSH:-<unset>}"
    echo "Remote project:    ${REMOTE_PROJECT_DIR:-~/JuPin}"
fi
echo "=============================================="
echo ""
echo "Each level still runs strict A/B: baseline branch first, optimized branch second."
if [[ "${AB_BACKEND_MODE:-local}" == "remote" ]]; then
    echo "Remote mode automatically checkouts branches, rebuilds/restarts app, and resets remote state."
else
    echo "Rebuild and restart the backend whenever ab_suite.sh prompts after a branch switch."
fi

if contains_test oversell; then
    run_oversell_levels
fi

if contains_test idempotent; then
    run_idempotent_levels
fi

if contains_test cache; then
    run_cache_levels
fi

echo ""
echo "=============================================="
echo "Gradient pressure suite complete."
echo "Base RUN_ID: $BASE_RUN_ID"
echo "Results: $ROOT_DIR/Test/ab_results"
echo "=============================================="
