#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source common library once
source "$SCRIPT_DIR/ab_common.sh"

BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
AB_ROUNDS="${AB_ROUNDS:-5}"
AB_WARMUP="${AB_WARMUP:-2}"
AB_OPTIMIZED_BRANCH="${AB_OPTIMIZED_BRANCH:-cleanup-bench-review}"
AB_RESULTS_DIR="${AB_RESULTS_DIR:-$ROOT_DIR/Test/ab_results}"
AB_RESET_EACH_ROUND="${AB_RESET_EACH_ROUND:-true}"

# Baseline branches (surgically stripped of one protection each)
BRANCH_OVERSELL="${BRANCH_OVERSELL:-ab-baseline-oversell}"
BRANCH_IDEMPOTENT="${BRANCH_IDEMPOTENT:-ab-baseline-idempotent}"
BRANCH_CACHE="${BRANCH_CACHE:-ab-baseline-cache}"

TESTS="${TESTS:-oversell,idempotent,cache}"

mkdir -p "$AB_RESULTS_DIR"

echo "=============================================="
echo "JuPin A/B Benchmark Suite"
echo "=============================================="
echo "RUN_ID:           $RUN_ID"
echo "BASE_URL:         $BASE_URL"
echo "Rounds per test:  $AB_ROUNDS (data) + $AB_WARMUP (warmup)"
echo "Results dir:      $AB_RESULTS_DIR"
echo "Tests:            $TESTS"
echo "Reset each round: $AB_RESET_EACH_ROUND"
echo "Backend mode:     ${AB_BACKEND_MODE:-local}"
if [[ "${AB_BACKEND_MODE:-local}" == "remote" ]]; then
    echo "Remote SSH:       ${REMOTE_SSH:-<unset>}"
fi
echo "=============================================="
echo ""
echo "Before running:"
if [[ "${AB_BACKEND_MODE:-local}" == "remote" ]]; then
    echo "  1. Make sure the remote server can be reached by SSH and HTTP"
    echo "  2. The script will checkout branches, rebuild app, restart app, and reset remote MySQL + Redis"
    echo "  3. Run from the branch that contains the latest Test/ab scripts"
else
    echo "  1. Make sure MySQL + Redis containers are running"
    echo "  2. You will be prompted to rebuild + restart after each branch switch"
    echo "  3. By default, each A/B round resets MySQL and Redis before requests are sent"
fi
echo "=============================================="
echo ""

IFS=',' read -r -a test_array <<< "$TESTS"

for test in "${test_array[@]}"; do
    case "$test" in
        oversell)
            echo ""
            echo "##############################################"
            echo "# TEST 1/3: OVERSELL PROTECTION A/B"
            echo "# A: $BRANCH_OVERSELL (no lock, no capacity guard)"
            echo "# B: $AB_OPTIMIZED_BRANCH (full protection)"
            echo "##############################################"
            source "$SCRIPT_DIR/ab_oversell.sh"
            PLAYER_COUNT="${PLAYER_COUNT:-20}" \
            CONCURRENCY="${CONCURRENCY:-10}" \
            POOL_MAX_MEMBERS="${POOL_MAX_MEMBERS:-3}" \
            run_full_ab_test "$BRANCH_OVERSELL" run_oversell_round oversell
            ;;
        idempotent)
            echo ""
            echo "##############################################"
            echo "# TEST 2/3: IDEMPOTENCY A/B"
            echo "# A: $BRANCH_IDEMPOTENT (no idempotentKey check, no DuplicateKeyException catch)"
            echo "# B: $AB_OPTIMIZED_BRANCH (full protection)"
            echo "##############################################"
            source "$SCRIPT_DIR/ab_idempotent.sh"
            REQUESTS="${IDEM_REQUESTS:-50}" \
            CONCURRENCY="${IDEM_CONCURRENCY:-10}" \
            run_full_ab_test "$BRANCH_IDEMPOTENT" run_idempotent_round idempotent
            ;;
        cache)
            echo ""
            echo "##############################################"
            echo "# TEST 3/3: CACHE A/B"
            echo "# A: $BRANCH_CACHE (direct MySQL, no Redis cache)"
            echo "# B: $AB_OPTIMIZED_BRANCH (Cache Aside + null cache + fixed TTL jitter + write invalidation)"
            echo "##############################################"
            source "$SCRIPT_DIR/ab_cache.sh"
            REQUESTS="${CACHE_REQUESTS:-300}" \
            CONCURRENCY="${CACHE_CONCURRENCY:-30}" \
            POOL_ID="${POOL_ID:-1}" \
            run_full_ab_test "$BRANCH_CACHE" run_cache_round cache
            ;;
        *)
            echo "Unknown test: $test" >&2
            ;;
    esac
done

echo ""
echo "=============================================="
echo "All A/B tests complete!"
echo "Results: $AB_RESULTS_DIR"
echo ""
echo "Summary files:"
find "$AB_RESULTS_DIR" -name "*_comparison.tsv" 2>/dev/null | while read -r f; do echo "  $f"; done
echo "=============================================="
