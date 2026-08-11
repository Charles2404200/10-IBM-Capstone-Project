#!/bin/sh
set -eu

: "${API_METRICS_TARGET:?Set API_METRICS_TARGET to api.railway.internal:<api-port>}"
: "${OBSERVABILITY_TOKEN:?Set OBSERVABILITY_TOKEN to the same secret used by the API}"

envsubst < /etc/prometheus/prometheus.yml.template > /etc/prometheus/prometheus.yml
exec /bin/prometheus \
  --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention.time="${PROMETHEUS_RETENTION_TIME:-15d}" \
  --web.listen-address=":${PORT:-9090}"
