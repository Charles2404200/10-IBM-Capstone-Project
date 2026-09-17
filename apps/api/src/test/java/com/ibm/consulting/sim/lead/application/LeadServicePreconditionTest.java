package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import com.ibm.consulting.sim.engagement.domain.EngagementRepository;
import com.ibm.consulting.sim.lead.domain.ConfidenceLevel;
import com.ibm.consulting.sim.lead.domain.EvidenceOrigin;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import com.ibm.consulting.sim.lead.domain.EvidenceVerificationStatus;
import com.ibm.consulting.sim.lead.domain.LeadRepository;
import com.ibm.consulting.sim.lead.domain.ResearchEvidenceRepository;
import com.ibm.consulting.sim.scenario.application.DifficultyProfileService;
import com.ibm.consulting.sim.scenario.application.ScenarioAuthoringConfigService;
import com.ibm.consulting.sim.scenario.domain.ScenarioRepository;
import com.ibm.consulting.sim.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LeadServicePreconditionTest {

    @Test
    void savingResearchWithoutASelectedLeadIsATypedDomainFailure() {
        Fixture fixture = fixtureWithoutSelectedLead();

        assertThatThrownBy(() -> fixture.service().saveEvidence(
                fixture.engagementId(), fixture.userId(), "Valid research note", null,
                EvidenceType.COMPANY_NEWS, null, null, EvidenceOrigin.USER_SUPPLIED,
                EvidenceVerificationStatus.UNVERIFIED, null, ConfidenceLevel.MEDIUM,
                35, null, Set.of()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("lead")
                .hasMessageContaining(fixture.engagementId().toString());

        verifyNoInteractions(fixture.evidence());
    }

    @Test
    void readingIntelligenceWithoutASelectedLeadIsATypedDomainFailure() {
        Fixture fixture = fixtureWithoutSelectedLead();

        assertThatThrownBy(() -> fixture.service().getIntelligence(
                fixture.engagementId(), fixture.userId()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("lead")
                .hasMessageContaining(fixture.engagementId().toString());

        verifyNoInteractions(fixture.leads(), fixture.evidence());
    }

    private Fixture fixtureWithoutSelectedLead() {
        UUID userId = UUID.randomUUID();
        Engagement engagement = Engagement.start(userId, UUID.randomUUID(), UUID.randomUUID());
        EngagementRepository engagements = mock(EngagementRepository.class);
        LeadRepository leads = mock(LeadRepository.class);
        ResearchEvidenceRepository evidence = mock(ResearchEvidenceRepository.class);
        when(engagements.findByIdAndUserIdForUpdate(engagement.getId(), userId))
                .thenReturn(Optional.of(engagement));
        when(engagements.findByIdAndUserId(engagement.getId(), userId))
                .thenReturn(Optional.of(engagement));
        LeadService service = new LeadService(leads, evidence, engagements,
                mock(DifficultyProfileService.class), mock(ScenarioRepository.class),
                mock(ScenarioAuthoringConfigService.class));
        return new Fixture(userId, engagement.getId(), service, leads, evidence);
    }

    private record Fixture(UUID userId, UUID engagementId, LeadService service,
                           LeadRepository leads, ResearchEvidenceRepository evidence) {}
}
