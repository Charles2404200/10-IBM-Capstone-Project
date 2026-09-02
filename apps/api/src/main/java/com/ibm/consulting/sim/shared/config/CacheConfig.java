package com.ibm.consulting.sim.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRedisCacheManager;
import com.ibm.consulting.sim.shared.infrastructure.cache.UpstashRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Caching for read-heavy, low-churn reference data — scenario catalogues,
 * per-scenario lead lists, and (§13/§28 perf notes) persona identity used on
 * every Live Meeting turn.
 *
 * <p>The provider is selected by {@code app.cache.provider} and is
 * transparent to every {@code @Cacheable}/{@code @CacheEvict} call site,
 * which only ever depend on the standard Spring {@link CacheManager} SPI:
 * <ul>
 *   <li>{@code caffeine} (default) — JVM-local, in-process. Correct for a
 *       single instance; a second app instance would not see another
 *       instance's writes/evictions.</li>
 *   <li>{@code upstash} — distributed via the Upstash Redis REST API. Safe
 *       across multiple instances/replicas and survives app restarts.</li>
 * </ul>
 *
 * <p><b>Swapping in IBM's own Redis later:</b> add
 * {@code spring-boot-starter-data-redis} to {@code build.gradle.kts}, add a
 * third {@code @Bean} here — a standard {@code RedisCacheManager} built from
 * a {@code LettuceConnectionFactory} pointed at the IBM Cloud Databases for
 * Redis host/port/password — guarded by
 * {@code @ConditionalOnProperty(name = "app.cache.provider", havingValue = "redis")},
 * and set {@code CACHE_PROVIDER=redis} in the environment. No other class in
 * the codebase references Upstash or Caffeine directly, so this is a
 * config-only change.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SCENARIOS_CACHE = "scenarios";
    public static final String SCENARIO_CACHE = "scenario";
    public static final String SCENARIO_CATALOG_CACHE = "scenarioCatalog";
    public static final String SCENARIO_CATALOG_FACETS_CACHE = "scenarioCatalogFacets";
    public static final String LEADS_BY_SCENARIO_CACHE = "leadsByScenario";
    public static final String LEAD_CATALOG_CACHE = "leadCatalog";
    public static final String LEAD_CATALOG_FACETS_CACHE = "leadCatalogFacets";
    public static final String PERSONA_CACHE = "persona";
    public static final String CLIENT_INTELLIGENCE_CACHE = "clientIntelligence";
    /** Short-lived per-learner read models used on the Command Centre. */
    public static final String ENGAGEMENT_DASHBOARD_CACHE = "engagementDashboard";
    public static final String PORTFOLIO_SUMMARY_CACHE = "portfolioSummary";
    /** Short-lived, cross-user read model backing the administrative cockpit. */
    public static final String ADMIN_PLATFORM_OVERVIEW_CACHE = "adminPlatformOverview";
    /** Paged authoring catalogue; intentionally separate from learner-visible scenarios. */
    public static final String ADMIN_SCENARIO_CATALOG_CACHE = "adminScenarioCatalog";
    /** Immutable AI coaching result keyed by the complete proposal and source snapshot. */
    public static final String PROPOSAL_REVIEW_CACHE = "proposalReview";
    /** Natural-language rendering of an already-determined client decision. */
    public static final String PROPOSAL_DECISION_NARRATIVE_CACHE = "proposalDecisionNarrative";
    /** Structured AI coaching keyed by a completed deterministic assessment snapshot. */
    public static final String ASSESSMENT_FEEDBACK_CACHE = "assessmentFeedback";
    /** Immutable role-scoped notification bodies; user-specific read state is never cached here. */
    public static final String NOTIFICATION_DETAIL_CACHE = "notificationDetail";

    @Bean
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                localCache(SCENARIOS_CACHE, Duration.ofMinutes(10)),
                localCache(SCENARIO_CACHE, Duration.ofMinutes(10)),
                localCache(SCENARIO_CATALOG_CACHE, Duration.ofMinutes(10)),
                localCache(SCENARIO_CATALOG_FACETS_CACHE, Duration.ofMinutes(10)),
                localCache(LEADS_BY_SCENARIO_CACHE, Duration.ofMinutes(10)),
                localCache(LEAD_CATALOG_CACHE, Duration.ofMinutes(10)),
                localCache(LEAD_CATALOG_FACETS_CACHE, Duration.ofMinutes(10)),
                localCache(PERSONA_CACHE, Duration.ofMinutes(30)),
                localCache(CLIENT_INTELLIGENCE_CACHE, Duration.ofMinutes(10)),
                localCache(ENGAGEMENT_DASHBOARD_CACHE, Duration.ofSeconds(30)),
                localCache(PORTFOLIO_SUMMARY_CACHE, Duration.ofSeconds(60)),
                localCache(ADMIN_PLATFORM_OVERVIEW_CACHE, Duration.ofSeconds(20)),
                localCache(ADMIN_SCENARIO_CATALOG_CACHE, Duration.ofSeconds(45)),
                localCache(PROPOSAL_REVIEW_CACHE, Duration.ofMinutes(15)),
                localCache(PROPOSAL_DECISION_NARRATIVE_CACHE, Duration.ofHours(1)),
                localCache(ASSESSMENT_FEEDBACK_CACHE, Duration.ofHours(1)),
                localCache(NOTIFICATION_DETAIL_CACHE, Duration.ofMinutes(5))));
        manager.initializeCaches();
        return manager;
    }

    private CaffeineCache localCache(String name, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.provider", havingValue = "upstash")
    public CacheManager upstashCacheManager(
            UpstashRestClient upstashRestClient,
            @Value("${app.cache.default-ttl-seconds:600}") long defaultTtlSeconds,
            @Value("${app.cache.persona-ttl-seconds:1800}") long personaTtlSeconds) {
        // Persona reference data changes only via the admin authoring API, so it
        // can safely be cached longer than the general default (used by e.g.
        // scenario summaries, which authors also edit but check more often).
        Map<String, Duration> ttlOverrides = Map.of(
                PERSONA_CACHE, Duration.ofSeconds(personaTtlSeconds),
                ENGAGEMENT_DASHBOARD_CACHE, Duration.ofSeconds(30),
                PORTFOLIO_SUMMARY_CACHE, Duration.ofSeconds(60),
                ADMIN_PLATFORM_OVERVIEW_CACHE, Duration.ofSeconds(20),
                ADMIN_SCENARIO_CATALOG_CACHE, Duration.ofSeconds(45),
                PROPOSAL_REVIEW_CACHE, Duration.ofMinutes(15),
                PROPOSAL_DECISION_NARRATIVE_CACHE, Duration.ofHours(1),
                ASSESSMENT_FEEDBACK_CACHE, Duration.ofHours(1),
                NOTIFICATION_DETAIL_CACHE, Duration.ofMinutes(5));
        return new UpstashRedisCacheManager(upstashRestClient, Duration.ofSeconds(defaultTtlSeconds), ttlOverrides);
    }
}
