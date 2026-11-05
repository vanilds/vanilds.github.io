package io.github.vanilds.creditdesk.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keycloak puts realm roles in {@code "realm_access": {"roles": ["ANALYST", ...]}}, a place Spring does not
 * read by default (it reads {@code scope}). This maps only the roles CreditDesk knows to {@code ROLE_*}
 * authorities; Keycloak's own roles (offline_access, uma_authorization…) are ignored on purpose.
 */
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Set<String> KNOWN = Stream.of(Role.values()).map(Role::name).collect(Collectors.toSet());

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> access) || !(access.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(KNOWN::contains)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
