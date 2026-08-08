package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.PersonaRepository;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ibm.consulting.sim.shared.config.CacheConfig.PERSONA_CACHE;

/**
 * Read path for persona reference data, cached ahead of the live meeting hot
 * path (§13/§28 perf notes): persona identity and behavioural framing never
 * change during a meeting, so re-fetching it from Postgres on every message
 * is wasted round-trip latency. Cached under {@link com.ibm.consulting.sim.shared.config.CacheConfig#PERSONA_CACHE},
 * distributed via Upstash/Redis when {@code app.cache.provider=upstash}, or
 * JVM-local via Caffeine otherwise — callers never need to know which.
 */
@Service
public class PersonaCatalogService {

    private final PersonaRepository personaRepository;

    public PersonaCatalogService(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PERSONA_CACHE, key = "#personaId")
    public PersonaProfile getPersona(UUID personaId) {
        return personaRepository.findById(personaId)
                .map(PersonaProfile::from)
                .orElseThrow(() -> new NotFoundException("Persona", personaId));
    }
}
