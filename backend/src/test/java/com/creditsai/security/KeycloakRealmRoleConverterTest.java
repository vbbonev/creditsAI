package com.creditsai.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void convert_realmRoles_mapsToRolePrefixedAuthorities() {
        // given
        Jwt jwt = jwtWithClaim("realm_access", Map.of("roles", List.of("USER", "ADMIN")));

        // when
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // then
        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void convert_noRealmAccessClaim_returnsEmpty() {
        // given
        Jwt jwt = jwtWithClaim("preferred_username", "user1");

        // when
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // then
        assertThat(authorities).isEmpty();
    }

    @Test
    void convert_realmAccessWithoutRoles_returnsEmpty() {
        // given
        Jwt jwt = jwtWithClaim("realm_access", Map.of("other", "value"));

        // when
        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        // then
        assertThat(authorities).isEmpty();
    }

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(name, value)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
