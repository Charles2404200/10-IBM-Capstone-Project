package com.ibm.consulting.sim.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialTokenServiceTest {

    private final CredentialTokenService service = new CredentialTokenService();

    @Test
    void persistsAndParsesOnlyTheSelectorAndHashedSecret() {
        CredentialTokenService.IssuedCredential issued = service.issue();

        CredentialTokenService.ParsedCredential parsed = service.parse(issued.compactToken());

        assertThat(parsed.selector()).isEqualTo(issued.selector());
        assertThat(parsed.secret()).isEqualTo(issued.secret());
        assertThat(issued.hash()).doesNotContain(issued.secret());
        assertThat(service.matches(issued.hash(), parsed.secret())).isTrue();
    }

    @Test
    void rejectsAnIncorrectSecret() {
        CredentialTokenService.IssuedCredential issued = service.issue();

        assertThat(service.matches(issued.hash(), "incorrect-secret")).isFalse();
    }

    @Test
    void rejectsMalformedCompactTokens() {
        assertThatThrownBy(() -> service.parse("missing-separator"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parse("selector."))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
