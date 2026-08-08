##
## Stage 1: compile + package. Produces a Spring Boot "layered" jar, which
## `layertools` below splits into dependency/resource/application layers —
## the standard enterprise pattern for Spring Boot container images, since
## the (large, rarely-changing) dependency layer is cached independently of
## the (small, frequently-changing) application-code layer. The Gradle
## dependency cache itself is kept in a BuildKit cache mount, so repeat
## builds don't re-download the internet even when build files change.
##
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies -q || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test -q

FROM eclipse-temurin:21-jdk AS layers
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

##
## Stage 3: runtime image — minimal JRE, non-root user, only the extracted
## layers (no Gradle, no source, no build cache) ship in the final image.
##
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./
USER spring:spring
EXPOSE 8080

# Container-aware heap sizing (respects the cgroup memory limit set via
# `deploy.resources` in docker-compose / Kubernetes requests-limits) instead
# of a hardcoded -Xmx that would either waste or exceed the container quota.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
