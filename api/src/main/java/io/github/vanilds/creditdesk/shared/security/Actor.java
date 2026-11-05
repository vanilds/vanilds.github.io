package io.github.vanilds.creditdesk.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Who is doing the request, taken from the validated JWT.
 *
 * <p>{@code id} is the token subject ({@code sub}): a Keycloak user id that never changes, so it is what we store
 * as owner and in the audit log. {@code username} is a snapshot for humans reading the screen; usernames can be
 * renamed, ids cannot.
 *
 * <p>Controllers receive it as a method parameter (see {@link ActorArgumentResolver}) and pass it down explicitly,
 * so business rules like "only the owner can submit" are plain Java that a unit test can call.
 */
public record Actor(String id, String username, Set<Role> roles) {

    public Actor {
        Objects.requireNonNull(id, "id");
        username = username == null || username.isBlank() ? id : username;
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }

    static Actor from(Authentication authentication) {
        String id = authentication.getName();
        String username = authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaimAsString("preferred_username")
                : id;
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (Role role : Role.values()) {
                if (role.authority().equals(authority.getAuthority())) {
                    roles.add(role);
                }
            }
        }
        return new Actor(id, username, roles);
    }
}
