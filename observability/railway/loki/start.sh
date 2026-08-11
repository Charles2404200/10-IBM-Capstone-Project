#!/bin/sh
set -eu

export LOKI_PORT="${PORT:-3100}"
envsubst < /etc/loki/config.yml.template > /etc/loki/config.yml
exec /usr/bin/loki -config.file=/etc/loki/config.yml
