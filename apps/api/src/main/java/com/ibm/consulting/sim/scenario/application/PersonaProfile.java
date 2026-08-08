package com.ibm.consulting.sim.scenario.application;

import com.ibm.consulting.sim.scenario.domain.Persona;

import java.io.Serializable;
import java.util.UUID;

/**
 * Cache-safe, serialization-friendly snapshot of a {@link Persona}. Deliberately
 * does not carry the lazy {@code scenario} back-reference the JPA entity has —
 * that reference would either trigger a {@code LazyInitializationException} or
 * an unbounded serialization graph when this value is written to a distributed
 * cache (Upstash/Redis) as JSON. Personas are authored content that changes
 * only through the admin API, so this is safe to cache with a TTL.
 */
public final class PersonaProfile implements Serializable {

    private UUID id;
    private String name;
    private String jobTitle;
    private String organisation;
    private String communicationStyle;
    private String visibleConcerns;
    private String hiddenConcerns;
    private String businessGoals;
    private int promptVersion;

    /** Required for JSON deserialization by the cache layer. */
    public PersonaProfile() {}

    private PersonaProfile(UUID id, String name, String jobTitle, String organisation,
                            String communicationStyle, String visibleConcerns, String hiddenConcerns,
                            String businessGoals, int promptVersion) {
        this.id = id;
        this.name = name;
        this.jobTitle = jobTitle;
        this.organisation = organisation;
        this.communicationStyle = communicationStyle;
        this.visibleConcerns = visibleConcerns;
        this.hiddenConcerns = hiddenConcerns;
        this.businessGoals = businessGoals;
        this.promptVersion = promptVersion;
    }

    public static PersonaProfile from(Persona persona) {
        return new PersonaProfile(
                persona.getId(), persona.getName(), persona.getJobTitle(), persona.getOrganisation(),
                persona.getCommunicationStyle(), persona.getVisibleConcerns(), persona.getHiddenConcerns(),
                persona.getBusinessGoals(), persona.getPromptVersion());
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getJobTitle() { return jobTitle; }
    public String getOrganisation() { return organisation; }
    public String getCommunicationStyle() { return communicationStyle; }
    public String getVisibleConcerns() { return visibleConcerns; }
    public String getHiddenConcerns() { return hiddenConcerns; }
    public String getBusinessGoals() { return businessGoals; }
    public int getPromptVersion() { return promptVersion; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setOrganisation(String organisation) { this.organisation = organisation; }
    public void setCommunicationStyle(String communicationStyle) { this.communicationStyle = communicationStyle; }
    public void setVisibleConcerns(String visibleConcerns) { this.visibleConcerns = visibleConcerns; }
    public void setHiddenConcerns(String hiddenConcerns) { this.hiddenConcerns = hiddenConcerns; }
    public void setBusinessGoals(String businessGoals) { this.businessGoals = businessGoals; }
    public void setPromptVersion(int promptVersion) { this.promptVersion = promptVersion; }
}
