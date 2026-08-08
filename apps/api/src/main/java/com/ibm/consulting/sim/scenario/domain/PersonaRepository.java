package com.ibm.consulting.sim.scenario.domain;

import java.util.Optional;
import java.util.UUID;

public interface PersonaRepository {
    Optional<Persona> findById(UUID id);
}
