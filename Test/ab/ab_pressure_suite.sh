#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

BASE_RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
TESTS="${TESTS:-oversell,idempotent,cache}"
AB_ROUNDS="${AB_ROUNDS:-3}"
AB_WARMUP="${AB_WARMUP:-1}"
AB_RESET_EACH_ROUND="${AB_RESET_EACH_ROUND:-true}"

# Format:
#   oversell:   players:concurrency:maxMembers
#   idempotent: requests:concurrency
#   cache:      requests:concurrency:poolId
OVERSELL_LEVELS="${OVERSELL_LEVELS:-20:10:3,50:25:3,100:50:3,200:100:3}"
IDEMPOTENT_LEVELS="${IDEMPOTENT_LEVELS:-50:10,100:25,200:50,500:100}"
CACHE_LEVELS="${CACHE_LEVELS:-300:30:1,1000:100:1,3000:200:1,10000:300:1}"

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
        echo "PRESSURE LEVEL oversell L${level}: players=${players}, concurrency=${concurrency}, max_members=${max_members}"
        echo "============================================================"
        RUN_ID="${BASE_RUN_ID}_oversell_L${level}" \
        TESTS=oversell \
        AB_ROUNDS="$AB_ROUNDS" \
        AB_WARMUP="$AB_WARMUP" \
        AB_RESET_EACH_ROUND="$AB_RESET_EACH_ROUND" \
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
        echo "PRESSURE LEVEL idempotent L${level}: requests=${requests}, concurrency=${concurrency}"
        echo "============================================================"
        RUN_ID="${BASE_RUN_ID}_idempotent_L${level}" \
        TESTS=idempotent \
        AB_ROUNDS="$AB_ROUNDS" \
        AB_WARMUP="$AB_WARMUP" \
        AB_RESET_EACH_ROUND="$AB_RESET_EACH_ROUND" \
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
        echo "PRESSURE LEVEL cache L${level}: requests=${requests}, concurrency=${concurrency}, pool_id=${pool_id}"
        echo "============================================================"
        RUN_ID="${BASE_RUN_ID}_cache_L${level}" \
        TESTS=cache \
        AB_ROUNDS="$AB_ROUNDS" \
        AB_WARMUP="$AB_WARMUP" \
        AB_RESET_EACH_ROUND="$AB_RESET_EACH_ROUND" \
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
echo "=============================================="
echo ""
echo "Each level still runs strict A/B: baseline branch first, optimized branch second."
echo "Rebuild and restart the backend whenever ab_suite.sh prompts after a branch switch."

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
