package com.creditsai.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns the Keycloak realm roles in a JWT ({@code realm_access.roles}) into Spring Security
 * authorities with the {@code ROLE_} prefix, so {@code USER} becomes {@code ROLE_USER} and
 * {@code @PreAuthorize("hasRole('USER')")} works.
 *
 * <p>Implements the <b>Adapter</b> pattern: it adapts Keycloak's token layout to Spring Security's
 * authority model.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_KEY = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Reads the realm roles from the token and maps each one to a {@code ROLE_*} authority.
     *
     * @param jwt the validated access token
     * @return the authorities, or an empty list if the token carries no realm roles
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
