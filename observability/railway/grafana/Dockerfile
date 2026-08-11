FROM grafana/grafana:11.3.0
USER root
RUN apk add --no-cache gettext
COPY datasources.yml.template /etc/grafana/provisioning/datasources/datasources.yml.template
COPY dashboard-provider.yml /etc/grafana/provisioning/dashboards/dashboard-provider.yml
COPY dashboards /etc/grafana/dashboards
COPY start.sh /usr/local/bin/start-grafana
RUN chmod +x /usr/local/bin/start-grafana \
    && chown -R 472:0 /etc/grafana/provisioning/datasources /etc/grafana/provisioning/dashboards /etc/grafana/dashboards
# The official image guarantees Grafana's numeric runtime UID (472), but not
# a stable passwd/group entry across tags. Numeric ownership avoids a build-time
# dependency on the image's user database.
USER 472
ENTRYPOINT ["/usr/local/bin/start-grafana"]
