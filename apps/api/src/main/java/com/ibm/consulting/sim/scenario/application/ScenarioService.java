package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.Persona;
import com.ibm.consulting.sim.scenario.domain.Scenario;
import com.ibm.consulting.sim.scenario.domain.ScenarioCatalogQuery;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.scenario.domain.ScenarioStatus;
import com.ibm.consulting.sim.scenario.domain.ScenarioAuthoringConfig;
import com.ibm.consulting.sim.lead.application.LeadSummary;
import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.knowledge.application.KnowledgeIngestionService;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static com.ibm.consulting.sim.shared.config.CacheConfig.SCENARIOS_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.SCENARIO_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.SCENARIO_CATALOG_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.SCENARIO_CATALOG_FACETS_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.LEADS_BY_SCENARIO_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.LEAD_CATALOG_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.LEAD_CATALOG_FACETS_CACHE;
import static com.ibm.consulting.sim.shared.config.CacheConfig.ADMIN_PLATFORM_OVERVIEW_CACHE;

@Service
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final DifficultyProfileService difficultyProfileService;
    private final ScenarioAuthoringConfigService authoringConfigService;
    private final LeadRepository leadRepository;
    private final KnowledgeIngestionService knowledgeIngestionService;

    public ScenarioService(ScenarioRepository scenarioRepository, DifficultyProfileService difficultyProfileService,
                           ScenarioAuthoringConfigService authoringConfigService, LeadRepository leadRepository,
                           KnowledgeIngestionService knowledgeIngestionService) {
        this.scenarioRepository = scenarioRepository;
        this.difficultyProfileService = difficultyProfileService;
        this.authoringConfigService = authoringConfigService;
        this.leadRepository = leadRepository;
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SCENARIOS_CACHE, key = "'active'")
    public List<ScenarioSummary> listActive() {
        return scenarioRepository.findAllActive().stream()
                .map(this::summary)
                .toList();
    }

    /** Bounded learner catalogue query that never loads every scenario into a dashboard request. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SCENARIO_CATALOG_CACHE, key = "#query.cacheKey()")
    public ScenarioCatalogResponse listCatalog(ScenarioCatalogQuery query) {
        return ScenarioCatalogResponse.from(scenarioRepository.findCatalog(query), this::summary);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, key = "'industries'")
    public List<String> listCatalogIndustries() {
        return scenarioRepository.findCatalogIndustries();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = SCENARIO_CACHE, key = "#id")
    public ScenarioSummary getById(UUID id) {
        return scenarioRepository.findById(id)
                .map(this::summary)
                .orElseThrow(() -> new NotFoundException("Scenario", id));
    }

    /** Admin capability: list every scenario regardless of status (DRAFT/ACTIVE/ARCHIVED). */
    @Transactional(readOnly = true)
    public List<ScenarioSummary> listAllForAdmin() {
        return scenarioRepository.findAll().stream()
                .map(this::summary)
                .toList();
    }

    /** Author/admin capability: create a new scenario in DRAFT state. */
    @Transactional
    @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    public ScenarioSummary create(CreateScenarioRequest request) {
        Scenario scenario = Scenario.create(
                request.title(), request.industry(), request.description(), request.difficulty());
        return summary(scenarioRepository.save(scenario));
    }

    /** Author/admin capability: attach a persona to an existing scenario. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioSummary addPersona(UUID scenarioId, CreatePersonaRequest request) {
        Scenario scenario = findScenario(scenarioId);
        Persona persona = scenario.addPersona(
                request.name(), request.jobTitle(), request.organisation(), request.communicationStyle(),
                request.visibleConcerns(), request.hiddenConcerns(), request.businessGoals());
        scenarioRepository.save(scenario);
        return summary(scenario);
    }

    /** Author/admin capability: publish a DRAFT scenario so it becomes visible to learners. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioSummary publish(UUID scenarioId) {
        Scenario scenario = findScenario(scenarioId);
        ScenarioAuthoringView.Readiness readiness = readiness(scenario);
        if (!readiness.readyToPublish()) throw new ScenarioNotReadyException(readiness.blockers());
        scenarioRepository.findByLineageIdAndStatus(scenario.getScenarioLineageId(), ScenarioStatus.ACTIVE)
                .stream().filter(active -> !active.getId().equals(scenarioId)).forEach(Scenario::archive);
        scenario.publish();
        return summary(scenarioRepository.save(scenario));
    }

    /** Author/admin capability: retire a scenario so it no longer appears for new engagements. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioSummary archive(UUID scenarioId) {
        Scenario scenario = findScenario(scenarioId);
        scenario.archive();
        return summary(scenarioRepository.save(scenario));
    }

    /** Author/admin capability: customise how much each competency contributes to the overall score. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioSummary updateRubricWeights(UUID scenarioId, Map<String, Integer> weights) {
        Scenario scenario = findScenario(scenarioId);
        scenario.updateRubricWeights(weights);
        return summary(scenarioRepository.save(scenario));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioSummary updateDifficultyProfile(UUID scenarioId, UpdateDifficultyProfileRequest request) {
        Scenario scenario = findScenario(scenarioId);
        difficultyProfileService.updateScenarioProfile(scenario, request.profile());
        return summary(scenarioRepository.save(scenario));
    }

    @Transactional(readOnly = true)
    public ScenarioAuthoringView authoringView(UUID scenarioId) {
        Scenario scenario = findScenario(scenarioId);
        return new ScenarioAuthoringView(summary(scenario), scenario.getScenarioLineageId(),
                authoringConfigService.forScenario(scenario), readiness(scenario));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioAuthoringView updateBlueprint(UUID scenarioId, UpdateScenarioBlueprintRequest request) {
        Scenario scenario = findScenario(scenarioId);
        scenario.updateMetadata(request.title(), request.industry(), request.description(), request.difficulty());
        scenario.updateBriefing(request.consultantRole(), request.objective(), request.successCriteria(), request.simulatedDays());
        scenario.updateDifficultyDimensions(request.informationAmbiguity(), request.stakeholderComplexity(), request.commercialPressure());
        scenarioRepository.save(scenario);
        return authoringView(scenarioId);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioAuthoringView updateAuthoringConfig(UUID scenarioId, ScenarioAuthoringConfig config) {
        Scenario scenario = findScenario(scenarioId);
        authoringConfigService.update(scenario, config);
        scenarioRepository.save(scenario);
        return authoringView(scenarioId);
    }

    /** Forks all authored content into a new draft revision; existing engagements stay on the source revision. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = SCENARIOS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public ScenarioAuthoringView createRevision(UUID scenarioId) {
        Scenario source = findScenario(scenarioId);
        Scenario revision = scenarioRepository.save(source.createRevision());

        Map<UUID, UUID> personaIdMap = new LinkedHashMap<>();
        source.getPersonas().forEach(persona -> {
            var copy = revision.addPersona(persona.getName(), persona.getJobTitle(), persona.getOrganisation(),
                    persona.getCommunicationStyle(), persona.getVisibleConcerns(), persona.getHiddenConcerns(), persona.getBusinessGoals());
            personaIdMap.put(persona.getId(), copy.getId());
        });
        scenarioRepository.save(revision);

        leadRepository.findByScenarioId(source.getId()).forEach(lead -> leadRepository.save(Lead.copyForScenario(lead, revision.getId())));
        knowledgeIngestionService.copyScenarioDocuments(source.getId(), revision.getId(), personaIdMap);
        return authoringView(revision.getId());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = LEADS_BY_SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = LEAD_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public LeadSummary createLead(UUID scenarioId, LeadAuthoringRequest request) {
        Scenario scenario = findScenario(scenarioId);
        assertDraft(scenario);
        Lead lead = Lead.create(scenarioId, request.companyName(), request.industry(), request.publicDescription(), request.difficulty());
        configureLead(lead, request);
        return LeadSummary.from(leadRepository.save(lead));
    }

    @Transactional(readOnly = true)
    public List<LeadAuthoringView> listAuthoringLeads(UUID scenarioId) {
        findScenario(scenarioId);
        return leadRepository.findByScenarioId(scenarioId).stream().map(LeadAuthoringView::from).toList();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = LEADS_BY_SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = LEAD_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public LeadSummary updateLead(UUID scenarioId, UUID leadId, LeadAuthoringRequest request) {
        Scenario scenario = findScenario(scenarioId);
        assertDraft(scenario);
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new NotFoundException("Lead", leadId));
        if (!lead.getScenarioId().equals(scenarioId)) throw new IllegalArgumentException("Lead does not belong to this scenario");
        configureLead(lead, request);
        return LeadSummary.from(leadRepository.save(lead));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = LEADS_BY_SCENARIO_CACHE, key = "#scenarioId"),
            @CacheEvict(cacheNames = LEAD_CATALOG_CACHE, allEntries = true),
            @CacheEvict(cacheNames = LEAD_CATALOG_FACETS_CACHE, allEntries = true),
            @CacheEvict(cacheNames = ADMIN_PLATFORM_OVERVIEW_CACHE, allEntries = true)
    })
    public void deleteLead(UUID scenarioId, UUID leadId) {
        Scenario scenario = findScenario(scenarioId);
        assertDraft(scenario);
        Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new NotFoundException("Lead", leadId));
        if (!lead.getScenarioId().equals(scenarioId)) throw new IllegalArgumentException("Lead does not belong to this scenario");
        leadRepository.delete(lead);
    }

    private Scenario findScenario(UUID scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new NotFoundException("Scenario", scenarioId));
    }

    private ScenarioAuthoringView.Readiness readiness(Scenario scenario) {
        List<String> blockers = new ArrayList<>();
        ScenarioAuthoringConfig config = authoringConfigService.forScenario(scenario);
        int personas = scenario.getPersonas().size();
        int leads = leadRepository.findByScenarioId(scenario.getId()).size();
        if (personas == 0) blockers.add("Add at least one client persona.");
        if (leads == 0) blockers.add("Add at least one lead definition.");
        if (scenario.getObjective() == null || scenario.getObjective().isBlank()) blockers.add("Define the learner objective.");
        if (config.canonicalFacts().isEmpty()) blockers.add("Add scenario-approved canonical facts.");
        if (config.revealRules().isEmpty()) blockers.add("Define intelligence reveal rules.");
        if (scenario.getRubricWeights().isEmpty()) blockers.add("Save competency rubric weights.");
        return new ScenarioAuthoringView.Readiness(blockers.isEmpty(), List.copyOf(blockers), personas, leads,
                config.canonicalFacts().size(), config.revealRules().size());
    }

    private void configureLead(Lead lead, LeadAuthoringRequest request) {
        List<Lead.SignalInput> signals = request.signals() == null ? List.of() : request.signals().stream()
                .map(signal -> new Lead.SignalInput(signal.label(), signal.category())).toList();
        lead.configure(request.companyName(), request.industry(), request.publicDescription(), request.difficulty(),
                request.potentialValueRange(), request.decisionMaker(), request.technologyStack(), request.budgetSignal(),
                request.painSeverity(), signals);
    }

    private void assertDraft(Scenario scenario) {
        if (scenario.getStatus() != ScenarioStatus.DRAFT) {
            throw new Scenario.ScenarioNotEditableException(scenario.getStatus());
        }
    }

    private ScenarioSummary summary(Scenario scenario) {
        return ScenarioSummary.from(scenario, difficultyProfileService.forScenario(scenario));
    }
}
