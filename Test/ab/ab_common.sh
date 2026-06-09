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
AB_AUTO_BACKEND="${AB_AUTO_BACKEND:-false}"
AB_MAVEN_CMD="${AB_MAVEN_CMD:-/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn}"
AB_JAVA_HOME="${AB_JAVA_HOME:-}"
AB_BACKEND_JAR="${AB_BACKEND_JAR:-$ROOT_DIR/jupin/jupin-server/target/jupin-server-1.0.0.jar}"
AB_BACKEND_PID_FILE="${AB_BACKEND_PID_FILE:-/tmp/jupin_ab_backend.pid}"
AB_BACKEND_LOG_DIR="${AB_BACKEND_LOG_DIR:-$AB_RESULTS_DIR/backend_logs}"
AB_CURL_MAX_TIME="${AB_CURL_MAX_TIME:-15}"
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
    if [[ "$AB_AUTO_BACKEND" == "true" ]]; then
        build_and_restart_backend "$branch"
    else
        echo ""
        echo "=============================================="
        echo "BRANCH SWITCHED TO: $branch"
        echo "Please:"
        echo "  1. mvn clean package -DskipTests"
        echo "  2. Restart the Spring Boot application"
        echo "=============================================="
        echo -n "Press ENTER when backend is ready on $BASE_URL ..."
        read -r
    fi

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
        if [[ "$AB_AUTO_BACKEND" == "true" ]]; then
            echo "Backend log tail:" >&2
            tail -80 "$AB_BACKEND_LOG_DIR/${RUN_ID}_${branch}.log" 2>/dev/null || true
        fi
        echo "ERROR: Backend not reachable after 60s. Exiting." >&2
        exit 1
    fi
    echo "Backend is up."
}

resolve_java_home() {
    if [[ -n "$AB_JAVA_HOME" ]]; then
        echo "$AB_JAVA_HOME"
        return
    fi
    /usr/libexec/java_home -v 17
}

stop_backend() {
    if [[ -f "$AB_BACKEND_PID_FILE" ]]; then
        local pid
        pid=$(cat "$AB_BACKEND_PID_FILE")
        if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
            echo "Stopping backend pid=$pid"
            kill "$pid" 2>/dev/null || true
            for _ in $(seq 1 20); do
                if ! kill -0 "$pid" 2>/dev/null; then
                    break
                fi
                sleep 0.5
            done
            kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$AB_BACKEND_PID_FILE"
    fi

    local port_pid
    port_pid=$(lsof -ti tcp:8080 2>/dev/null || true)
    if [[ -n "$port_pid" ]]; then
        echo "Stopping process on port 8080: $port_pid"
        kill $port_pid 2>/dev/null || true
        sleep 2
        kill -9 $port_pid 2>/dev/null || true
    fi
}

build_and_restart_backend() {
    local branch="$1"
    local java_home java_bin log_file
    java_home=$(resolve_java_home)
    java_bin="$java_home/bin/java"
    mkdir -p "$AB_BACKEND_LOG_DIR"
    log_file="$AB_BACKEND_LOG_DIR/${RUN_ID}_${branch}.log"

    echo ""
    echo "=============================================="
    echo "BRANCH SWITCHED TO: $branch"
    echo "Auto building with IDEA Maven:"
    echo "  JAVA_HOME=$java_home"
    echo "  MAVEN=$AB_MAVEN_CMD"
    echo "=============================================="

    (cd "$ROOT_DIR/jupin" && JAVA_HOME="$java_home" "$AB_MAVEN_CMD" clean package -DskipTests)

    stop_backend
    echo "Starting backend jar: $AB_BACKEND_JAR"
    nohup "$java_bin" -jar "$AB_BACKEND_JAR" > "$log_file" 2>&1 &
    echo $! > "$AB_BACKEND_PID_FILE"
    echo "Backend pid=$(cat "$AB_BACKEND_PID_FILE") log=$log_file"
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

    local tmp scenarios scenario
    tmp=$(mktemp)
    for f in "$a_dir"/*_summary.tsv; do
        tail -1 "$f" | awk -F'\t' '{print "A\t" $3 "\t" $15}'
    done >> "$tmp"
    for f in "$b_dir"/*_summary.tsv; do
        tail -1 "$f" | awk -F'\t' '{print "B\t" $3 "\t" $15}'
    done >> "$tmp"

    scenarios=$(awk -F'\t' '{print $2}' "$tmp" | sort -u)

    echo -e "metric\tA_mean\tA_stddev\tB_mean\tB_stddev\timprovement_pct" > "$output"
    while IFS= read -r scenario; do
        [[ -z "$scenario" ]] && continue

        local a_mean a_std b_mean b_std improvement
        a_mean=$(awk -F'\t' -v s="$scenario" '$1 == "A" && $2 == s {sum += $3; n++} END {if (n == 0) print "0"; else printf "%.4f", sum / n}' "$tmp")
        a_std=$(awk -F'\t' -v s="$scenario" -v m="$a_mean" '$1 == "A" && $2 == s {sum += ($3 - m)^2; n++} END {if (n > 1) printf "%.4f", sqrt(sum / (n - 1)); else print "0"}' "$tmp")
        b_mean=$(awk -F'\t' -v s="$scenario" '$1 == "B" && $2 == s {sum += $3; n++} END {if (n == 0) print "0"; else printf "%.4f", sum / n}' "$tmp")
        b_std=$(awk -F'\t' -v s="$scenario" -v m="$b_mean" '$1 == "B" && $2 == s {sum += ($3 - m)^2; n++} END {if (n > 1) printf "%.4f", sqrt(sum / (n - 1)); else print "0"}' "$tmp")
        improvement=$(awk -v a="$a_mean" -v b="$b_mean" 'BEGIN {if (a == 0) print "0"; else printf "%.1f", (a - b) / a * 100}')

        echo -e "${scenario}.p95_seconds\t${a_mean}\t${a_std}\t${b_mean}\t${b_std}\t${improvement}" >> "$output"
        echo "${scenario} P95: A=${a_mean}s (±${a_std})  B=${b_mean}s (±${b_std})  improvement=${improvement}%"
    done <<< "$scenarios"
    rm -f "$tmp"
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
