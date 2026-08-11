FROM prom/prometheus:v2.54.1 AS source

FROM alpine:3.20
RUN apk add --no-cache ca-certificates gettext
COPY --from=source /bin/prometheus /bin/prometheus
COPY --from=source /usr/share/prometheus /usr/share/prometheus
COPY prometheus.yml.template /etc/prometheus/prometheus.yml.template
COPY start.sh /usr/local/bin/start-prometheus
RUN chmod +x /usr/local/bin/start-prometheus
ENTRYPOINT ["/usr/local/bin/start-prometheus"]
