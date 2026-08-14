#!/bin/sh
set -eu

: "${PROMETHEUS_URL:?Set PROMETHEUS_URL to the Prometheus private URL}"
: "${LOKI_URL:?Set LOKI_URL to the Loki private URL}"
export GF_SERVER_HTTP_PORT="${PORT:-3000}"
envsubst < /etc/grafana/provisioning/datasources/datasources.yml.template > /etc/grafana/provisioning/datasources/datasources.yml
exec /run.sh
