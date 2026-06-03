#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-jp-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-jp-redis}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-script_murder_carpool}"
INIT_SQL="${INIT_SQL:-$ROOT_DIR/jupin/sql/init.sql}"
SEED_SQL="${SEED_SQL:-$ROOT_DIR/seed-data.sql}"

need_file() {
  if [[ ! -f "$1" ]]; then
    echo "Missing file: $1" >&2
    exit 1
  fi
}

need_file "$INIT_SQL"
need_file "$SEED_SQL"

echo "reset_mysql_schema=$INIT_SQL"
docker exec -i "$MYSQL_CONTAINER" mysql \
  -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --default-character-set=utf8mb4 < "$INIT_SQL"

echo "reset_mysql_seed=$SEED_SQL"
docker exec -i "$MYSQL_CONTAINER" mysql \
  -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --default-character-set=utf8mb4 "$MYSQL_DATABASE" < "$SEED_SQL"

echo "flush_redis=$REDIS_CONTAINER"
docker exec "$REDIS_CONTAINER" redis-cli FLUSHDB >/dev/null

echo "state_reset_done"
