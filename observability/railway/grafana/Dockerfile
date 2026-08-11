FROM grafana/grafana:11.3.0
USER root
RUN apk add --no-cache gettext
COPY datasources.yml.template /etc/grafana/provisioning/datasources/datasources.yml.template
COPY start.sh /usr/local/bin/start-grafana
RUN chmod +x /usr/local/bin/start-grafana \
    && chown -R grafana:grafana /etc/grafana/provisioning/datasources
USER grafana
ENTRYPOINT ["/usr/local/bin/start-grafana"]
