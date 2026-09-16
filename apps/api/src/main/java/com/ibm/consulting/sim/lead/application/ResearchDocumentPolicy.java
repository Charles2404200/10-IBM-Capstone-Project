package com.ibm.consulting.sim.lead.application;

/** Size and evidence-density contract for a rendered research source. */
public record ResearchDocumentPolicy(int minimumBlocks, int maximumBlocks, int minimumWords, int maximumWords) {

    public static ResearchDocumentPolicy compact() {
        return new ResearchDocumentPolicy(5, 6, 0, 1_000);
    }

    public static ResearchDocumentPolicy corpusBacked() {
        return new ResearchDocumentPolicy(4, 6, 180, 320);
    }
}