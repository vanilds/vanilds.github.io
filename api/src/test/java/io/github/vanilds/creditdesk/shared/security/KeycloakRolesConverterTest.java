package io.github.vanilds.creditdesk.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The part the MockMvc jwt() shortcut skips: reading roles from a token shaped exactly like Keycloak's. */
class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter converter = new KeycloakRolesConverter();

    private static Jwt token(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void mapsOnlyCreditDeskRealmRoles() {
        Jwt jwt = token(Map.of("realm_access",
                Map.of("roles", List.of("MANAGER", "offline_access", "uma_authorization", "default-roles-creditdesk"))));
        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MANAGER");
    }

    @Test
    void tokenWithoutRealmRolesHasNoAuthorities() {
        assertThat(converter.convert(token(Map.of("scope", "openid")))).isEmpty();
    }

    @Test
    void audienceMustContainTheApi() {
        var validator = SecurityConfig.audienceValidator("creditdesk-api");
        assertThat(validator.validate(token(Map.of("aud", List.of("account", "creditdesk-api")))).hasErrors()).isFalse();
        assertThat(validator.validate(token(Map.of("aud", List.of("account")))).hasErrors()).isTrue();
        assertThat(validator.validate(token(Map.of())).hasErrors()).isTrue();
    }
}
