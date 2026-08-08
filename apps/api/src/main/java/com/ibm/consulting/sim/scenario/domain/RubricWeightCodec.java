package com.ibm.consulting.sim.scenario.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes/decodes scenario rubric weights (competency name → weight percent) to a
 * compact delimited string for storage, avoiding a hard dependency from the domain
 * layer on a JSON library.
 */
final class RubricWeightCodec {

    private RubricWeightCodec() {}

    static String encode(Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        weights.forEach((name, weight) -> {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(name.replace(":", "").replace(";", "")).append(':').append(weight);
        });
        return sb.toString();
    }

    static Map<String, Integer> decode(String encoded) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String entry : encoded.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":", 2);
            if (parts.length == 2) {
                result.put(parts[0], Integer.parseInt(parts[1]));
            }
        }
        return result;
    }
}
