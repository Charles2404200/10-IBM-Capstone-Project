package com.ibm.consulting.sim.lead.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
import com.ibm.consulting.sim.ai.infrastructure.MockAiGateway;
import com.ibm.consulting.sim.lead.domain.EvidenceType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIntelligenceResponseParserTest {

    private final ClientIntelligenceResponseParser parser = new ClientIntelligenceResponseParser(
            new ObjectMapper(),
            Map.of("budget_signal", "Board reviewing technology budget next quarter"),
            EvidenceType.FINANCIAL_SIGNAL);

    @Test
    void parsesValidStructuredArtifacts() {
        var artifacts = parser.parse("""
                {
                  "artifacts": [{
                    "id": "financial-brief",
                    "title": "Funding signal under review",
                    "category": "FINANCIAL_SIGNAL",
                    "content": "Board reviewing technology budget next quarter.",
                    "sourceType": "FINANCIAL_REPORT",
                    "reliability": "MEDIUM",
                    "supportedFactIds": ["budget_signal"],
                    "relevance": 0.82,
                    "confidence": 0.78,
                    "blocks": [
                      {"type": "PARAGRAPH", "content": "The board is reviewing a technology budget in the next quarter, providing a confirmed commercial signal for the current research record.", "purpose": "FACT", "selectable": true, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "The review indicates that investment appetite may be emerging, while the record does not establish a funded scope, owner, amount or approval decision.", "purpose": "INTERPRETATION", "selectable": true, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "This financial note is limited to the budget-review signal supplied in the scenario and contains no separate confirmation of business-case timing or investment authority.", "purpose": "CONTEXT", "selectable": false, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "No accountable sponsor, approval route, baseline measure or committed investment date has been confirmed in the available client facts.", "purpose": "UNCERTAINTY", "selectable": false, "factIds": []},
                      {"type": "CAPTION", "content": "Funding status remains unconfirmed in this source.", "purpose": "UNCERTAINTY", "selectable": false, "factIds": []}
                    ]
                  }]
                }
                """);

        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).origin()).isEqualTo("AI_SYNTHESIZED");
        assertThat(artifacts.get(0).allowedFactKeys()).containsExactly("budget_signal");
        assertThat(artifacts.get(0).blocks()).hasSize(5);
    }

    @Test
    void rejectsUnsupportedFactIds() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "artifacts": [{
                    "title": "Invented budget",
                    "category": "FINANCIAL_SIGNAL",
                    "content": "The client has $10M approved.",
                    "sourceType": "FINANCIAL_REPORT",
                    "reliability": "HIGH",
                    "supportedFactIds": ["invented_budget"]
                  }]
                }
                """))
                .isInstanceOf(AiValidationException.class)
                .hasMessageContaining("Unsupported fact id");
    }

    @Test
    void rejectsWrongCategory() {
        assertThatThrownBy(() -> parser.parse("""
                {
                  "artifacts": [{
                    "title": "Technology note",
                    "category": "TECHNOLOGY_INDICATOR",
                    "content": "A valid fact in the wrong category.",
                    "sourceType": "TECHNOLOGY_NOTE",
                    "reliability": "MEDIUM",
                    "supportedFactIds": ["budget_signal"]
                  }]
                }
                """))
                .isInstanceOf(AiValidationException.class)
                .hasMessageContaining("did not match requested");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not json"))
                .isInstanceOf(AiValidationException.class)
                .hasMessageContaining("Malformed client intelligence JSON");
    }

    @Test
    void mockGatewayBuildsGroundedMultiBlockDocumentsFromTheSourceDeckPrompt() {
        String raw = new MockAiGateway().complete("client_intelligence", """
                Research lane: COMPANY_NEWS
                Problem frame:
                - Business situation: Horizon Hotels is pursuing a brand-experience initiative across its properties.
                - Observable symptom: Guest satisfaction varies across properties because service issues are not resolved consistently.
                - Consulting mandate: Identify the operating hand-offs behind service variation and agree a low-risk pilot.
                - Unknowns to validate: Which guest journey varies most|Which property teams own the response
                Canonical facts (the complete allowed truth):
                - company_name: Horizon Hotels
                - business_situation: Horizon Hotels is pursuing a brand-experience initiative across its properties.
                - observable_symptom: Guest satisfaction varies across properties because service issues are not resolved consistently.
                - consulting_mandate: Identify the operating hand-offs behind service variation and agree a low-risk pilot.
                - signal_executive_priority: The brand-experience initiative depends on consistent guest service.
                - signal_operating_signal: Guest satisfaction varies across properties.
                """);
        ClientIntelligenceResponseParser companyNewsParser = new ClientIntelligenceResponseParser(
                new ObjectMapper(),
                Map.of(
                        "company_name", "Horizon Hotels",
                        "business_situation", "Horizon Hotels is pursuing a brand-experience initiative across its properties.",
                        "observable_symptom", "Guest satisfaction varies across properties because service issues are not resolved consistently.",
                        "consulting_mandate", "Identify the operating hand-offs behind service variation and agree a low-risk pilot.",
                        "signal_executive_priority", "The brand-experience initiative depends on consistent guest service.",
                        "signal_operating_signal", "Guest satisfaction varies across properties."),
                EvidenceType.COMPANY_NEWS);

        var artifacts = companyNewsParser.parse(raw);

        assertThat(artifacts).hasSize(2);
        assertThat(artifacts).allSatisfy(artifact -> {
            assertThat(artifact.blocks()).hasSize(7);
            assertThat(artifact.blocks().stream().map(block -> block.content()).collect(java.util.stream.Collectors.joining(" ")))
                    .contains("Guest satisfaction varies");
            assertThat(artifact.allowedFactKeys()).contains("company_name", "observable_symptom");
            assertThat(artifact.blocks().stream().filter(block -> block.selectable()).allMatch(block ->
                    block.purpose().name().equals("FACT") || block.purpose().name().equals("INTERPRETATION"))).isTrue();
        });
    }
}
