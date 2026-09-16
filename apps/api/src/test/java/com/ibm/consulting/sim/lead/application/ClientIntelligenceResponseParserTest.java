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
                      {"type": "PARAGRAPH", "content": "The board is reviewing a technology budget in the next quarter, providing a confirmed commercial signal that belongs in the current financial research picture.", "purpose": "FACT", "selectable": true, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "The budget review is the confirmed client signal in this document. It records active consideration of technology spending without stating a value, approved scope, owner, supplier, or implementation timetable.", "purpose": "FACT", "selectable": true, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "The timing attached to the known signal is the next quarter. That timing gives the item commercial relevance while leaving the decision process and criteria outside the confirmed facts.", "purpose": "FACT", "selectable": true, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "The scenario records a board review rather than a completed investment decision. The distinction matters because a review can precede approval, reprioritisation, delay, or a decision not to fund.", "purpose": "FACT", "selectable": true, "factIds": ["budget_signal"]},
                      {"type": "PARAGRAPH", "content": "The available financial evidence is therefore specific about the existence and timing of review activity, while it remains silent on the budget range, accountable executive, commercial baseline, and final outcome.", "purpose": "FACT", "selectable": true, "factIds": ["budget_signal"]}
                    ]
                  }]
                }
                """);

        assertThat(artifacts).hasSize(1);
        assertThat(artifacts.get(0).origin()).isEqualTo("AI_SYNTHESIZED");
        assertThat(artifacts.get(0).allowedFactKeys()).containsExactly("budget_signal");
        assertThat(artifacts.get(0).blocks()).hasSize(5);
                assertThat(artifacts.get(0).blocks()).allSatisfy(block -> {
                        assertThat(block.purpose().name()).isEqualTo("FACT");
                        assertThat(block.selectable()).isTrue();
                });
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
        void rejectsInterpretationBlocksInSourceDocuments() {
                assertThatThrownBy(() -> parser.parse(validFinancialDocumentJson()
                                .replaceFirst("\\\"purpose\\\":\\\"FACT\\\"", "\\\"purpose\\\":\\\"INTERPRETATION\\\"")))
                                .isInstanceOf(AiValidationException.class)
                                .hasMessageContaining("may only contain factual evidence blocks");
        }

            @Test
            void rejectsRepeatedEvidenceBlocks() {
                assertThatThrownBy(() -> parser.parse(validFinancialDocumentJson()
                        .replace("Entry 2 preserves", "Entry 1 preserves")))
                        .isInstanceOf(AiValidationException.class)
                        .hasMessageContaining("repeated evidence block");
            }

            @Test
            void rejectsResearchNarrativePadding() {
                assertThatThrownBy(() -> parser.parse(validFinancialDocumentJson()
                        .replaceFirst("The board is reviewing a technology budget in the next quarter",
                                "For this research angle, the document retains the reported point")))
                        .isInstanceOf(AiValidationException.class)
                        .hasMessageContaining("Consulting guidance cannot be emitted");
            }

            @Test
            void rejectsAnOverlongDocumentPreview() {
                assertThatThrownBy(() -> parser.parse(validFinancialDocumentJson()
                        .replaceFirst("Board reviewing technology budget next quarter\\.",
                                "This long preview repeats every client signal, operating issue, funding consideration, technology constraint, leadership priority, dispatch concern, maintenance delay, reliability target, value range, decision context, and scenario detail before the reader reaches the actual evidence blocks.")))
                        .isInstanceOf(AiValidationException.class)
                        .hasMessageContaining("document preview must be concise");
            }

        @Test
        void parsesJsonWrappedInAModelCodeFence() {
                var artifacts = parser.parse("```json\n" + validFinancialDocumentJson() + "\n```");

                assertThat(artifacts).hasSize(1);
                assertThat(artifacts.getFirst().blocks()).hasSize(5);
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
            assertThat(artifact.blocks()).hasSize(5);
            assertThat(artifact.blocks().stream().map(block -> block.content()).collect(java.util.stream.Collectors.joining(" ")))
                    .contains("Guest satisfaction varies");
            assertThat(artifact.allowedFactKeys()).contains("company_name", "observable_symptom");
            assertThat(artifact.blocks().stream().allMatch(block ->
                    block.purpose().name().equals("FACT") && block.selectable())).isTrue();
        });
    }

        private static String validFinancialDocumentJson() {
                String content = "The board is reviewing a technology budget in the next quarter. This confirmed commercial signal is retained in the financial briefing as a current point of reference for the client research narrative, without assigning an approval outcome, investment value, supplier, owner, or delivery date.";
                String blocks = java.util.stream.IntStream.rangeClosed(1, 10)
                                .limit(5)
                                .mapToObj(index -> "{\"type\":\"PARAGRAPH\",\"content\":\"" + content
                                                + " Entry " + index + " preserves the same reported signal in a distinct section of the long-form document.\",\"purpose\":\"FACT\",\"selectable\":true,\"factIds\":[\"budget_signal\"]}")
                                .collect(java.util.stream.Collectors.joining(","));
                return "{\"artifacts\":[{\"id\":\"financial-brief\",\"title\":\"Funding signal under review\",\"category\":\"FINANCIAL_SIGNAL\",\"content\":\"Board reviewing technology budget next quarter.\",\"sourceType\":\"FINANCIAL_REPORT\",\"reliability\":\"MEDIUM\",\"supportedFactIds\":[\"budget_signal\"],\"relevance\":0.82,\"confidence\":0.78,\"blocks\":[" + blocks + "]}]}";
        }
}
