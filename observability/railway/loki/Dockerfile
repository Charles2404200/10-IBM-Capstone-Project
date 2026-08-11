FROM grafana/loki:3.2.1 AS source

FROM alpine:3.20
RUN apk add --no-cache ca-certificates gettext
COPY --from=source /usr/bin/loki /usr/bin/loki
COPY loki-config.yml.template /etc/loki/config.yml.template
COPY start.sh /usr/local/bin/start-loki
RUN chmod +x /usr/local/bin/start-loki
ENTRYPOINT ["/usr/local/bin/start-loki"]
