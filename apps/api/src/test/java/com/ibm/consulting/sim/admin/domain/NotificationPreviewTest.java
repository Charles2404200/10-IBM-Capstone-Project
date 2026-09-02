package com.ibm.consulting.sim.admin.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationPreviewTest {

    @Test
    void truncatesWithoutSplittingSupplementaryUnicodeCharacters() {
        String message = "😀".repeat(200);
        String preview = NotificationPreview.from(message);

        assertEquals(NotificationPreview.MAX_CODE_POINTS, preview.codePointCount(0, preview.length()));
        //checks that the preview does not contain the Unicode replacement character
        assertFalse(preview.contains("\uFFFD"));
        //That is a proper ellipsis character, not three dots:
        assertEquals('…', preview.charAt(preview.length() - 1));
    }
}

