package com.ibm.consulting.sim.scenario.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reversible, dependency-free persistence codec for authored success criteria.
 * The version prefix allows future evolution while the legacy decoder keeps
 * existing pipe-delimited scenario rows readable.
 */
final class SuccessCriteriaCodec {

    private static final String VERSION_PREFIX = "SC_LIST_B64_V1:";
    private static final String ITEM_DELIMITER = ".";
    /** URL-safe Base64 never emits '~', so it can represent an encoded empty item unambiguously. */
    private static final String EMPTY_ITEM = "~";
    private static final Pattern LEGACY_DELIMITER = Pattern.compile("\\|");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private SuccessCriteriaCodec() {}

    static String encode(List<String> criteria) {
        if (criteria == null || criteria.isEmpty()) return "";
        return VERSION_PREFIX + criteria.stream()
                .map(value -> Objects.requireNonNull(value, "success criterion cannot be null"))
                .map(SuccessCriteriaCodec::encodeItem)
                .collect(java.util.stream.Collectors.joining(ITEM_DELIMITER));
    }

    static List<String> decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        if (!encoded.startsWith(VERSION_PREFIX)) {
            return Arrays.stream(LEGACY_DELIMITER.split(encoded))
                    .map(String::strip)
                    .toList();
        }

        String payload = encoded.substring(VERSION_PREFIX.length());
        if (payload.isEmpty()) return List.of();
        try {
            return Arrays.stream(payload.split(Pattern.quote(ITEM_DELIMITER), -1))
                    .map(SuccessCriteriaCodec::decodeItem)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new InvalidScenarioAuthoringConfigException("Stored success criteria are not valid", exception);
        }
    }

    private static String encodeItem(String value) {
        String encoded = ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return encoded.isEmpty() ? EMPTY_ITEM : encoded;
    }

    private static String decodeItem(String value) {
        return EMPTY_ITEM.equals(value) ? "" : new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }
}
