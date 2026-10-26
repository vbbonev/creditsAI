package com.creditsai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security setup for a stateless OAuth2 resource server that only validates Keycloak JWTs.
 * There is no login, session or local user store: Keycloak authenticates, the backend authorizes.
 *
 * <p>Requests are denied by default; only the health endpoint is public. Role checks happen per
 * endpoint with {@code @PreAuthorize}, enabled here with {@link EnableMethodSecurity}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String AUTHORIZED_PARTY_CLAIM = "azp";

    /**
     * Builds the HTTP security filter chain: stateless, no CSRF (no cookies are used), CORS from
     * {@link #corsConfigurationSource()}, and JWT bearer authentication.
     *
     * @param http the security builder provided by Spring
     * @param jwtAuthenticationConverter maps Keycloak roles to authorities
     * @return the configured filter chain
     * @throws Exception if the security builder fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    /**
     * Creates the single converter that turns a validated JWT into an authentication whose
     * authorities are the Keycloak realm roles ({@code ROLE_USER}, {@code ROLE_ADMIN}).
     *
     * @return the JWT authentication converter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    /**
     * Accepts only tokens issued to the frontend client: the {@code azp} (authorized party) claim
     * must equal the configured client id. Without it, a token the {@code credits} realm issued to
     * any other client (e.g. {@code admin-cli}) would be accepted too. Spring Boot adds this bean
     * to the auto-configured {@code JwtDecoder}, on top of the signature, expiry and issuer checks.
     *
     * <p>The claim is typed as {@code Object} so a non-string {@code azp} is simply rejected (401)
     * instead of failing with a {@code ClassCastException} (500).
     *
     * @param clientId the expected {@code azp} value, from {@code app.security.client-id}
     * @return the token validator
     * @throws IllegalArgumentException if the client id is blank, so a misconfiguration fails at
     *         startup instead of silently rejecting every request
     */
    @Bean
    public OAuth2TokenValidator<Jwt> authorizedPartyValidator(@Value("${app.security.client-id}") String clientId) {
        Assert.hasText(clientId, "app.security.client-id must be set");
        return new JwtClaimValidator<Object>(AUTHORIZED_PARTY_CLAIM, clientId::equals);
    }

    /**
     * Allows no cross-origin requests: the SPA and the API share the origin {@code http://localhost}
     * behind nginx. The mapping is still registered with no allowed origins, so every cross-origin
     * preflight is actively rejected with 403 rather than passed through.
     *
     * @return the CORS configuration used by the filter chain
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", new CorsConfiguration());
        return source;
    }
}
