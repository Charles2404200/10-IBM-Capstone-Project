package com.ibm.consulting.sim.knowledge.api;

/** Request limits that bound persistence and embedding work for one knowledge document. */
final class KnowledgeDocumentLimits {
    static final int MAX_TITLE_LENGTH = 255;
    static final int MAX_CONTENT_LENGTH = 50_000;

    private KnowledgeDocumentLimits() {}
}
