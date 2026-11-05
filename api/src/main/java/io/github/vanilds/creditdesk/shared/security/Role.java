package io.github.vanilds.creditdesk.shared.security;

/**
 * Realm roles defined in Keycloak (infra/keycloak/creditdesk-realm.json).
 * The hierarchy MANAGER &gt; ANALYST is declared once, in {@link SecurityConfig}.
 */
public enum Role {
    /** Registers customers, creates and submits their own proposals. */
    ANALYST,
    /** Everything an analyst does, plus deciding proposals created by someone else (four-eyes). */
    MANAGER,
    /** Read-only: proposals, customers and the audit trail. Can reveal a full CPF, giving a reason. */
    AUDITOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
