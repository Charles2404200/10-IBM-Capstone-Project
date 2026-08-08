package com.ibm.consulting.sim.knowledge.domain;

/** Encodes/decodes an embedding vector to/from a compact comma-separated string for storage. */
final class EmbeddingCodec {

    private EmbeddingCodec() {}

    static String encode(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    static float[] decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new float[0];
        }
        String[] parts = encoded.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
