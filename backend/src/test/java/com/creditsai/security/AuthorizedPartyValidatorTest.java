package com.creditsai.security;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class AuthorizedPartyValidatorTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void authorizedPartyValidator_blankClientId_failsAtStartup() {
        // when / then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.authorizedPartyValidator(" "))
                .withMessageContaining("app.security.client-id");
    }

    @Test
    void authorizedPartyValidator_emptyClientId_failsAtStartup() {
        // when / then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> securityConfig.authorizedPartyValidator(""));
    }
}
