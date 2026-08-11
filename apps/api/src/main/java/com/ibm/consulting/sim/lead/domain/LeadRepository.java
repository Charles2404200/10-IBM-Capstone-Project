package com.ibm.consulting.sim.lead.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository {
    List<Lead> findByScenarioId(UUID scenarioId);
    Optional<Lead> findById(UUID id);
    Lead save(Lead lead);
    void delete(Lead lead);
}
