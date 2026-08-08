package com.ibm.consulting.sim.ai.domain;

/** Domain-neutral port for text embedding generation, used by the RAG pipeline (§5.5). */
public interface EmbeddingGateway {
    float[] embed(String text);
}
