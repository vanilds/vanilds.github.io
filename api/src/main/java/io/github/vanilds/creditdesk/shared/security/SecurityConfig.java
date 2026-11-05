package io.github.vanilds.creditdesk.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API is a stateless OAuth2 <b>resource server</b>: it never sees a password. Keycloak signs a JWT, the
 * browser sends it as {@code Authorization: Bearer …}, and the API checks four things on every request:
 * signature (public keys from the JWKS endpoint), expiry, issuer and audience.
 *
 * <ul>
 *   <li><b>Why issuer and JWKS URL are separate settings:</b> the issuer is the public URL written inside the
 *   token ({@code http://localhost:8180/realms/creditdesk}); the JWKS is fetched over the internal network
 *   ({@code http://keycloak:8080/…}). With only {@code issuer-uri}, the container would have to reach
 *   "localhost:8180", which inside Docker/Kubernetes is itself.</li>
 *   <li><b>Why check the audience:</b> any client of the realm can get a valid token. {@code aud} must contain
 *   {@code creditdesk-api}, so a token minted for another application is refused here.</li>
 *   <li><b>Why CSRF is off:</b> CSRF abuses cookies the browser sends automatically. A bearer token in a header is
 *   never sent automatically, so there is nothing to forge.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity // @PreAuthorize on controller methods
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, ProblemSecurityHandlers problems) throws Exception {
        var authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Kubernetes probes and Prometheus scrape from inside the cluster (network policy: Phase 7)
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // The contract is public on purpose: the front-ends generate their clients from it
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Fine-grained rules (who can approve, who can read the audit log) are @PreAuthorize
                        // next to each endpoint. Here: no anonymous access to the API at all.
                        .requestMatchers("/api/**").authenticated()
                        // Anything not listed above does not exist for outsiders
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authentication))
                        .authenticationEntryPoint(problems))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    /** A manager can do everything an analyst can. Declared once instead of repeating "ANALYST or MANAGER". */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(Role.MANAGER.authority() + " > " + Role.ANALYST.authority());
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${creditdesk.security.jwk-set-uri}") String jwkSetUri,
                          @Value("${creditdesk.security.issuer}") String issuer,
                          @Value("${creditdesk.security.audience}") String audience) {
        // Keys are fetched on the first request and cached, so the API starts even if Keycloak is still booting
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), // signature is checked by the decoder; this adds exp/nbf + iss
                audienceValidator(audience)));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "The token was not issued for " + audience, null));
    }

    @Bean
    ProblemSecurityHandlers problemSecurityHandlers(ObjectMapper json) {
        return new ProblemSecurityHandlers(json);
    }
}
