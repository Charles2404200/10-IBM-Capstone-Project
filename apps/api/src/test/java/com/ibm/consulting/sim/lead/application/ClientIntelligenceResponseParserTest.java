package com.ibm.consulting.sim.lead.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.consulting.sim.ai.domain.AiValidationException;
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
                    "confidence": 0.78
                  }]
                }
                """);

        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).origin()).isEqualTo("AI_SYNTHESIZED");
        assertThat(artifacts.get(0).allowedFactKeys()).containsExactly("budget_signal");
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
}
