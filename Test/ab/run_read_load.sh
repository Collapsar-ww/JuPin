#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POOL_ID="${POOL_ID:-1}"
REQUESTS="${REQUESTS:-300}"
CONCURRENCY="${CONCURRENCY:-30}"
LABEL="${LABEL:-manual}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
RESULTS_DIR="${RESULTS_DIR:-Test/results}"

mkdir -p "$RESULTS_DIR"

RAW_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_read_raw.tsv"
SUMMARY_FILE="$RESULTS_DIR/${RUN_ID}_${LABEL}_read_summary.tsv"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1" >&2
    exit 1
  fi
}

need_cmd curl
need_cmd awk
need_cmd sort

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

run_load() {
  : > "$RAW_FILE"

  local start end elapsed running
  start=$(date +%s)
  running=0
  for i in $(seq 1 "$REQUESTS"); do
    (
      curl -sS -o /dev/null -w "%{time_total}\t%{http_code}\n" \
        "$BASE_URL/api/player/pool/$POOL_ID"
    ) >> "$RAW_FILE" &

    running=$((running + 1))
    if (( running >= CONCURRENCY )); then
      wait
      running=0
    fi
  done
  wait
  end=$(date +%s)
  elapsed=$((end - start))
  if (( elapsed < 1 )); then
    elapsed=1
  fi

  local total ok avg p50 p95 p99 throughput status_counts
  total=$(wc -l < "$RAW_FILE" | tr -d ' ')
  ok=$(awk '$2 == 200 {count++} END {print count + 0}' "$RAW_FILE")
  avg=$(avg_from_file "$RAW_FILE")
  p50=$(pct_from_file "$RAW_FILE" 0.50)
  p95=$(pct_from_file "$RAW_FILE" 0.95)
  p99=$(pct_from_file "$RAW_FILE" 0.99)
  throughput=$(awk -v total="$total" -v elapsed="$elapsed" 'BEGIN {printf "%.2f", total / elapsed}')
  status_counts=$(awk '{count[$2]++} END {for (status in count) printf "%s:%s ", status, count[status]}' "$RAW_FILE")

  {
    echo -e "run_id\tlabel\tbase_url\tpool_id\trequests\tconcurrency\ttotal\thttp_200\tavg_seconds\tp50_seconds\tp95_seconds\tp99_seconds\telapsed_seconds\tthroughput_req_s\tstatus_counts"
    echo -e "${RUN_ID}\t${LABEL}\t${BASE_URL}\t${POOL_ID}\t${REQUESTS}\t${CONCURRENCY}\t${total}\t${ok}\t${avg}\t${p50}\t${p95}\t${p99}\t${elapsed}\t${throughput}\t${status_counts}"
  } > "$SUMMARY_FILE"

  cat "$SUMMARY_FILE"

  if [[ "$ok" != "$total" ]]; then
    echo "Read load failed: HTTP 200 count does not match total requests." >&2
    exit 1
  fi
}

run_load
