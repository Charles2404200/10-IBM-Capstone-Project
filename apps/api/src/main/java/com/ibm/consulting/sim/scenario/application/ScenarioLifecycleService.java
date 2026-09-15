package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;

import static com.ibm.consulting.sim.shared.config.CacheConfig.*;

@Service
public class ScenarioLifecycleService {
    private final ScenarioRepository scenarios;
    private final LifecycleDefinitionCodec codec;
    private final AuditLogger audit;

    public ScenarioLifecycleService(ScenarioRepository scenarios, LifecycleDefinitionCodec codec, AuditLogger audit) {
        this.scenarios = scenarios;
        this.codec = codec;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ScenarioLifecycleView get(UUID scenarioId) {
        return view(find(scenarioId));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = {SCENARIOS_CACHE, SCENARIO_CATALOG_CACHE, SCENARIO_CATALOG_FACETS_CACHE,
                    ADMIN_SCENARIO_CATALOG_CACHE, ADMIN_PLATFORM_OVERVIEW_CACHE}, allEntries = true)
    })
    public ScenarioLifecycleView update(UUID scenarioId, UpdateScenarioLifecycleRequest request) {
        Scenario scenario = find(scenarioId);
        if (scenario.getStatus() != ScenarioStatus.DRAFT) {
            throw new Scenario.ScenarioNotEditableException(scenario.getStatus());
        }
        if (request.version() == null || !Objects.equals(request.version(), scenario.getVersion())) {
            throw new ScenarioLifecycleConflictException();
        }
        String encoded = codec.encode(request.definition());
        scenario.updateLifecycleDefinition(encoded);
        scenarios.save(scenario);
        // Flush before returning the new token; a concurrent writer must fail before acknowledgement.
        scenarios.flush();
        String summary = "revision=%d; stages=%d; objectives=%d".formatted(scenario.getContentVersion(),
                request.definition().stages().size(), request.definition().objectives().size());
        Runnable record = () -> audit.recordAdmin(AuditAction.ADMIN_SCENARIO_LIFECYCLE_CHANGED,
                "SCENARIO", scenarioId.toString(), summary);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { record.run(); }
            });
        } else {
            record.run();
        }
        return view(scenario);
    }

    private Scenario find(UUID id) {
        return scenarios.findById(id).orElseThrow(() -> new NotFoundException("Scenario", id));
    }

    private ScenarioLifecycleView view(Scenario scenario) {
        return new ScenarioLifecycleView(codec.decode(scenario.getLifecycleDefinition(), scenario.getObjective()),
                scenario.getVersion());
    }
}
