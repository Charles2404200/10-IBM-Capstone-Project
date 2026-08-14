#!/bin/sh
set -eu

export LOKI_PORT="${PORT:-3100}"
export LOKI_RETENTION_PERIOD="${LOKI_RETENTION_PERIOD:-336h}"
envsubst < /etc/loki/config.yml.template > /etc/loki/config.yml
exec /usr/bin/loki -config.file=/etc/loki/config.yml
