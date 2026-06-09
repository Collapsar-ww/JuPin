#!/usr/bin/env bash

# Guard against double-sourcing
if [[ -n "${_AB_COMMON_SOURCED:-}" ]]; then
    return 0
fi
readonly _AB_COMMON_SOURCED=1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/common.sh"

BASE_URL="${BASE_URL:-http://localhost:8080}"

# A/B configuration
AB_ROUNDS="${AB_ROUNDS:-5}"
AB_WARMUP="${AB_WARMUP:-2}"
AB_RESULTS_DIR="${AB_RESULTS_DIR:-$ROOT_DIR/Test/ab_results}"
AB_BASELINE_BRANCH="${AB_BASELINE_BRANCH:-}"     # set per test
AB_OPTIMIZED_BRANCH="${AB_OPTIMIZED_BRANCH:-cleanup-bench-review}"
AB_RESET_EACH_ROUND="${AB_RESET_EACH_ROUND:-true}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
LABEL="${LABEL:-ab}"

mkdir -p "$AB_RESULTS_DIR"

# ========== UTILITIES ==========

stddev_from_file() {
    local file="$1"
    awk '{
        sum += $1; sumsq += $1*$1; n++
    } END {
        if (n < 2) { print "0"; exit }
        mean = sum / n
        variance = (sumsq - sum*sum/n) / (n - 1)
        printf "%.6f", sqrt(variance)
    }' "$file"
}

# Prompt user to rebuild and restart backend after branch switch
wait_for_backend() {
    local branch="$1"
    echo ""
    echo "=============================================="
    echo "BRANCH SWITCHED TO: $branch"
    echo "Please:"
    echo "  1. mvn clean package -DskipTests"
    echo "  2. Restart the Spring Boot application"
    echo "=============================================="
    echo -n "Press ENTER when backend is ready on $BASE_URL ..."
    read -r
    # Quick health check
    local ok=0
    for i in $(seq 1 30); do
        if curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/api/player/pool/1" 2>/dev/null | grep -q '200\|401'; then
            ok=1
            break
        fi
        sleep 2
    done
    if [[ "$ok" != "1" ]]; then
        echo "ERROR: Backend not reachable after 60s. Exiting." >&2
        exit 1
    fi
    echo "Backend is up."
}

# Switch git branch
switch_branch() {
    local branch="$1"
    echo "Switching to branch: $branch"
    git -C "$ROOT_DIR" checkout "$branch"
}

reset_test_context() {
    WRITE_PHONE=""
    WRITE_TOKEN=""
    WRITE_POOL_ID=""
    ORDER_NO=""
}

reset_state_if_needed() {
    local round_label="$1"
    if [[ "$AB_RESET_EACH_ROUND" != "true" ]]; then
        return
    fi

    echo ""
    echo "===== RESET STATE: $round_label ====="
    bash "$SCRIPT_DIR/reset_state.sh"
    reset_test_context
}

# Run a single A/B round. Returns 0 on success.
# $1: variant label (e.g. "oversell_A_1")
# $2: "A" or "B"
# $3: function name to execute
run_ab_round() {
    local round_label="$1"
    local variant="$2"
    local test_fn="$3"
    shift 3

    AB_VARIANT="$variant"
    AB_ROUND_LABEL="$round_label"
    LABEL="$round_label"
    RUN_ID="${RUN_ID}"

    reset_state_if_needed "$round_label"
    "$test_fn" "$@"
}

# Aggregate per-round summaries into a comparison report.
# Input: summary files named ${test}_${variant}_${round}_summary.tsv
# Output: ${test}_comparison.tsv with columns:
#   metric | A_mean | A_stddev | B_mean | B_stddev | improvement_pct
aggregate_comparison() {
    local test_name="$1"
    local a_dir="$2"
    local b_dir="$3"
    local output="$AB_RESULTS_DIR/${RUN_ID}_${test_name}_comparison.tsv"

    echo "=== Aggregate Comparison for $test_name ==="
    echo "A (baseline) dir: $a_dir"
    echo "B (optimized) dir: $b_dir"
    echo ""

    # Collect P95 from A rounds. write_summary columns:
    # 13=avg_seconds, 14=p50_seconds, 15=p95_seconds, 16=p99_seconds.
    local a_p95s=()
    local b_p95s=()
    for f in "$a_dir"/*_summary.tsv; do
        local p95
        p95=$(tail -1 "$f" | awk -F'\t' '{print $15}')
        a_p95s+=("$p95")
    done
    for f in "$b_dir"/*_summary.tsv; do
        local p95
        p95=$(tail -1 "$f" | awk -F'\t' '{print $15}')
        b_p95s+=("$p95")
    done

    # Compute mean and stddev
    local a_mean a_std b_mean b_std
    a_mean=$(printf '%s\n' "${a_p95s[@]}" | awk '{sum+=$1; n++} END {printf "%.4f", sum/n}')
    a_std=$(printf '%s\n' "${a_p95s[@]}" | awk -v m="$a_mean" '{sum+=($1-m)^2; n++} END {if(n>1) printf "%.4f", sqrt(sum/(n-1)); else print "0"}')
    b_mean=$(printf '%s\n' "${b_p95s[@]}" | awk '{sum+=$1; n++} END {printf "%.4f", sum/n}')
    b_std=$(printf '%s\n' "${b_p95s[@]}" | awk -v m="$b_mean" '{sum+=($1-m)^2; n++} END {if(n>1) printf "%.4f", sqrt(sum/(n-1)); else print "0"}')

    local improvement
    improvement=$(awk -v a="$a_mean" -v b="$b_mean" 'BEGIN {printf "%.1f", (a - b) / a * 100}')

    {
        echo -e "metric\tA_mean\tA_stddev\tB_mean\tB_stddev\timprovement_pct"
        echo -e "p95_seconds\t${a_mean}\t${a_std}\t${b_mean}\t${b_std}\t${improvement}"
    } > "$output"

    echo "P95:  A=${a_mean}s (±${a_std})  B=${b_mean}s (±${b_std})  improvement=${improvement}%"
    echo "Comparison saved to $output"
}

# Run full A/B test: warmup A→B, then interleaved rounds A→B
# $1: baseline branch name
# $2: test function name
# $3: test name (oversell/idempotent/cache)
run_full_ab_test() {
    local baseline_branch="$1"
    local test_fn="$2"
    local test_name="$3"
    shift 3

    local a_results="$AB_RESULTS_DIR/${RUN_ID}_${test_name}_A"
    local b_results="$AB_RESULTS_DIR/${RUN_ID}_${test_name}_B"
    mkdir -p "$a_results" "$b_results"

    # === A (baseline / no protection) ===
    switch_branch "$baseline_branch"
    wait_for_backend "$baseline_branch"

    if (( AB_WARMUP > 0 )); then
        echo ""
        echo "===== $test_name: A WARMUP (baseline: $baseline_branch) ====="
        for r in $(seq 1 "$AB_WARMUP"); do
            echo "--- $test_name A warmup round $r/$AB_WARMUP ---"
            RESULTS_DIR="/tmp/jupin_ab_warmup"
            mkdir -p "$RESULTS_DIR"
            run_ab_round "${test_name}_A_warmup_$r" "A" "$test_fn" "$@"
        done
    fi

    echo ""
    echo "===== $test_name: A DATA ROUNDS (baseline: $baseline_branch) ====="
    for r in $(seq 1 "$AB_ROUNDS"); do
        echo "--- $test_name A round $r/$AB_ROUNDS ---"
        RESULTS_DIR="$a_results"
        run_ab_round "${test_name}_A_$r" "A" "$test_fn" "$@"
    done

    # === B (optimized / with full protection) ===
    switch_branch "$AB_OPTIMIZED_BRANCH"
    wait_for_backend "$AB_OPTIMIZED_BRANCH"

    if (( AB_WARMUP > 0 )); then
        echo ""
        echo "===== $test_name: B WARMUP (optimized: $AB_OPTIMIZED_BRANCH) ====="
        for r in $(seq 1 "$AB_WARMUP"); do
            echo "--- $test_name B warmup round $r/$AB_WARMUP ---"
            RESULTS_DIR="/tmp/jupin_ab_warmup"
            mkdir -p "$RESULTS_DIR"
            run_ab_round "${test_name}_B_warmup_$r" "B" "$test_fn" "$@"
        done
    fi

    echo ""
    echo "===== $test_name: B DATA ROUNDS (optimized: $AB_OPTIMIZED_BRANCH) ====="
    for r in $(seq 1 "$AB_ROUNDS"); do
        echo "--- $test_name B round $r/$AB_ROUNDS ---"
        RESULTS_DIR="$b_results"
        run_ab_round "${test_name}_B_$r" "B" "$test_fn" "$@"
    done

    # === Comparison ===
    echo ""
    aggregate_comparison "$test_name" "$a_results" "$b_results"

    # Restore optimized branch
    switch_branch "$AB_OPTIMIZED_BRANCH"
    echo ""
    echo "$test_name: all rounds complete."
}
