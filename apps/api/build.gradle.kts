plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.ibm.consulting"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.12.6"
// 1.21.4 retains the 1.x API while adding compatibility with Docker Engine 29.
val testcontainersVersion = "1.21.4"
val springdocVersion = "2.6.0"
val logstashLogbackVersion = "8.0"

dependencies {
    // Web + Security
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackVersion")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Local caching (Phase 3 performance: reference-data caching ahead of Redis)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // Real-time bidirectional channel for the live meeting chat (STOMP over
    // WebSocket) — replaces the per-message HTTP+SSE round trip with a single
    // persistent connection per meeting session (see MeetingSocketController /
    // docs/architecture/LIVE_MEETING_REALTIME.md).
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // HTTP client for watsonx calls
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    //Kafka
    implementation ("org.springframework.kafka:spring-kafka")

    // Circuit breaker for the multi-provider AI orchestration layer (Gemini/OpenRouter/watsonx
    // fallback chain) — a free API rate-limiting or timing out must not cascade into every
    // learner request waiting out its full timeout before falling back.
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")

    // IBM Cloud Object Storage (S3-compatible)
    implementation(platform("software.amazon.awssdk:bom:2.28.10"))
    implementation("software.amazon.awssdk:s3")

    // Test
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("aiEvaluation") {
    group = "verification"
    description = "Runs the versioned AI behaviour regression corpus."
    useJUnitPlatform()
    include("**/AiEvaluationRegressionSuiteTest.class")
    include("**/PersonaPromptContractRegressionTest.class")
}

tasks.named<Test>("test") {
    exclude("**/AiEvaluationRegressionSuiteTest.class")
    exclude("**/PersonaPromptContractRegressionTest.class")
}

tasks.named("check") {
    dependsOn("aiEvaluation")
}

// Load variables from the repo-root .env file (if present) into the bootRun
// task's environment so `./gradlew bootRun` works the same way docker-compose
// does, without requiring developers to manually export every variable.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = rootProject.projectDir.resolve("../../.env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("=") }
            .forEach { line ->
                val (key, value) = line.split("=", limit = 2)
                environment(key.trim(), value.trim())
            }
    }
}
